package mcmod.yfcorelib.api.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/** SimpleSyncEngine：快照比较免重发、三模式独立开关、订阅管理 */
class SimpleSyncEngineTest {
	private record Sent(UUID player, String snapshot) {
	}

	private static SimpleSyncEngine<String> engine(List<Sent> sink) {
		return new SimpleSyncEngine<>((id, s) -> sink.add(new Sent(id, s)));
	}

	private static final UUID P1 = UUID.randomUUID();
	private static final UUID P2 = UUID.randomUUID();

	@Test
	void publishShouldSendOnChangeByDefault() {
		List<Sent> sink = new ArrayList<>();
		SimpleSyncEngine<String> engine = engine(sink);
		engine.publish(P1, "v1");
		assertEquals(1, sink.size());
		// 相同快照不重发
		engine.publish(P1, "v1");
		assertEquals(1, sink.size());
		// 内容变化才重发
		engine.publish(P1, "v2");
		assertEquals(2, sink.size());
		assertEquals("v2", engine.snapshot(P1));
	}

	@Test
	void syncOnChangeDisabledShouldSuppressSend() {
		List<Sent> sink = new ArrayList<>();
		SimpleSyncEngine<String> engine = engine(sink);
		engine.syncOnChange(false);
		engine.publish(P1, "v1");
		engine.publish(P1, "v2");
		assertEquals(0, sink.size());
		assertEquals("v2", engine.snapshot(P1));
	}

	@Test
	void syncOnLoginShouldResendSnapshot() {
		List<Sent> sink = new ArrayList<>();
		SimpleSyncEngine<String> engine = engine(sink);
		engine.publish(P1, "v1");
		sink.clear();
		// 默认开启：加入即重发；未发布过的玩家不发送
		engine.onJoin(P1);
		engine.onJoin(P2);
		assertEquals(1, sink.size());
		assertEquals(new Sent(P1, "v1"), sink.get(0));
		// 关闭后不再发送
		engine.syncOnLogin(false);
		engine.onJoin(P1);
		assertEquals(1, sink.size());
		assertFalse(engine.syncOnLogin());
	}

	@Test
	void syncEveryTicksShouldResendPeriodically() {
		List<Sent> sink = new ArrayList<>();
		SimpleSyncEngine<String> engine = engine(sink);
		engine.syncEveryTicks(0).syncOnChange(false);
		engine.publish(P1, "v1");
		engine.publish(P2, "v2");
		assertEquals(0, sink.size());
		// 周期 3 刻：前两次不发，第三次发全部
		engine.syncEveryTicks(3);
		engine.tick();
		engine.tick();
		assertEquals(0, sink.size());
		engine.tick();
		assertEquals(2, sink.size());
		engine.tick();
		engine.tick();
		assertEquals(2, sink.size());
		engine.tick();
		assertEquals(4, sink.size());
	}

	@Test
	void onLeaveShouldDropCache() {
		List<Sent> sink = new ArrayList<>();
		SimpleSyncEngine<String> engine = engine(sink);
		engine.syncOnChange(false);
		engine.publish(P1, "v1");
		assertEquals("v1", engine.snapshot(P1));
		engine.onLeave(P1);
		assertNull(engine.snapshot(P1));
		// 离开后 tick 不再发送该玩家
		engine.syncEveryTicks(1);
		engine.tick();
		assertEquals(0, sink.size());
	}

	@Test
	void subscriptionShouldReceiveAndBeClosable() throws Exception {
		SimpleSyncEngine<String> engine = engine(new ArrayList<>());
		AtomicInteger received = new AtomicInteger();
		AutoCloseable sub = engine.subscribe((id, s) -> received.incrementAndGet());
		engine.receive(P1, "v1");
		engine.receive(P1, "v2");
		assertEquals(2, received.get());
		sub.close();
		engine.receive(P1, "v3");
		assertEquals(2, received.get());
	}

	@Test
	void builderShouldValidateInput() {
		SimpleSyncEngine<String> engine = engine(new ArrayList<>());
		assertThrows(NullPointerException.class, () -> engine.publish(null, "v"));
		assertThrows(NullPointerException.class, () -> engine.publish(P1, null));
		assertThrows(NullPointerException.class, () -> engine.subscribe(null));
		assertThrows(IllegalArgumentException.class, () -> engine.syncEveryTicks(-1));
	}
}
