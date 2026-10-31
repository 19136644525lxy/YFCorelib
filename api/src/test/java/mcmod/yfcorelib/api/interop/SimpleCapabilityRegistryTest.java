package mcmod.yfcorelib.api.interop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/** SimpleCapabilityRegistry：注册、查询、重复与类型冲突 */
class SimpleCapabilityRegistryTest {
	interface Energy {
		int stored();
	}

	@Test
	void registerThenGetShouldReturnProvider() {
		SimpleCapabilityRegistry registry = new SimpleCapabilityRegistry();
		CapabilityKey<Energy> key = CapabilityKey.of("test:energy", Energy.class);
		Energy impl = () -> 42;
		registry.register(key, impl);
		assertTrue(registry.isRegistered(key));
		assertEquals(Optional.of(impl), registry.get(key));
	}

	@Test
	void missingKeyShouldReturnEmpty() {
		SimpleCapabilityRegistry registry = new SimpleCapabilityRegistry();
		CapabilityKey<Energy> key = CapabilityKey.of("test:energy", Energy.class);
		assertTrue(registry.get(key).isEmpty());
		assertFalse(registry.isRegistered(key));
	}

	@Test
	void duplicateRegistrationShouldThrow() {
		SimpleCapabilityRegistry registry = new SimpleCapabilityRegistry();
		CapabilityKey<Energy> key = CapabilityKey.of("test:energy", Energy.class);
		registry.register(key, () -> 1);
		IllegalStateException ex = assertThrows(IllegalStateException.class,
				() -> registry.register(key, () -> 2));
		assertTrue(ex.getMessage().contains("已经被"), ex.getMessage());
	}

	@Test
	void typeMismatchOnLookupShouldThrow() {
		SimpleCapabilityRegistry registry = new SimpleCapabilityRegistry();
		CapabilityKey<Energy> energy = CapabilityKey.of("test:shared", Energy.class);
		registry.register(energy, () -> 1);
		CapabilityKey<Runnable> wrong = CapabilityKey.of("test:shared", Runnable.class);
		assertThrows(IllegalArgumentException.class, () -> registry.get(wrong));
	}

	@Test
	void idsShouldBeSnapshot() {
		SimpleCapabilityRegistry registry = new SimpleCapabilityRegistry();
		registry.register(CapabilityKey.of("a:one", Energy.class), () -> 1);
		Set<String> ids = registry.ids();
		registry.register(CapabilityKey.of("a:two", Energy.class), () -> 2);
		assertEquals(Set.of("a:one"), ids);
		assertEquals(2, registry.ids().size());
	}

	@Test
	void usageCountShouldBeIndependentPerRegistry() {
		AtomicInteger calls = new AtomicInteger();
		SimpleCapabilityRegistry registry = new SimpleCapabilityRegistry();
		registry.register(CapabilityKey.of("a:counter", Runnable.class), calls::incrementAndGet);
		registry.get(CapabilityKey.of("a:counter", Runnable.class)).orElseThrow().run();
		registry.get(CapabilityKey.of("a:counter", Runnable.class)).orElseThrow().run();
		assertEquals(2, calls.get());
	}
}
