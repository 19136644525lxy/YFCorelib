package mcmod.yfcorelib.api;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import mcmod.yfcorelib.api.config.ConfigManager;
import mcmod.yfcorelib.api.config.ConfigMigrator;
import mcmod.yfcorelib.api.config.ConfigSpec;
import mcmod.yfcorelib.api.config.FileConfigSource;
import mcmod.yfcorelib.api.config.SimpleConfigManager;
import mcmod.yfcorelib.api.event.EventBus;
import mcmod.yfcorelib.api.event.LifecycleQueue;
import mcmod.yfcorelib.api.event.SimpleEventBus;
import mcmod.yfcorelib.api.network.NetworkChannel;
import mcmod.yfcorelib.api.network.NetworkProvider;
import mcmod.yfcorelib.api.platform.Platform;
import mcmod.yfcorelib.api.platform.Services;
import mcmod.yfcorelib.api.registry.RegistryPaths;
import mcmod.yfcorelib.api.scheduler.Scheduler;
import mcmod.yfcorelib.api.scheduler.SimpleTimerStore;
import mcmod.yfcorelib.api.scheduler.TickScheduler;
import mcmod.yfcorelib.api.scheduler.TimerStore;

/** 库统一门面：业务代码从这里拿平台、事件总线、调度器、计时器与配置 */
public final class YFCoreLib {
	public static final String MOD_ID = Ids.MOD_ID;

	private static final SimpleEventBus EVENTS = new SimpleEventBus();
	private static final Map<Side, TickScheduler> SCHEDULERS = buildSchedulers();
	private static final LifecycleQueue LIFECYCLE = new LifecycleQueue();
	private static final SimpleTimerStore SERVER_TIMERS =
			new SimpleTimerStore(() -> SCHEDULERS.get(Side.SERVER).currentTick());
	private static final Map<String, ConfigManager> CONFIGS = new ConcurrentHashMap<>();
	private static final Map<String, NetworkChannel> CHANNELS = new ConcurrentHashMap<>();

	private YFCoreLib() {
	}

	private static Map<Side, TickScheduler> buildSchedulers() {
		Map<Side, TickScheduler> map = new EnumMap<>(Side.class);
		for (Side side : Side.values()) {
			// COMMON 不创建实例：没有任何平台会为它驱动刻，存在实例只会诱导误用
			if (side != Side.COMMON) {
				map.put(side, new TickScheduler());
			}
		}
		return map;
	}

	public static Platform platform() {
		return Services.platform();
	}

	public static EventBus events() {
		return EVENTS;
	}

	/**
	 * 指定侧的调度器。只有 CLIENT / SERVER 分别由客户端与服务端刻驱动。
	 *
	 * @throws IllegalArgumentException 传入 {@link Side#COMMON}：该侧没有刻驱动来源，
	 *                                  受理任务会导致任务静默永不执行，故直接拒绝
	 */
	public static Scheduler scheduler(Side side) {
		Objects.requireNonNull(side, "side");
		if (side == Side.COMMON) {
			throw new IllegalArgumentException(
					"Side.COMMON 没有刻驱动来源，任务将永不执行；请改用 Side.CLIENT 或 Side.SERVER");
		}
		return SCHEDULERS.get(side);
	}

	/** 服务端刻级计时器；持久化由 common 的 TimerPersistence 挂接 */
	public static TimerStore timers() {
		return SERVER_TIMERS;
	}

	/** 延迟加载队列：业务注册钩子，平台在玩家就绪时触发 */
	public static LifecycleQueue lifecycle() {
		return LIFECYCLE;
	}

	/**
	 * 获取或创建配置管理器：按 spec 名缓存单例，文件位于平台 config 目录。
	 * <p>同名重复调用返回首次创建的实例；迁移器随首次创建生效。</p>
	 */
	public static ConfigManager config(ConfigSpec spec, ConfigMigrator... migrators) {
		Objects.requireNonNull(spec, "spec");
		Objects.requireNonNull(migrators, "migrators");
		ConfigManager existing = CONFIGS.get(spec.name());
		if (existing != null) {
			return existing;
		}
		// 不在 computeIfAbsent 里建：reload() 会读盘，CHM 的桶锁会被整段 IO 占住
		SimpleConfigManager created =
				new SimpleConfigManager(spec, new FileConfigSource(platform().configDir()), migrators);
		created.reload();
		ConfigManager prev = CONFIGS.putIfAbsent(spec.name(), created);
		return prev != null ? prev : created;
	}

	/** 已创建的配置管理器名集合（监控用，返回副本） */
	public static java.util.Set<String> configNames() {
		return java.util.Set.copyOf(CONFIGS.keySet());
	}

	/**
	 * 获取或创建网络通道：名称必须为 "命名空间:路径" 形式，同名重复调用返回同一实例。
	 * <p>需在平台初始化阶段调用（通道注册有加载器时机要求），首次调用发现 NetworkProvider 实现。</p>
	 *
	 * @throws IllegalArgumentException 名称不是合法的 {@code namespace:path}
	 */
	public static NetworkChannel network(String name) {
		String clean = requireChannelName(name);
		return CHANNELS.computeIfAbsent(clean, n ->
				Services.load(NetworkProvider.class).orElseThrow(() -> new IllegalStateException(
						"未找到 NetworkProvider 实现：请确认平台模块已注册 META-INF/services")).channel(n));
	}

	/** 通道名必须在调用时就校验：否则拼错的 id 要到首次发包才炸，且报错点在更远的地方 */
	private static String requireChannelName(String name) {
		Objects.requireNonNull(name, "name");
		int colon = name.indexOf(':');
		if (colon <= 0 || colon == name.length() - 1 || name.indexOf(':', colon + 1) >= 0) {
			throw new IllegalArgumentException("通道名必须为 命名空间:路径 形式，实际: '" + name + "'");
		}
		RegistryPaths.requireNamespace(name.substring(0, colon));
		RegistryPaths.requirePath(name.substring(colon + 1));
		return name;
	}
}
