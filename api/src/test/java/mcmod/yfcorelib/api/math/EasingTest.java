package mcmod.yfcorelib.api.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Easing：端点固定、输入夹紧、组合器语义 */
class EasingTest {
	private static final double EPS = 1e-9;

	@Test
	void linearShouldBeIdentityAndClampInput() {
		assertEquals(0.0, Easing.LINEAR.apply(0), EPS);
		assertEquals(0.5, Easing.LINEAR.apply(0.5), EPS);
		assertEquals(1.0, Easing.LINEAR.apply(1), EPS);
		// 越界输入夹紧，NaN 归零
		assertEquals(0.0, Easing.LINEAR.apply(-5), EPS);
		assertEquals(1.0, Easing.LINEAR.apply(5), EPS);
		assertEquals(0.0, Easing.LINEAR.apply(Double.NaN), EPS);
	}

	@Test
	void standardCurvesShouldAnchorAtBothEnds() {
		Easing[] curves = {
				Easing.EASE_IN_QUAD, Easing.EASE_OUT_QUAD, Easing.EASE_IN_OUT_QUAD,
				Easing.EASE_IN_CUBIC, Easing.EASE_OUT_CUBIC, Easing.EASE_IN_OUT_CUBIC,
				Easing.EASE_IN_SINE, Easing.EASE_OUT_SINE, Easing.EASE_IN_OUT_SINE,
				Easing.EASE_IN_EXPO, Easing.EASE_OUT_EXPO, Easing.EASE_OUT_BACK,
		};
		for (Easing curve : curves) {
			assertEquals(0.0, curve.apply(0), EPS, "起点必须为 0");
			assertEquals(1.0, curve.apply(1), EPS, "终点必须为 1");
		}
	}

	@Test
	void easeInShouldStartSlowerThanLinear() {
		assertTrue(Easing.EASE_IN_QUAD.apply(0.25) < 0.25);
		assertTrue(Easing.EASE_IN_CUBIC.apply(0.5) < 0.5);
		// 缓出同理：前半段跑得更快
		assertTrue(Easing.EASE_OUT_QUAD.apply(0.25) > 0.25);
	}

	@Test
	void outBackShouldOvershootAboveOne() {
		double max = 0;
		for (int i = 0; i <= 100; i++) {
			max = Math.max(max, Easing.EASE_OUT_BACK.apply(i / 100.0));
		}
		assertTrue(max > 1.0, "回弹曲线应当短暂越过 1，实际最大值 " + max);
	}

	@Test
	void reverseShouldMirrorCurve() {
		Easing reversed = Easing.reverse(Easing.EASE_IN_QUAD);
		// reverse(inQuad)(t) == 1 - (1-t)^2 == outQuad(t)
		for (int i = 0; i <= 10; i++) {
			double t = i / 10.0;
			assertEquals(Easing.EASE_OUT_QUAD.apply(t), reversed.apply(t), EPS);
		}
	}

	@Test
	void inOutShouldBeSymmetricAroundHalf() {
		Easing curve = Easing.inOut(Easing.EASE_IN_QUAD);
		for (int i = 0; i <= 10; i++) {
			double t = i / 10.0;
			assertEquals(1.0, curve.apply(t) + curve.apply(1 - t), EPS);
		}
	}

	@Test
	void interpolateShouldMapBetweenBounds() {
		assertEquals(12.5, Easing.LINEAR.interpolate(10, 20, 0.25), EPS);
		assertEquals(10.0, Easing.LINEAR.interpolate(10, 20, -1), EPS);
		assertEquals(20.0, Easing.LINEAR.interpolate(10, 20, 2), EPS);
	}

	@Test
	void combinationsShouldRejectNull() {
		assertThrows(NullPointerException.class, () -> Easing.reverse(null));
		assertThrows(NullPointerException.class, () -> Easing.inOut(null));
	}
}
