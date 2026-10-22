package mcmod.yfcorelib.api;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import mcmod.yfcorelib.api.config.ConfigManager;
import mcmod.yfcorelib.api.config.ConfigMigrator;
import mcmod.yfcorelib.api.config.ConfigSpec;
import mcmod.yfcorelib.api.config.FileConfigSource;
import mcmod.yfcorelib.api.config.SimpleConfigManager;
import mcmod.yfcorelib.api.event.EventBus;
import mcmod.yfcorelib.api.event.LifecycleQueue;
import mcmod.yfcorelib.api.event.SimpleEventBus;
import mcmod.yfcorelib.api.platform.Platform;
import mcmod.yfcorelib.api.platform.Services;
import mcmod.yfcorelib.api.scheduler.Scheduler;
import mcmod.yfcorelib.api.scheduler.SimpleTimerStore;
import mcmod.yfcorelib.api.scheduler.TickScheduler;
import mcmod.yfcorelib.api.scheduler.TimerStore;

/** 库统一门面：业务代码从这里拿平台、事件总线、调度器、计时器与配置 */
public final class YFCoreLib {
	public static final String MOD_ID = "yfcorelib";

	private static final SimpleEventBus EVENTS = new SimpleEventBus();
	private static final Map<Side, TickScheduler> SCHEDULERS = buildSchedulers();
	private static final LifecycleQueue LIFECYCLE = new LifecycleQueue();
	private static final SimpleTimerStore SERVER_TIMERS =
			new SimpleTimerStore(() -> SCHEDULERS.get(Side.SERVER).currentTick());
	private static final Map<String, ConfigManager> CONFIGS = new ConcurrentHashMap<>();

	private YFCoreLib() {
	}

	private static Map<Side, TickScheduler> buildSchedulers() {
		Map<Side, TickScheduler> map = new EnumMap<>(Side.class);
		for (Side side : Side.values()) {
			map.put(side, new TickScheduler());
		}
		return map;
	}

	public static Platform platform() {
		return Services.platform();
	}

	public static EventBus events() {
		return EVENTS;
	}

	/** 指定侧的调度器；COMMON 预留，平台仅驱动 CLIENT/SERVER */
	public static Scheduler scheduler(Side side) {
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
		return CONFIGS.computeIfAbsent(spec.name(),
				name -> {
					SimpleConfigManager manager =
							new SimpleConfigManager(spec, new FileConfigSource(platform().configDir()), migrators);
					manager.reload();
					return manager;
				});
	}

	/** 已创建的配置管理器名集合（监控用） */
	public static java.util.Set<String> configNames() {
		return CONFIGS.keySet();
	}
}
