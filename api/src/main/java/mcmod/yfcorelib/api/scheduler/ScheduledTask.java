package mcmod.yfcorelib.api.scheduler;

/** 可取消的任务句柄：线程安全，可重复调用 */
public interface ScheduledTask {
	/** 取消任务；首次取消返回 true，已取消或已完成的返回 false */
	boolean cancel();

	boolean isCancelled();
}
