package mcmod.yfcorelib.api.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import mcmod.yfcorelib.api.event.PlayerJoinEvent;
import mcmod.yfcorelib.api.event.PlayerLeaveEvent;
import mcmod.yfcorelib.api.event.PlayerRef;
import mcmod.yfcorelib.api.event.ServerStoppingEvent;
import mcmod.yfcorelib.api.event.SimpleEventBus;
import org.junit.jupiter.api.Test;

/** 会话数据：加入创建、离开回收、停服清理与退订 */
class SimplePlayerSessionStoreTest {
	private record FakePlayer(UUID id, String name, boolean isOnline) implements PlayerRef {
	}

	private static FakePlayer player(UUID id) {
		return new FakePlayer(id, "tester", true);
	}

	@Test
	void joinShouldCreateDefaultValue() {
		SimpleEventBus bus = new SimpleEventBus();
		SimplePlayerSessionStore<AtomicInteger> store =
				new SimplePlayerSessionStore<>(bus, () -> new AtomicInteger(7));
		UUID id = UUID.randomUUID();
		bus.post(new PlayerJoinEvent(player(id)));
		assertNotNull(store.get(id));
		assertEquals(7, store.get(id).get());
		assertTrue(store.online().contains(id));
		assertEquals(1, store.size());
	}

	@Test
	void leaveShouldEvictAndInvokeHook() {
		SimpleEventBus bus = new SimpleEventBus();
		AtomicInteger evictedValue = new AtomicInteger();
		SimplePlayerSessionStore<AtomicInteger> store =
				new SimplePlayerSessionStore<>(bus, () -> new AtomicInteger(1),
						(playerId, value) -> evictedValue.set(value.get()));
		UUID id = UUID.randomUUID();
		bus.post(new PlayerJoinEvent(player(id)));
		store.get(id).set(42);
		bus.post(new PlayerLeaveEvent(player(id)));
		assertFalse(store.has(id));
		assertFalse(store.online().contains(id));
		assertEquals(42, evictedValue.get());
	}

	@Test
	void getOrCreateWorksWithoutJoin() {
		SimpleEventBus bus = new SimpleEventBus();
		SimplePlayerSessionStore<AtomicInteger> store =
				new SimplePlayerSessionStore<>(bus, AtomicInteger::new);
		UUID id = UUID.randomUUID();
		assertNull(store.get(id));
		assertEquals(0, store.getOrCreate(id).get());
		assertTrue(store.has(id));
	}

	@Test
	void clearShouldEvictAll() {
		SimpleEventBus bus = new SimpleEventBus();
		AtomicInteger evictions = new AtomicInteger();
		SimplePlayerSessionStore<AtomicInteger> store =
				new SimplePlayerSessionStore<>(bus, AtomicInteger::new,
						(playerId, value) -> evictions.incrementAndGet());
		bus.post(new PlayerJoinEvent(player(UUID.randomUUID())));
		bus.post(new PlayerJoinEvent(player(UUID.randomUUID())));
		store.clear();
		assertEquals(0, store.size());
		assertEquals(0, store.online().size());
		assertEquals(2, evictions.get());
	}

	@Test
	void serverStoppingShouldClearAll() {
		SimpleEventBus bus = new SimpleEventBus();
		SimplePlayerSessionStore<AtomicInteger> store =
				new SimplePlayerSessionStore<>(bus, AtomicInteger::new);
		bus.post(new PlayerJoinEvent(player(UUID.randomUUID())));
		bus.post(new ServerStoppingEvent());
		assertEquals(0, store.size());
	}

	@Test
	void closeShouldStopListening() {
		SimpleEventBus bus = new SimpleEventBus();
		SimplePlayerSessionStore<AtomicInteger> store =
				new SimplePlayerSessionStore<>(bus, AtomicInteger::new);
		store.close();
		UUID id = UUID.randomUUID();
		bus.post(new PlayerJoinEvent(player(id)));
		assertFalse(store.has(id));
	}

	@Test
	void concurrentAccessShouldBeSafe() throws InterruptedException {
		SimpleEventBus bus = new SimpleEventBus();
		SimplePlayerSessionStore<AtomicInteger> store =
				new SimplePlayerSessionStore<>(bus, AtomicInteger::new);
		int threads = 4;
		int perThread = 200;
		Thread[] workers = new Thread[threads];
		for (int i = 0; i < threads; i++) {
			workers[i] = new Thread(() -> {
				for (int j = 0; j < perThread; j++) {
					UUID id = UUID.randomUUID();
					store.getOrCreate(id).incrementAndGet();
					store.remove(id);
				}
			});
			workers[i].start();
		}
		for (Thread worker : workers) {
			worker.join();
		}
		assertEquals(0, store.size());
	}
}
