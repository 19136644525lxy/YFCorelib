package mcmod.yfcorelib.api.scheduler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 基于内部刻计数器的调度实现。
 * <p>任务挂在 CopyOnWriteArrayList 上，提交与取消可来自任意线程；
 * 同刻到期任务按提交顺序执行，单个任务异常不影响本轮其他任务。</p>
 */
public final class TickScheduler implements Scheduler {
	private static final Logger LOG = Logger.getLogger(TickScheduler.class.getName());
	private static final Comparator<Task> BY_SEQ = Comparator.comparingLong(t -> t.seq);

	private final CopyOnWriteArrayList<Task> tasks = new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<Tickable> tickables = new CopyOnWriteArrayList<>();
	private final AtomicLong seqGen = new AtomicLong();
	private volatile long currentTick;

	/** 内部刻计数（平台驱动以来累计），供 TimerStore 等作外部时钟 */
	public long currentTick() {
		return currentTick;
	}

	/** 当前未完成任务数，用于监控与测试 */
	public int pendingCount() {
		int n = 0;
		for (Task t : tasks) {
			if (!t.cancelled.get() && !t.done) {
				n++;
			}
		}
		return n;
	}

	@Override
	public ScheduledTask delay(int delayTicks, Runnable task) {
		return delay(null, delayTicks, task);
	}

	@Override
	public ScheduledTask delay(Object owner, int delayTicks, Runnable task) {
		return schedule(owner, task, delayTicks, 0);
	}

	@Override
	public ScheduledTask repeat(int periodTicks, Runnable task) {
		return repeat(null, 0, periodTicks, task);
	}

	@Override
	public ScheduledTask repeat(int delayTicks, int periodTicks, Runnable task) {
		return repeat(null, delayTicks, periodTicks, task);
	}

	@Override
	public ScheduledTask repeat(Object owner, int delayTicks, int periodTicks, Runnable task) {
		if (periodTicks <= 0) {
			throw new IllegalArgumentException("periodTicks 必须为正数，实际 " + periodTicks);
		}
		return schedule(owner, task, delayTicks, periodTicks);
	}

	@Override
	public void attach(Tickable tickable) {
		tickables.add(Objects.requireNonNull(tickable, "tickable"));
	}

	@Override
	public void cancelAll(Object owner) {
		for (Task t : tasks) {
			if (t.owner == owner) {
				t.cancel();
			}
		}
	}

	@Override
	public void tick() {
		currentTick++;
		tickTickables();
		runDueTasks();
	}

	private void tickTickables() {
		for (Tickable t : tickables) {
			if (t.isDone()) {
				tickables.remove(t);
				continue;
			}
			try {
				t.tick();
			} catch (Throwable ex) {
				LOG.log(Level.SEVERE, "Tickable 执行异常", ex);
			}
		}
	}

	private void runDueTasks() {
		List<Task> due = null;
		boolean dirty = false;
		for (Task t : tasks) {
			if (t.cancelled.get() || t.done) {
				dirty = true;
			} else if (t.nextRunTick <= currentTick) {
				if (due == null) {
					due = new ArrayList<>();
				}
				due.add(t);
			}
		}
		if (dirty) {
			tasks.removeIf(t -> t.cancelled.get() || t.done);
		}
		if (due == null) {
			return;
		}
		due.sort(BY_SEQ);
		for (Task t : due) {
			if (t.cancelled.get()) {
				continue;
			}
			try {
				t.action.run();
			} catch (Throwable ex) {
				LOG.log(Level.SEVERE, "调度任务执行异常 seq=" + t.seq, ex);
			}
			if (!t.cancelled.get() && t.periodTicks > 0) {
				// 重复任务留在列表中，仅推进下次到期刻
				t.nextRunTick = currentTick + t.periodTicks;
			} else {
				t.done = true;
			}
		}
	}

	private ScheduledTask schedule(Object owner, Runnable action, int delayTicks, int periodTicks) {
		Objects.requireNonNull(action, "task");
		if (delayTicks < 0) {
			throw new IllegalArgumentException("delayTicks 不能为负，实际 " + delayTicks);
		}
		Task task = new Task(owner, action, currentTick + delayTicks, periodTicks, seqGen.getAndIncrement());
		tasks.add(task);
		return task;
	}

	private static final class Task implements ScheduledTask {
		private final Object owner;
		private final Runnable action;
		private final int periodTicks;
		private final long seq;
		private volatile long nextRunTick;
		private final AtomicBoolean cancelled = new AtomicBoolean();
		private volatile boolean done;

		Task(Object owner, Runnable action, long nextRunTick, int periodTicks, long seq) {
			this.owner = owner;
			this.action = action;
			this.nextRunTick = nextRunTick;
			this.periodTicks = periodTicks;
			this.seq = seq;
		}

		@Override
		public boolean cancel() {
			if (done) {
				return false;
			}
			return cancelled.compareAndSet(false, true);
		}

		@Override
		public boolean isCancelled() {
			return cancelled.get();
		}
	}
}
