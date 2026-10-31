package mcmod.yfcorelib.api.interop;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

/**
 * SoftDependency：判定逻辑本身（不触碰平台实现）。
 * <p>{@code isLoaded} 需要平台实例，因此这里只覆盖接受 {@code Predicate} 的重载。</p>
 */
class SoftDependencyTest {
	@Test
	void allLoadedShouldRequireEveryMod() {
		Set<String> present = Set.of("a", "b");
		assertTrue(SoftDependency.allLoaded(present::contains, "a", "b"));
		assertFalse(SoftDependency.allLoaded(present::contains, "a", "c"));
	}

	@Test
	void anyLoadedShouldAcceptOneOf() {
		Set<String> present = Set.of("a");
		assertTrue(SoftDependency.anyLoaded(present::contains, "a", "c"));
		assertFalse(SoftDependency.anyLoaded(present::contains, "c", "d"));
	}

	@Test
	void emptyInputShouldBeNeutral() {
		assertTrue(SoftDependency.allLoaded(id -> true));
		assertFalse(SoftDependency.anyLoaded(id -> true));
	}

	@Test
	void invalidIdsShouldThrow() {
		assertThrows(IllegalArgumentException.class, () -> SoftDependency.allLoaded(id -> true, ""));
		assertThrows(IllegalArgumentException.class, () -> SoftDependency.allLoaded(id -> true, "  "));
		assertThrows(IllegalArgumentException.class, () -> SoftDependency.anyLoaded(id -> true, (String) null));
	}

	@Test
	void nullProbeShouldThrow() {
		// 必须显式转型：null 同时匹配 allLoaded(String...) 与 allLoaded(Predicate, String...)
		Predicate<String> probe = null;
		assertThrows(NullPointerException.class, () -> SoftDependency.allLoaded(probe, "a"));
		assertThrows(NullPointerException.class, () -> SoftDependency.anyLoaded(probe, "a"));
	}
}
