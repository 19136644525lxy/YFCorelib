package mcmod.yfcorelib.api.scheduler;

/** 持续型每刻对象：由 Scheduler 驱动，完成后自动脱离 */
public interface Tickable {
	void tick();

	/** 返回 true 后不再被调度 */
	boolean isDone();
}
