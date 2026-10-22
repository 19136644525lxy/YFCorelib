package mcmod.yfcorelib.api.scheduler;

/**
 * 刻级调度器：由平台模块在对应 tick 事件中驱动 {@link #tick()}，业务代码只提交任务。
 * <p>COMMON/CLIENT/SERVER 实例相互独立，服务端与客户端调度互不干扰。</p>
 */
public interface Scheduler {
	/** 延迟执行一次；delayTicks=0 表示下一次 tick 执行 */
	ScheduledTask delay(int delayTicks, Runnable task);

	/** 携带归属者的延迟任务，供 {@link #cancelAll(Object)} 按身份批量取消 */
	ScheduledTask delay(Object owner, int delayTicks, Runnable task);

	/** 每隔 periodTicks 重复执行，首次执行在 periodTicks 之后 */
	ScheduledTask repeat(int periodTicks, Runnable task);

	/** 首次执行在 delayTicks 之后，之后每 periodTicks 重复 */
	ScheduledTask repeat(int delayTicks, int periodTicks, Runnable task);

	ScheduledTask repeat(Object owner, int delayTicks, int periodTicks, Runnable task);

	/** 挂接持续型对象，每 tick 调用其 tick()，直至 isDone() 为真自动移除 */
	void attach(Tickable tickable);

	/** 按 owner 身份（==）取消该归属者名下全部未完成任务 */
	void cancelAll(Object owner);

	/** 平台模块驱动，业务代码禁止调用 */
	void tick();
}
