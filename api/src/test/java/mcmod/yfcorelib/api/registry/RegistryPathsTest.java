package mcmod.yfcorelib.api.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RegistryPathsTest {
	@Test
	void acceptsLegalNamespace() {
		assertEquals("mymod", RegistryPaths.requireNamespace("mymod"));
		assertEquals("my_mod-1.x", RegistryPaths.requireNamespace("my_mod-1.x"));
	}

	@Test
	void rejectsIllegalNamespace() {
		assertThrows(IllegalArgumentException.class, () -> RegistryPaths.requireNamespace(null));
		assertThrows(IllegalArgumentException.class, () -> RegistryPaths.requireNamespace(""));
		assertThrows(IllegalArgumentException.class, () -> RegistryPaths.requireNamespace("MyMod"));
		assertThrows(IllegalArgumentException.class, () -> RegistryPaths.requireNamespace("my mod"));
	}

	@Test
	void acceptsLegalPath() {
		assertEquals("ruby_block", RegistryPaths.requirePath("ruby_block"));
		assertEquals("magic/ruby_block", RegistryPaths.requirePath("magic/ruby_block"));
	}

	@Test
	void rejectsPathWithNamespaceOrUppercase() {
		assertThrows(IllegalArgumentException.class, () -> RegistryPaths.requirePath(null));
		assertThrows(IllegalArgumentException.class, () -> RegistryPaths.requirePath(""));
		assertThrows(IllegalArgumentException.class, () -> RegistryPaths.requirePath("mymod:ruby"));
		assertThrows(IllegalArgumentException.class, () -> RegistryPaths.requirePath("Ruby"));
	}
}
