package mcmod.yfcorelib.api.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Noise：确定性、值域、连续性、参数校验 */
class NoiseTest {
	@Test
	void sameSeedShouldBeDeterministic() {
		Noise a = new Noise(42);
		Noise b = new Noise(42);
		for (int i = 0; i < 20; i++) {
			double x = i * 0.37;
			double y = i * -0.61;
			assertEquals(a.noise2(x, y), b.noise2(x, y), 1e-12);
		}
	}

	@Test
	void differentSeedsShouldDiffer() {
		Noise a = new Noise(1);
		Noise b = new Noise(2);
		boolean differs = false;
		for (int i = 0; i < 20 && !differs; i++) {
			double x = i * 0.37;
			double y = i * 0.61;
			if (Math.abs(a.noise2(x, y) - b.noise2(x, y)) > 1e-6) {
				differs = true;
			}
		}
		assertTrue(differs, "不同种子不应给出完全相同的噪声场");
	}

	@Test
	void valuesShouldStayInUnitRange() {
		Noise noise = new Noise(7);
		for (int i = -20; i <= 20; i++) {
			for (int j = -20; j <= 20; j++) {
				double value = noise.noise2(i * 0.37, j * 0.41);
				assertTrue(value >= -1 && value <= 1, "越界: " + value);
			}
		}
	}

	@Test
	void fbmShouldStayInUnitRangeAndEqualSingleOctave() {
		Noise noise = new Noise(7);
		for (int i = 0; i < 20; i++) {
			double x = i * 0.29;
			double y = i * -0.53;
			double fbm = noise.fbm2(x, y, 4);
			assertTrue(fbm >= -1 && fbm <= 1, "fbm 越界: " + fbm);
			// 单层 fBm 就是原始噪声
			assertEquals(noise.noise2(x, y), noise.fbm2(x, y, 1), 1e-12);
		}
	}

	@Test
	void neighbouringSamplesShouldBeContinuous() {
		Noise noise = new Noise(11);
		double base = noise.noise2(0.5, 0.5);
		double nudged = noise.noise2(0.5 + 1e-4, 0.5);
		assertTrue(Math.abs(base - nudged) < 1e-3, "相邻采样跳变过大: " + Math.abs(base - nudged));
	}

	@Test
	void negativeCoordinatesShouldWork() {
		Noise noise = new Noise(3);
		double value = noise.noise2(-12.5, -7.25);
		assertTrue(value >= -1 && value <= 1);
		// 周期 256，不是"负数就回绕到 0"
		assertNotEquals(noise.noise2(0, 0), value);
	}

	@Test
	void invalidFbmParametersShouldThrow() {
		Noise noise = new Noise(1);
		assertThrows(IllegalArgumentException.class, () -> noise.fbm2(0, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> noise.fbm2(0, 0, 3, 0, 0.5));
		assertThrows(IllegalArgumentException.class, () -> noise.fbm2(0, 0, 3, 2, 0));
		assertThrows(IllegalArgumentException.class, () -> noise.fbm2(0, 0, 3, Double.POSITIVE_INFINITY, 0.5));
	}
}
