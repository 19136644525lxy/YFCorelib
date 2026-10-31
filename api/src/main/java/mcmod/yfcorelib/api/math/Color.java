package mcmod.yfcorelib.api.math;

/**
 * 不可变颜色：四个分量各自 0–255，不隐含任何颜色空间假设。
 * <p>提供 {@code 0xAARRGGBB} 打包整数的互转，方便与 MC 的 {@code int} 颜色对接；
 * 本类本身不依赖 Minecraft。</p>
 */
public record Color(int red, int green, int blue, int alpha) {
	public static final Color WHITE = new Color(255, 255, 255, 255);
	public static final Color BLACK = new Color(0, 0, 0, 255);
	public static final Color TRANSPARENT = new Color(0, 0, 0, 0);
	public static final Color RED = new Color(255, 0, 0, 255);
	public static final Color GREEN = new Color(0, 255, 0, 255);
	public static final Color BLUE = new Color(0, 0, 255, 255);

	public Color {
		requireByte(red, "red");
		requireByte(green, "green");
		requireByte(blue, "blue");
		requireByte(alpha, "alpha");
	}

	/** 不透明颜色 */
	public static Color of(int red, int green, int blue) {
		return new Color(red, green, blue, 255);
	}

	/** 从 {@code 0xRRGGBB} 解包，alpha 取 255 */
	public static Color rgb(int rgb) {
		return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255);
	}

	/** 从 {@code 0xAARRGGBB} 解包 */
	public static Color argb(int argb) {
		return new Color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);
	}

	/** 打包成 {@code 0xRRGGBB}（丢弃 alpha） */
	public int toRgb() {
		return (red << 16) | (green << 8) | blue;
	}

	/** 打包成 {@code 0xAARRGGBB} */
	public int toArgb() {
		return (alpha << 24) | (red << 16) | (green << 8) | blue;
	}

	/** 线性插值：{@code t} 会被夹紧到 {@code [0,1]}，各分量四舍五入 */
	public Color lerp(Color other, double t) {
		if (other == null) {
			throw new NullPointerException("other");
		}
		double x = Easing.clamp(t);
		return new Color(
				mix(red, other.red, x),
				mix(green, other.green, x),
				mix(blue, other.blue, x),
				mix(alpha, other.alpha, x));
	}

	/** 整体缩放亮度并夹紧；{@code factor < 1} 变暗，{@code > 1} 变亮 */
	public Color scale(double factor) {
		if (Double.isNaN(factor)) {
			throw new IllegalArgumentException("factor 不能是 NaN");
		}
		return new Color(
				scaleChannel(red, factor),
				scaleChannel(green, factor),
				scaleChannel(blue, factor),
				alpha);
	}

	public Color withAlpha(int value) {
		return new Color(red, green, blue, value);
	}

	public Color withRed(int value) {
		return new Color(value, green, blue, alpha);
	}

	public Color withGreen(int value) {
		return new Color(red, value, blue, alpha);
	}

	public Color withBlue(int value) {
		return new Color(red, green, value, alpha);
	}

	/** 感知亮度（Rec. 601），落在 {@code [0,255]} */
	public double luminance() {
		return 0.299 * red + 0.587 * green + 0.114 * blue;
	}

	/** {@code #RRGGBB} */
	public String toHex() {
		return String.format("#%06X", toRgb());
	}

	/** {@code #AARRGGBB} */
	public String toHexArgb() {
		return String.format("#%08X", toArgb());
	}

	private static int mix(int from, int to, double t) {
		return (int) Math.round(from + (to - from) * t);
	}

	private static int scaleChannel(int value, double factor) {
		double scaled = value * factor;
		if (scaled <= 0) {
			return 0;
		}
		return scaled >= 255 ? 255 : (int) Math.round(scaled);
	}

	private static void requireByte(int value, String name) {
		if (value < 0 || value > 255) {
			throw new IllegalArgumentException(name + " 必须在 0–255 之间，实际: " + value);
		}
	}
}
