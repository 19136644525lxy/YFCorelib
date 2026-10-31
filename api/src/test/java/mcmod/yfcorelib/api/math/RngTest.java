package mcmod.yfcorelib.api.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Rng：可复现性、派生独立性、边界校验 */
class RngTest {
	@Test
	void sameSeedShouldProduceSameSequence() {
		Rng a = Rng.of(12345);
		Rng b = Rng.of(12345);
		for (int i = 0; i < 50; i++) {
			assertEquals(a.nextLong(), b.nextLong());
		}
	}

	@Test
	void differentSeedsShouldDiverge() {
		Rng a = Rng.of(1);
		Rng b = Rng.of(2);
		boolean differs = false;
		for (int i = 0; i < 10 && !differs; i++) {
			if (a.nextLong() != b.nextLong()) {
				differs = true;
			}
		}
		assertTrue(differs, "不同种子不应产生完全相同的序列");
	}

	@Test
	void deriveShouldBeStableAndIndependent() {
		Rng base = Rng.of(7);
		long first = base.derive("combat:damage").nextLong();
		long again = Rng.of(7).derive("combat:damage").nextLong();
		assertEquals(first, again, "同名派生必须可复现");

		long other = Rng.of(7).derive("loot:chest").nextLong();
		assertNotEquals(first, other, "不同名字应给出不同子序列");

		// of(seed, parts...) 与链式 derive 等价
		assertEquals(first, Rng.of(7, "combat:damage").nextLong());
	}

	@Test
	void nextIntShouldStayWithinBounds() {
		Rng rng = Rng.of(99);
		for (int i = 0; i < 500; i++) {
			int value = rng.nextInt(7);
			assertTrue(value >= 0 && value < 7, "越界: " + value);
		}
		for (int i = 0; i < 500; i++) {
			int value = rng.nextInt(-3, 4);
			assertTrue(value >= -3 && value < 4, "越界: " + value);
		}
	}

	@Test
	void invalidBoundsShouldThrow() {
		Rng rng = Rng.of(1);
		assertThrows(IllegalArgumentException.class, () -> rng.nextInt(0));
		assertThrows(IllegalArgumentException.class, () -> rng.nextInt(-1));
		assertThrows(IllegalArgumentException.class, () -> rng.nextInt(5, 5));
		assertThrows(IllegalArgumentException.class, () -> rng.nextInt(10, 2));
	}

	@Test
	void nextDoubleAndFloatShouldBeInUnitRange() {
		Rng rng = Rng.of(3);
		for (int i = 0; i < 200; i++) {
			double d = rng.nextDouble();
			assertTrue(d >= 0 && d < 1, "double 越界: " + d);
			float f = rng.nextFloat();
			assertTrue(f >= 0 && f < 1, "float 越界: " + f);
		}
	}

	@Test
	void positionFactoryShouldBeDeterministicPerCoordinate() {
		long atOrigin = Rng.at(42, 0, 0).nextLong();
		assertEquals(atOrigin, Rng.at(42, 0, 0).nextLong());
		assertNotEquals(atOrigin, Rng.at(42, 1, 0).nextLong());
		assertNotEquals(atOrigin, Rng.at(42, 0, 1).nextLong());
		assertNotEquals(atOrigin, Rng.at(43, 0, 0).nextLong());
	}

	@Test
	void seedShouldBeReportedBack() {
		assertEquals(2024L, Rng.of(2024).seed());
	}
}
