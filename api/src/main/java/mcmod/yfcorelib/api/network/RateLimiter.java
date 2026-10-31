package mcmod.yfcorelib.api.network;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 固定窗口限流：按 key 统计窗口内次数，超限返回 false。
 * <p>计数与窗口判定都在 {@code compute} 内完成，因此同一 key 的并发调用不会漏计。</p>
 * <p>窗口时间用 {@link System#nanoTime()}（单调），系统时钟回拨不会让窗口永不过期。</p>
 * <p>sweep 定期清理过期窗口，防止 key 无限累积导致内存泄漏。</p>
 */
public final class RateLimiter {
	private record Window(long startNanos, AtomicInteger count) {
	}

	private final int maxPerWindow;
	private final long windowNanos;
	private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

	public RateLimiter(int maxPerWindow, long windowMillis) {
		if (maxPerWindow <= 0 || windowMillis <= 0) {
			throw new IllegalArgumentException("限流参数必须为正数");
		}
		this.maxPerWindow = maxPerWindow;
		// 防止超大窗口在换算成纳秒时溢出
		this.windowNanos = windowMillis > Long.MAX_VALUE / 1_000_000L
				? Long.MAX_VALUE
				: windowMillis * 1_000_000L;
	}

	/** 尝试获取一次配额；判定与计数在同一把桶锁内完成，线程安全 */
	public boolean tryAcquire(String key) {
		Objects.requireNonNull(key, "key");
		long now = System.nanoTime();
		Window window = windows.compute(key, (k, old) -> {
			if (old == null || now - old.startNanos() >= windowNanos) {
				return new Window(now, new AtomicInteger(1));
			}
			old.count().incrementAndGet();
			return old;
		});
		return window.count().get() <= maxPerWindow;
	}

	/** 清理已过期的窗口条目 */
	public void sweep() {
		long now = System.nanoTime();
		windows.entrySet().removeIf(e -> now - e.getValue().startNanos() >= windowNanos);
	}

	/** 当前跟踪的 key 数量（监控用） */
	public int size() {
		return windows.size();
	}
}
