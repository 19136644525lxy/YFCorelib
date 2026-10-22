package mcmod.yfcorelib.common.diagnostic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mcmod.yfcorelib.api.Side;
import mcmod.yfcorelib.api.YFCoreLib;
import mcmod.yfcorelib.api.config.ConfigBuilder;
import mcmod.yfcorelib.api.config.ConfigKey;
import mcmod.yfcorelib.api.config.ConfigSource;
import mcmod.yfcorelib.api.config.ConfigSpec;
import mcmod.yfcorelib.api.config.SimpleConfigManager;
import mcmod.yfcorelib.api.data.DataCodec;
import mcmod.yfcorelib.api.data.SimplePlayerSessionStore;
import mcmod.yfcorelib.api.event.PlayerJoinEvent;
import mcmod.yfcorelib.api.event.PlayerLeaveEvent;
import mcmod.yfcorelib.api.event.PlayerRef;
import mcmod.yfcorelib.api.event.SimpleEventBus;
import mcmod.yfcorelib.api.network.Packet;
import mcmod.yfcorelib.api.network.PacketType;
import mcmod.yfcorelib.api.network.SimpleChannelRegistry;
import mcmod.yfcorelib.api.scheduler.ScheduledTask;
import mcmod.yfcorelib.api.scheduler.TickScheduler;
import mcmod.yfcorelib.common.demo.DemoContent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * 运行时自检：以纯内存方式验证核心能力，任何异常都转为单条失败结果。
 * <p>调度器检查使用独立实例，不干扰平台驱动的全局调度器。</p>
 */
public final class SelfTest {
	private static final String CONFIG_NAME = "yfcorelib-selftest";

	public record Result(String name, boolean passed, String detail) {
	}

	private SelfTest() {
	}

	public static List<Result> runAll() {
		List<Result> results = new ArrayList<>();
		results.add(guard("事件总线", SelfTest::eventBus));
		results.add(guard("调度器", SelfTest::scheduler));
		results.add(guard("配置合并", SelfTest::config));
		results.add(guard("玩家会话", SelfTest::session));
		results.add(guard("网络编解码", SelfTest::network));
		if (YFCoreLib.platform().isDevelopment()) {
			results.add(guard("注册 DSL", SelfTest::registrations));
		}
		return results;
	}

	public static boolean allPassed(List<Result> results) {
		return results.stream().allMatch(Result::passed);
	}

	private static Result guard(String name, Supplier<String> check) {
		try {
			return new Result(name, true, check.get());
		} catch (RuntimeException | AssertionError e) {
			return new Result(name, false, e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}

	/** 精确类型分发 + 退订后不再命中 */
	private static String eventBus() {
		SimpleEventBus bus = new SimpleEventBus();
		AtomicInteger hits = new AtomicInteger();
		var subscription = bus.register(ProbeEvent.class, event -> hits.addAndGet(event.value()));
		bus.post(new ProbeEvent(2));
		bus.post(new ProbeEvent(3));
		subscription.close();
		bus.post(new ProbeEvent(1));
		require(hits.get() == 5, "期望命中 5，实际 " + hits.get());
		return "订阅、分发与退订均生效";
	}

	/** 独立调度器：delay(1) 与 repeat(1) 同刻到期，取消后停止 */
	private static String scheduler() {
		TickScheduler scheduler = new TickScheduler();
		AtomicInteger runs = new AtomicInteger();
		scheduler.delay(1, runs::incrementAndGet);
		ScheduledTask repeat = scheduler.repeat(1, runs::incrementAndGet);
		scheduler.tick();
		require(runs.get() == 2, "第 1 刻期望执行 2 次，实际 " + runs.get());
		scheduler.tick();
		require(runs.get() == 3, "第 2 刻期望累计 3 次，实际 " + runs.get());
		require(repeat.cancel(), "未执行完的重复任务应可取消");
		scheduler.tick();
		require(runs.get() == 3, "取消后不应再执行，实际 " + runs.get());
		return "延迟/重复/取消行为符合预期";
	}

	/** 补默认键不覆盖用户值、未知键保留、版本保持、写回可读 */
	private static String config() {
		ConfigSpec spec = ConfigBuilder.create(CONFIG_NAME)
				.define("enabled", Boolean.class, true)
				.version(1)
				.build();
		@SuppressWarnings("unchecked")
		ConfigKey<Boolean> enabled = (ConfigKey<Boolean>) spec.keys().get(0);

		MemorySource source = new MemorySource();
		source.write(CONFIG_NAME, "{\"__version\":1,\"enabled\":false,\"unknown\":7}");
		SimpleConfigManager manager = new SimpleConfigManager(spec, source);
		require(manager.reload(), "reload 失败");
		require(!manager.get(enabled), "用户值 false 被默认值覆盖");
		require(manager.loadedVersion() == 1, "版本应为 1，实际 " + manager.loadedVersion());

		manager.set(enabled, true);
		manager.save();
		manager.reload();
		require(manager.get(enabled), "写回后未读到 true");
		require(JsonParser.parseString(source.read(CONFIG_NAME).orElse("{}"))
				.getAsJsonObject().has("unknown"), "未知键被丢弃");
		return "默认合并、用户值保留、未知键保留与写回均通过";
	}

	/** 加入创建、离开回收 */
	private static String session() {
		SimpleEventBus bus = new SimpleEventBus();
		try (SimplePlayerSessionStore<String> store = new SimplePlayerSessionStore<>(bus, () -> "default")) {
			UUID id = UUID.randomUUID();
			PlayerRef ref = new ProbePlayer(id, "tester");
			bus.post(new PlayerJoinEvent(ref));
			require(store.has(id) && "default".equals(store.get(id)), "加入后未创建会话");
			require(store.online().contains(id), "在线集合缺少玩家");
			bus.post(new PlayerLeaveEvent(ref));
			require(!store.has(id) && store.size() == 0, "离开后未回收会话");
			return "加入创建、在线集合与离开回收均通过";
		}
	}

	/** 通道内包类型注册 + 编解码回环（传输层需真实客户端联机验证） */
	private static String network() {
		PacketType<ProbePacket> type = new PacketType<>("yfcorelib:selftest", Side.SERVER,
				ProbePacket.class, new ProbeCodec(), (packet, context) -> {
				});
		SimpleChannelRegistry registry = new SimpleChannelRegistry();
		registry.register(type);
		require(registry.require("yfcorelib:selftest") == type, "包类型查找失败");
		ProbePacket decoded = type.codec().decode(type.codec().encode(new ProbePacket("回环")));
		require("回环".equals(decoded.text()), "编解码结果不一致");
		return "包类型注册与编解码回环通过（实际传输需联机验证）";
	}

	/** 开发环境：演示内容必须真实进入注册表 */
	private static String registrations() {
		ResourceLocation item = ResourceLocation.tryParse(YFCoreLib.MOD_ID + ":" + DemoContent.ITEM_PATH);
		ResourceLocation block = ResourceLocation.tryParse(YFCoreLib.MOD_ID + ":" + DemoContent.BLOCK_PATH);
		require(item != null && BuiltInRegistries.ITEM.containsKey(item), "物品未注册: " + item);
		require(block != null && BuiltInRegistries.BLOCK.containsKey(block), "方块未注册: " + block);
		require(BuiltInRegistries.ITEM.containsKey(block), "方块缺少自动 BlockItem");
		return "演示物品与方块均已注册，方块物品自动生成";
	}

	private record ProbeEvent(int value) {
	}

	private record ProbePlayer(UUID id, String name) implements PlayerRef {
		@Override
		public boolean isOnline() {
			return true;
		}
	}

	private record ProbePacket(String text) implements Packet {
		@Override
		public String typeId() {
			return "yfcorelib:selftest";
		}
	}

	private static final class ProbeCodec implements DataCodec<ProbePacket> {
		@Override
		public JsonElement encode(ProbePacket value) {
			JsonObject json = new JsonObject();
			json.addProperty("text", value.text());
			return json;
		}

		@Override
		public ProbePacket decode(JsonElement json) {
			return new ProbePacket(json.getAsJsonObject().get("text").getAsString());
		}
	}

	private static final class MemorySource implements ConfigSource {
		private final Map<String, String> store = new HashMap<>();

		@Override
		public Optional<String> read(String name) {
			return Optional.ofNullable(store.get(name));
		}

		@Override
		public void write(String name, String content) {
			store.put(name, content);
		}
	}
}
