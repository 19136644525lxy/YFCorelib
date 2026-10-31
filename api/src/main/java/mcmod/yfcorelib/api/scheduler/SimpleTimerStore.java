package mcmod.yfcorelib.api.scheduler;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 内存计时器实现：存截止刻，读取时惰性清理过期项。
 * <p>{@link #keys()} 也会顺手清掉已过期条目，避免"只读监控"时过期项永久堆积。</p>
 */
public final class SimpleTimerStore implements TimerStore {
	private final LongSupplier clock;
	private final ConcurrentHashMap<String, Long> deadlines = new ConcurrentHashMap<>();

	public SimpleTimerStore(LongSupplier clock) {
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	public long remaining(String key) {
		Long deadline = deadlines.get(key);
		if (deadline == null) {
			return 0;
		}
		long now = clock.getAsLong();
		if (deadline <= now) {
			deadlines.remove(key, deadline);
			return 0;
		}
		long left = deadline - now;
		// 极端值下 deadline - now 可能溢出为负，此时按"很久以后"处理
		return left > 0 ? left : Long.MAX_VALUE;
	}

	@Override
	public void set(String key, long ticks) {
		Objects.requireNonNull(key, "key");
		if (ticks < 0) {
			// 之前是 Math.max(0, ticks) 静默钳成 0：调用方算错会变成"立刻到期"，
			// 表现为计时器莫名触发，比直接报错难查得多
			throw new IllegalArgumentException("计时长度不得为负数: " + ticks + "（key=" + key + "）");
		}
		long now = clock.getAsLong();
		long deadline = ticks > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + ticks;
		deadlines.put(key, deadline);
	}

	@Override
	public void clear(String key) {
		deadlines.remove(key);
	}

	@Override
	public Set<String> keys() {
		sweep();
		return Set.copyOf(deadlines.keySet());
	}

	@Override
	public void sweep() {
		long now = clock.getAsLong();
		deadlines.forEach((key, deadline) -> {
			if (deadline <= now) {
				deadlines.remove(key, deadline);
			}
		});
	}
}
