package mcmod.yfcorelib.api.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/** SimpleTimerStore：倒计时语义与过期清理 */
class SimpleTimerStoreTest {
	@Test
	void remainingShouldCountDown() {
		AtomicLong clock = new AtomicLong();
		SimpleTimerStore timers = new SimpleTimerStore(clock::get);
		timers.set("k", 10);
		assertEquals(10, timers.remaining("k"));
		clock.set(4);
		assertEquals(6, timers.remaining("k"));
		clock.set(10);
		assertEquals(0, timers.remaining("k")); // 到期
		assertEquals(0, timers.remaining("k")); // 到期后保持 0
		assertFalse(timers.keys().contains("k"));
	}

	@Test
	void absentKeyShouldReturnZero() {
		SimpleTimerStore timers = new SimpleTimerStore(() -> 0);
		assertEquals(0, timers.remaining("missing"));
		assertEquals(0, timers.keys().size());
	}

	@Test
	void resetShouldOverwrite() {
		AtomicLong clock = new AtomicLong();
		SimpleTimerStore timers = new SimpleTimerStore(clock::get);
		timers.set("k", 5);
		timers.set("k", 100);
		assertEquals(100, timers.remaining("k"));
		timers.clear("k");
		assertEquals(0, timers.remaining("k"));
	}

	@Test
	void keysShouldExcludeExpired() {
		AtomicLong clock = new AtomicLong();
		SimpleTimerStore timers = new SimpleTimerStore(clock::get);
		timers.set("a", 5);
		timers.set("b", 100);
		assertTrue(timers.keys().contains("a"));
		assertTrue(timers.keys().contains("b"));
		clock.set(6);
		assertTrue(timers.keys().contains("b"));
		assertFalse(timers.keys().contains("a"));
		// sweep 清理过期条目
		timers.sweep();
		clock.set(200);
		timers.sweep();
		assertFalse(timers.keys().contains("b"));
	}

	@Test
	void zeroTicksShouldExpireImmediately() {
		SimpleTimerStore timers = new SimpleTimerStore(() -> 42);
		timers.set("k", 0);
		assertEquals(0, timers.remaining("k"));
	}

	@Test
	void negativeTicksShouldBeRejected() {
		// 旧实现用 Math.max(0, ticks) 静默钳成 0，表现为"设了却立刻到期"，很难查
		SimpleTimerStore timers = new SimpleTimerStore(() -> 42);
		assertThrows(IllegalArgumentException.class, () -> timers.set("k", -1));
		assertFalse(timers.keys().contains("k"));
	}

	@Test
	void hugeTicksShouldSaturateInsteadOfOverflowing() {
		SimpleTimerStore timers = new SimpleTimerStore(() -> 100);
		timers.set("k", Long.MAX_VALUE);
		// deadline 溢出成负数会让 remaining 立刻变 0
		assertTrue(timers.remaining("k") > 0);
		assertTrue(timers.keys().contains("k"));
	}
}
