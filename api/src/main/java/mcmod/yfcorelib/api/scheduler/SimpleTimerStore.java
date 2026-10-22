package mcmod.yfcorelib.api.scheduler;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** 内存计时器实现：存截止刻，读取时惰性清理过期项 */
public final class SimpleTimerStore implements TimerStore {
	private final LongSupplier clock;
	private final ConcurrentHashMap<String, Long> deadlines = new ConcurrentHashMap<>();

	public SimpleTimerStore(LongSupplier clock) {
		this.clock = clock;
	}

	@Override
	public long remaining(String key) {
		Long deadline = deadlines.get(key);
		if (deadline == null) {
			return 0;
		}
		long left = deadline - clock.getAsLong();
		if (left <= 0) {
			deadlines.remove(key, deadline);
			return 0;
		}
		return left;
	}

	@Override
	public void set(String key, long ticks) {
		deadlines.put(key, clock.getAsLong() + Math.max(0, ticks));
	}

	@Override
	public void clear(String key) {
		deadlines.remove(key);
	}

	@Override
	public Set<String> keys() {
		long now = clock.getAsLong();
		Set<String> out = new HashSet<>();
		deadlines.forEach((key, deadline) -> {
			if (deadline > now) {
				out.add(key);
			}
		});
		return out;
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
