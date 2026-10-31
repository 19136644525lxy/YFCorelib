package mcmod.yfcorelib.api.mixin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import mcmod.yfcorelib.api.event.EventBus;
import org.junit.jupiter.api.Test;

/** MixinCallbacks：转发到事件总线、可开关、异常不外泄 */
class MixinCallbacksTest {
	record Ping(int value) {
	}

	record Boom() {
	}

	@Test
	void fireShouldDeliverToRegisteredListener() {
		List<Integer> received = new ArrayList<>();
		try (EventBus.Subscription ignored = MixinCallbacks.on(Ping.class, event -> received.add(event.value()))) {
			MixinCallbacks.fire(new Ping(1));
			MixinCallbacks.fire(new Ping(2));
		}
		assertEquals(List.of(1, 2), received);
	}

	@Test
	void closedSubscriptionShouldStopReceiving() {
		List<Integer> received = new ArrayList<>();
		EventBus.Subscription subscription = MixinCallbacks.on(Ping.class, event -> received.add(event.value()));
		MixinCallbacks.fire(new Ping(1));
		subscription.close();
		MixinCallbacks.fire(new Ping(2));
		assertEquals(List.of(1), received);
	}

	@Test
	void nullEventShouldThrow() {
		assertThrows(NullPointerException.class, () -> MixinCallbacks.fire(null));
	}

	@Test
	void disabledShouldDropEvents() {
		List<Integer> received = new ArrayList<>();
		boolean previous = MixinCallbacks.isEnabled();
		try (EventBus.Subscription ignored = MixinCallbacks.on(Ping.class, event -> received.add(event.value()))) {
			MixinCallbacks.setEnabled(false);
			MixinCallbacks.fire(new Ping(1));
			assertTrue(received.isEmpty());
			MixinCallbacks.setEnabled(true);
			MixinCallbacks.fire(new Ping(2));
		} finally {
			MixinCallbacks.setEnabled(previous);
		}
		assertEquals(List.of(2), received);
	}

	@Test
	void listenerExceptionShouldNotEscapeIntoMixinCode() {
		try (EventBus.Subscription ignored = MixinCallbacks.on(Boom.class, event -> {
			throw new IllegalStateException("业务监听器炸了");
		})) {
			// Mixin 里抛异常会直接破坏被注入的原版方法，因此这里必须吞掉
			assertDoesNotThrow(() -> MixinCallbacks.fire(new Boom()));
		}
	}
}
