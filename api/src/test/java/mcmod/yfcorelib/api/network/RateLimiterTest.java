package mcmod.yfcorelib.api.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** RateLimiter：固定窗口限流与过期清理 */
class RateLimiterTest {
	@Test
	void shouldLimitWithinWindow() {
		RateLimiter limiter = new RateLimiter(3, 60_000);
		assertTrue(limiter.tryAcquire("player1"));
		assertTrue(limiter.tryAcquire("player1"));
		assertTrue(limiter.tryAcquire("player1"));
		assertFalse(limiter.tryAcquire("player1"));
		// 不同 key 相互独立
		assertTrue(limiter.tryAcquire("player2"));
	}

	@Test
	void shouldRejectInvalidConfig() {
		assertThrows(IllegalArgumentException.class, () -> new RateLimiter(0, 1000));
		assertThrows(IllegalArgumentException.class, () -> new RateLimiter(10, 0));
		assertThrows(IllegalArgumentException.class, () -> new RateLimiter(-1, -1));
	}

	@Test
	void windowShouldResetAfterExpiry() throws InterruptedException {
		RateLimiter limiter = new RateLimiter(1, 50);
		assertTrue(limiter.tryAcquire("k"));
		assertFalse(limiter.tryAcquire("k"));
		// 等待窗口过期后恢复配额
		Thread.sleep(80);
		assertTrue(limiter.tryAcquire("k"));
	}

	@Test
	void sweepShouldRemoveExpiredWindows() throws InterruptedException {
		RateLimiter limiter = new RateLimiter(1, 40);
		limiter.tryAcquire("k1");
		limiter.tryAcquire("k2");
		assertEquals(2, limiter.size());
		Thread.sleep(60);
		limiter.sweep();
		assertEquals(0, limiter.size());
	}
}
