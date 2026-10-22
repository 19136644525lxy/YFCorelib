package mcmod.yfcorelib.api;

import java.util.EnumMap;
import java.util.Map;

import mcmod.yfcorelib.api.event.EventBus;
import mcmod.yfcorelib.api.event.LifecycleQueue;
import mcmod.yfcorelib.api.event.SimpleEventBus;
import mcmod.yfcorelib.api.platform.Platform;
import mcmod.yfcorelib.api.platform.Services;
import mcmod.yfcorelib.api.scheduler.Scheduler;
import mcmod.yfcorelib.api.scheduler.SimpleTimerStore;
import mcmod.yfcorelib.api.scheduler.TickScheduler;
import mcmod.yfcorelib.api.scheduler.TimerStore;

/** 库统一门面：业务代码从这里拿平台、事件总线、调度器与计时器 */
public final class YFCoreLib {
	public static final String MOD_ID = "yfcorelib";

	private static final SimpleEventBus EVENTS = new SimpleEventBus();
	private static final Map<Side, TickScheduler> SCHEDULERS = buildSchedulers();
	private static final LifecycleQueue LIFECYCLE = new LifecycleQueue();
	private static final SimpleTimerStore SERVER_TIMERS =
			new SimpleTimerStore(() -> SCHEDULERS.get(Side.SERVER).currentTick());

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

	/** 服务端刻级计时器（M3 接入持久化） */
	public static TimerStore timers() {
		return SERVER_TIMERS;
	}

	/** 延迟加载队列：业务注册钩子，平台在玩家就绪时触发 */
	public static LifecycleQueue lifecycle() {
		return LIFECYCLE;
	}
}
