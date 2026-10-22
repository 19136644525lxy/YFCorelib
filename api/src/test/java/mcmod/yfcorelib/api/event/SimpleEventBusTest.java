package mcmod.yfcorelib.api.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/** SimpleEventBus：投递、退订与并发安全 */
class SimpleEventBusTest {
	private record Msg(String text) {
	}

	@Test
	void postShouldReachListener() {
		SimpleEventBus bus = new SimpleEventBus();
		List<Msg> received = new ArrayList<>();
		EventBus.Subscription sub = bus.register(Msg.class, received::add);
		bus.post(new Msg("hello"));
		assertEquals(1, received.size());
		assertEquals("hello", received.get(0).text());
		sub.close();
	}

	@Test
	void multipleListenersShouldRunInOrder() {
		SimpleEventBus bus = new SimpleEventBus();
		List<String> order = new ArrayList<>();
		bus.register(Msg.class, e -> order.add("a"));
		bus.register(Msg.class, e -> order.add("b"));
		bus.post(new Msg("x"));
		assertEquals(List.of("a", "b"), order);
	}

	@Test
	void closedSubscriptionShouldStopReceiving() {
		SimpleEventBus bus = new SimpleEventBus();
		AtomicInteger count = new AtomicInteger();
		EventBus.Subscription sub = bus.register(Msg.class, e -> count.incrementAndGet());
		bus.post(new Msg("1"));
		sub.close();
		sub.close(); // 重复 close 安全
		bus.post(new Msg("2"));
		assertEquals(1, count.get());
	}

	@Test
	void listenerExceptionShouldNotBreakOthers() {
		SimpleEventBus bus = new SimpleEventBus();
		List<String> order = new ArrayList<>();
		bus.register(Msg.class, e -> order.add("first"));
		bus.register(Msg.class, e -> {
			throw new RuntimeException("boom");
		});
		bus.register(Msg.class, e -> order.add("last"));
		bus.post(new Msg("x"));
		assertEquals(List.of("first", "last"), order);
	}

	@Test
	void postUnknownEventShouldNotThrow() {
		SimpleEventBus bus = new SimpleEventBus();
		bus.post(new Msg("nobody"));
	}

	@Test
	void concurrentRegisterAndPostShouldNotThrow() throws InterruptedException {
		SimpleEventBus bus = new SimpleEventBus();
		CopyOnWriteArrayList<Msg> received = new CopyOnWriteArrayList<>();
		bus.register(Msg.class, received::add);
		CountDownLatch latch = new CountDownLatch(2);
		// 线程一：反复注册/退订
		Thread registrant = new Thread(() -> {
			for (int i = 0; i < 500; i++) {
				EventBus.Subscription sub = bus.register(Msg.class, e -> {
				});
				bus.post(new Msg("bg" + i));
				sub.close();
			}
			latch.countDown();
		});
		// 线程二：持续投递
		Thread poster = new Thread(() -> {
			for (int i = 0; i < 500; i++) {
				bus.post(new Msg("p" + i));
			}
			latch.countDown();
		});
		registrant.start();
		poster.start();
		latch.await();
		assertTrue(received.size() >= 500); // 主线程监听器始终在列
	}

	@Test
	void nullArgumentsShouldThrow() {
		SimpleEventBus bus = new SimpleEventBus();
		assertThrows(NullPointerException.class, () -> bus.register(null, e -> {
		}));
		assertThrows(NullPointerException.class, () -> bus.register(Msg.class, null));
		assertThrows(NullPointerException.class, () -> bus.post(null));
	}
}
