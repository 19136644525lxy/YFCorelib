package mcmod.yfcorelib.api.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/** TickScheduler：延迟/重复/取消/顺序/异常隔离 */
class TickSchedulerTest {
	@Test
	void delayShouldRunAfterTicks() {
		TickScheduler s = new TickScheduler();
		AtomicInteger ran = new AtomicInteger();
		s.delay(2, ran::incrementAndGet);
		s.tick();
		assertEquals(0, ran.get());
		s.tick();
		assertEquals(1, ran.get());
		s.tick();
		assertEquals(1, ran.get());
	}

	@Test
	void delayZeroShouldRunNextTick() {
		TickScheduler s = new TickScheduler();
		AtomicInteger ran = new AtomicInteger();
		s.delay(0, ran::incrementAndGet);
		assertEquals(0, ran.get());
		s.tick();
		assertEquals(1, ran.get());
	}

	@Test
	void repeatShouldRunPeriodically() {
		TickScheduler s = new TickScheduler();
		AtomicInteger ran = new AtomicInteger();
		s.repeat(3, ran::incrementAndGet);
		for (int i = 0; i < 3; i++) {
			s.tick();
		}
		assertEquals(1, ran.get());
		for (int i = 0; i < 3; i++) {
			s.tick();
		}
		assertEquals(2, ran.get());
	}

	@Test
	void cancelShouldStopTask() {
		TickScheduler s = new TickScheduler();
		AtomicInteger ran = new AtomicInteger();
		ScheduledTask task = s.delay(1, ran::incrementAndGet);
		assertTrue(task.cancel());
		assertFalse(task.cancel()); // 重复取消返回 false
		assertTrue(task.isCancelled());
		s.tick();
		assertEquals(0, ran.get());
		// 取消已完成的任务返回 false
		ScheduledTask ran1 = s.delay(1, () -> {
		});
		s.tick();
		assertFalse(ran1.cancel());
	}

	@Test
	void cancelSelfInsideRepeatShouldStop() {
		TickScheduler s = new TickScheduler();
		AtomicInteger ran = new AtomicInteger();
		ScheduledTask[] holder = new ScheduledTask[1];
		holder[0] = s.repeat(1, () -> {
			ran.incrementAndGet();
			holder[0].cancel();
		});
		s.tick();
		s.tick();
		s.tick();
		assertEquals(1, ran.get());
	}

	@Test
	void cancelAllShouldUseOwnerIdentity() {
		TickScheduler s = new TickScheduler();
		Object ownerA = new Object();
		Object ownerB = new Object();
		AtomicInteger ranA = new AtomicInteger();
		AtomicInteger ranB = new AtomicInteger();
		s.delay(ownerA, 1, ranA::incrementAndGet);
		s.delay(ownerA, 1, ranA::incrementAndGet);
		s.delay(ownerB, 1, ranB::incrementAndGet);
		s.cancelAll(ownerA);
		s.tick();
		assertEquals(0, ranA.get());
		assertEquals(1, ranB.get());
	}

	@Test
	void sameTickTasksShouldRunInSubmitOrder() {
		TickScheduler s = new TickScheduler();
		List<String> order = new ArrayList<>();
		s.delay(2, () -> order.add("first"));
		s.delay(2, () -> order.add("second"));
		s.delay(1, () -> order.add("earlier"));
		s.tick();
		s.tick();
		assertEquals(List.of("earlier", "first", "second"), order);
	}

	@Test
	void taskExceptionShouldNotBreakOthers() {
		TickScheduler s = new TickScheduler();
		List<String> order = new ArrayList<>();
		s.delay(1, () -> order.add("before"));
		s.delay(1, () -> {
			throw new RuntimeException("boom");
		});
		s.delay(1, () -> order.add("after"));
		s.tick();
		assertEquals(List.of("before", "after"), order);
		assertEquals(0, s.pendingCount());
	}

	@Test
	void repeatTaskExceptionShouldNotStopFutureRuns() {
		TickScheduler s = new TickScheduler();
		AtomicInteger ran = new AtomicInteger();
		s.repeat(1, () -> {
			ran.incrementAndGet();
			throw new RuntimeException("boom");
		});
		s.tick();
		s.tick();
		assertEquals(2, ran.get());
	}

	@Test
	void tickableShouldDetachWhenDone() {
		TickScheduler s = new TickScheduler();
		AtomicInteger ticks = new AtomicInteger();
		AtomicInteger detachProbe = new AtomicInteger();
		s.attach(new Tickable() {
			@Override
			public void tick() {
				ticks.incrementAndGet();
			}

			@Override
			public boolean isDone() {
				return ticks.get() >= 2;
			}
		});
		s.attach(new Tickable() {
			@Override
			public void tick() {
				detachProbe.incrementAndGet();
			}

			@Override
			public boolean isDone() {
				return false;
			}
		});
		s.tick();
		s.tick();
		s.tick();
		assertEquals(2, ticks.get());
		assertEquals(3, detachProbe.get());
	}

	@Test
	void invalidArgumentsShouldThrow() {
		TickScheduler s = new TickScheduler();
		assertThrows(IllegalArgumentException.class, () -> s.delay(-1, () -> {
		}));
		assertThrows(IllegalArgumentException.class, () -> s.repeat(0, () -> {
		}));
		assertThrows(NullPointerException.class, () -> s.delay(1, null));
	}
}
