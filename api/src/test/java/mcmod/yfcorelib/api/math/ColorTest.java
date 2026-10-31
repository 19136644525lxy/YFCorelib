package mcmod.yfcorelib.api.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Color：打包互转、插值、分量校验 */
class ColorTest {
	@Test
	void argbRoundTripShouldPreserveAllChannels() {
		Color color = new Color(0x12, 0x34, 0x56, 0x78);
		assertEquals(0x78123456, color.toArgb());
		assertEquals(color, Color.argb(color.toArgb()));
	}

	@Test
	void rgbRoundTripShouldDefaultToOpaque() {
		Color color = Color.rgb(0x123456);
		assertEquals(0x12, color.red());
		assertEquals(0x34, color.green());
		assertEquals(0x56, color.blue());
		assertEquals(255, color.alpha());
		assertEquals(0x123456, color.toRgb());
	}

	@Test
	void componentsShouldBeValidated() {
		assertThrows(IllegalArgumentException.class, () -> new Color(-1, 0, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> new Color(0, 256, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> Color.of(0, 0, 300));
	}

	@Test
	void lerpShouldWalkBetweenColors() {
		Color from = Color.of(0, 0, 0);
		Color to = Color.of(255, 255, 255);
		assertEquals(Color.of(0, 0, 0), from.lerp(to, 0));
		assertEquals(Color.of(255, 255, 255), from.lerp(to, 1));
		Color mid = from.lerp(to, 0.5);
		assertEquals(128, mid.red());
		// 越界参数被夹紧
		assertEquals(0, from.lerp(to, -1).red());
		assertEquals(255, from.lerp(to, 3).red());
	}

	@Test
	void scaleShouldClampAndKeepAlpha() {
		Color base = new Color(100, 200, 50, 128);
		Color brighter = base.scale(100);
		assertEquals(255, brighter.red());
		assertEquals(255, brighter.green());
		assertEquals(128, brighter.alpha(), "缩放亮度不应改变 alpha");
		Color darker = base.scale(0);
		assertEquals(0, darker.red());
		assertThrows(IllegalArgumentException.class, () -> base.scale(Double.NaN));
	}

	@Test
	void withMethodsShouldReplaceSingleChannel() {
		Color base = new Color(1, 2, 3, 4);
		assertEquals(new Color(9, 2, 3, 4), base.withRed(9));
		assertEquals(new Color(1, 9, 3, 4), base.withGreen(9));
		assertEquals(new Color(1, 2, 9, 4), base.withBlue(9));
		assertEquals(new Color(1, 2, 3, 9), base.withAlpha(9));
	}

	@Test
	void hexShouldBeZeroPadded() {
		assertEquals("#000000", Color.BLACK.toHex());
		assertEquals("#FF8000", Color.rgb(0xFF8000).toHex());
		assertEquals("#80FF8000", new Color(0xFF, 0x80, 0x00, 0x80).toHexArgb());
	}

	@Test
	void luminanceShouldFollowRec601() {
		assertEquals(255.0, Color.WHITE.luminance(), 1e-9);
		assertEquals(0.0, Color.BLACK.luminance(), 1e-9);
		assertTrue(Color.GREEN.luminance() > Color.BLUE.luminance());
	}

	@Test
	void lerpShouldRejectNull() {
		assertThrows(NullPointerException.class, () -> Color.WHITE.lerp(null, 0.5));
	}
}
