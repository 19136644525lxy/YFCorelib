package mcmod.yfcorelib.common.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mcmod.yfcorelib.api.Side;
import mcmod.yfcorelib.api.YFCoreLib;
import mcmod.yfcorelib.api.event.ServerStartedEvent;
import mcmod.yfcorelib.api.event.ServerStoppingEvent;
import mcmod.yfcorelib.api.event.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * 计时器持久化：服务端启动时恢复、停止时保存、运行中定期落盘。
 * <p>复用 api 的 {@code AtomicFiles} 原子写，文件位于 {@code gameDir/data/yfcorelib/timers.json}。
 * 通过 api 事件总线挂接，平台模块只需调用 {@link #register()} 一次。</p>
 */
public final class TimerPersistence {
	private static final Logger LOG = LoggerFactory.getLogger(TimerPersistence.class);
	private static final int AUTOSAVE_INTERVAL_TICKS = 6000;
	private static final String FILE_NAME = "timers.json";

	private static volatile boolean registered;
	private static long tickCounter;

	private TimerPersistence() {
	}

	/** 幂等注册：双端入口各调一次安全 */
	public static synchronized void register() {
		if (registered) {
			return;
		}
		registered = true;
		YFCoreLib.events().register(ServerStartedEvent.class, event -> load());
		YFCoreLib.events().register(ServerStoppingEvent.class, event -> save());
		YFCoreLib.events().register(ServerTickEvent.class, event -> {
			if (event.side() == Side.SERVER && ++tickCounter % AUTOSAVE_INTERVAL_TICKS == 0) {
				save();
			}
		});
	}

	/** 把持久化的剩余刻灌回计时器 */
	private static void load() {
		Path file = file();
		if (!Files.exists(file)) {
			return;
		}
		try {
			String content = Files.readString(file, StandardCharsets.UTF_8);
			if (content.isBlank()) {
				return;
			}
			JsonObject parsed = JsonParser.parseString(content).getAsJsonObject();
			parsed.entrySet().forEach(entry -> {
				try {
					YFCoreLib.timers().set(entry.getKey(), entry.getValue().getAsLong());
				} catch (RuntimeException e) {
					// 损坏条目跳过
					LOG.warn("计时器条目损坏，已跳过: {}", entry.getKey());
				}
			});
			LOG.info("已恢复 {} 个计时器", parsed.size());
		} catch (Exception e) {
			LOG.warn("计时器文件损坏，跳过恢复: {}", file, e);
		}
	}

	/** 全量保存剩余刻（原子写） */
	private static void save() {
		Set<String> keys = YFCoreLib.timers().keys();
		if (keys.isEmpty() && !Files.exists(file())) {
			return;
		}
		JsonObject out = new JsonObject();
		for (String key : keys) {
			out.addProperty(key, YFCoreLib.timers().remaining(key));
		}
		mcmod.yfcorelib.api.data.AtomicFiles.writeAtomic(file(), out.toString());
	}

	private static Path file() {
		return YFCoreLib.platform().gameDir()
				.resolve("data").resolve(YFCoreLib.MOD_ID).resolve(FILE_NAME);
	}
}
