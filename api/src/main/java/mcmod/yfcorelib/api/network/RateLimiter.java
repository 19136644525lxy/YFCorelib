package mcmod.yfcorelib.api.network;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 固定窗口限流：按 key 统计窗口内次数，超限返回 false。
 * <p>sweep 定期清理过期窗口，防止 key 无限累积导致内存泄漏。</p>
 */
public final class RateLimiter {
	private record Window(long start, AtomicInteger count) {
	}

	private final int maxPerWindow;
	private final long windowMillis;
	private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

	public RateLimiter(int maxPerWindow, long windowMillis) {
		if (maxPerWindow <= 0 || windowMillis <= 0) {
			throw new IllegalArgumentException("限流参数必须为正数");
		}
		this.maxPerWindow = maxPerWindow;
		this.windowMillis = windowMillis;
	}

	/** 尝试获取一次配额；原子判定，线程安全 */
	public boolean tryAcquire(String key) {
		long now = System.currentTimeMillis();
		Window window = windows.compute(key, (k, old) ->
				old == null || now - old.start() >= windowMillis ? new Window(now, new AtomicInteger()) : old);
		return window.count().incrementAndGet() <= maxPerWindow;
	}

	/** 清理已过期的窗口条目 */
	public void sweep() {
		long now = System.currentTimeMillis();
		windows.entrySet().removeIf(e -> now - e.getValue().start() >= windowMillis);
	}

	/** 当前跟踪的 key 数量（监控用） */
	public int size() {
		return windows.size();
	}
}
