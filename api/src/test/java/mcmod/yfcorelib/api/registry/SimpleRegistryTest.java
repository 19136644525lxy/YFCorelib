package mcmod.yfcorelib.api.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/** SimpleRegistry：冲突检测、冻结、标签索引与并发注册 */
class SimpleRegistryTest {
	@Test
	void duplicateIdShouldThrow() {
		SimpleRegistry<String> reg = new SimpleRegistry<>("test");
		reg.register("a", "A");
		assertThrows(IllegalStateException.class, () -> reg.register("a", "B"));
		assertEquals(1, reg.values().size());
	}

	@Test
	void frozenRegistryShouldRejectWrites() {
		SimpleRegistry<String> reg = new SimpleRegistry<>("test");
		reg.register("a", "A");
		reg.freeze();
		assertTrue(reg.isFrozen());
		assertThrows(IllegalStateException.class, () -> reg.register("b", "B"));
		assertThrows(IllegalStateException.class, () -> reg.tag("a", "hot"));
		// 重复冻结幂等
		reg.freeze();
	}

	@Test
	void iterationShouldFollowRegistrationOrder() {
		SimpleRegistry<String> reg = new SimpleRegistry<>("test");
		reg.register("c", "C");
		reg.register("a", "A");
		reg.register("b", "B");
		assertEquals(List.of("c", "a", "b"), List.copyOf(reg.ids()));
		assertEquals(List.of("C", "A", "B"), reg.values());
	}

	@Test
	void tagIndexShouldWork() {
		SimpleRegistry<String> reg = new SimpleRegistry<>("test");
		reg.register("a", "A");
		reg.register("b", "B");
		reg.register("c", "C");
		reg.tag("a", "hot");
		reg.tag("c", "hot");
		reg.tag("a", "hot"); // 重复标签幂等
		assertEquals(List.of("A", "C"), reg.byTag("hot"));
		assertEquals(List.of(), reg.byTag("cold"));
		assertThrows(IllegalArgumentException.class, () -> reg.tag("unknown", "hot"));
	}

	@Test
	void listenerShouldBeNotified() {
		SimpleRegistry<String> reg = new SimpleRegistry<>("test");
		AtomicInteger registered = new AtomicInteger();
		AtomicInteger frozen = new AtomicInteger();
		reg.addListener(new RegistryListener<String>() {
			@Override
			public void onRegister(String id, String value) {
				registered.incrementAndGet();
			}

			@Override
			public void onFreeze() {
				frozen.incrementAndGet();
			}
		});
		// 监听器抛异常不阻断注册
		reg.addListener(new RegistryListener<String>() {
			@Override
			public void onRegister(String id, String value) {
				throw new RuntimeException("boom");
			}
		});
		reg.register("a", "A");
		reg.freeze();
		assertEquals(1, registered.get());
		assertEquals(1, frozen.get());
		assertEquals("A", reg.getOrThrow("a"));
	}

	@Test
	void getOrThrowShouldThrowWhenAbsent() {
		SimpleRegistry<String> reg = new SimpleRegistry<>("test");
		assertThrows(NoSuchElementException.class, () -> reg.getOrThrow("missing"));
		assertTrue(reg.get("missing").isEmpty());
	}

	@Test
	void concurrentRegistrationShouldBeSafe() throws InterruptedException {
		SimpleRegistry<Integer> reg = new SimpleRegistry<>("test");
		int threads = 8;
		int perThread = 50;
		CountDownLatch latch = new CountDownLatch(threads);
		for (int t = 0; t < threads; t++) {
			final int base = t * perThread;
			Thread thread = new Thread(() -> {
				for (int i = 0; i < perThread; i++) {
					reg.register("id" + (base + i), base + i);
				}
				latch.countDown();
			});
			thread.start();
		}
		latch.await();
		assertEquals(threads * perThread, reg.values().size());
		Set<String> ids = reg.ids();
		assertEquals(threads * perThread, ids.size());
		for (int i = 0; i < threads * perThread; i++) {
			assertTrue(ids.contains("id" + i));
		}
		assertFalse(reg.isFrozen());
	}
}
