package mcmod.yfcorelib.api.math;

/**
 * 二维 Perlin 梯度噪声：输出归一化到 {@code [-1,1]}，同种子同坐标恒定。
 * <p>坐标取整不做边界回绕假设，任意实数（含负数）都可用；方格周期是 256，远大于常规地形尺度。</p>
 * <p>纯函数、无状态（构造后只读），可跨线程共享。</p>
 */
public final class Noise {
	/** 8 个单位梯度方向；用单位向量是为了让不同方向的振幅一致 */
	private static final double[][] GRADIENTS = {
			{1, 0}, {-1, 0}, {0, 1}, {0, -1},
			{0.7071067811865476, 0.7071067811865476},
			{-0.7071067811865476, 0.7071067811865476},
			{0.7071067811865476, -0.7071067811865476},
			{-0.7071067811865476, -0.7071067811865476},
	};

	/** 单位梯度下 2D Perlin 的理论极值约 0.7071，乘此系数把值域拉满到 [-1,1] */
	private static final double NORMALIZE = 1.4142135623730951;

	private final int[] perm = new int[512];

	public Noise(long seed) {
		int[] p = new int[256];
		for (int i = 0; i < 256; i++) {
			p[i] = i;
		}
		// 自己洗牌而不用 java.util.Random#nextInt 之外的东西：种子相同则排列相同
		Rng rng = Rng.of(seed);
		for (int i = 255; i > 0; i--) {
			int j = rng.nextInt(i + 1);
			int tmp = p[i];
			p[i] = p[j];
			p[j] = tmp;
		}
		for (int i = 0; i < 512; i++) {
			perm[i] = p[i & 255];
		}
	}

	/** 单层二维噪声，返回 {@code [-1,1]} */
	public double noise2(double x, double y) {
		int xi = floorToInt(x) & 255;
		int yi = floorToInt(y) & 255;
		double xf = x - Math.floor(x);
		double yf = y - Math.floor(y);
		double u = fade(xf);
		double v = fade(yf);

		int aa = perm[perm[xi] + yi];
		int ab = perm[perm[xi] + yi + 1];
		int ba = perm[perm[xi + 1] + yi];
		int bb = perm[perm[xi + 1] + yi + 1];

		double x1 = lerp(u, grad(aa, xf, yf), grad(ba, xf - 1, yf));
		double x2 = lerp(u, grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1));
		double value = lerp(v, x1, x2) * NORMALIZE;
		return value < -1 ? -1 : (value > 1 ? 1 : value);
	}

	/** 两层常用默认参数的分形噪声（2 层、频率 ×2、振幅 ×0.5） */
	public double fbm2(double x, double y, int octaves) {
		return fbm2(x, y, octaves, 2.0, 0.5);
	}

	/**
	 * 分形叠加（fBm）：每层频率乘 {@code lacunarity}、振幅乘 {@code gain}，最后按振幅总和归一化。
	 *
	 * @param octaves    叠加层数，必须 ≥ 1
	 * @param lacunarity 频率倍数，通常 2.0
	 * @param gain       振幅衰减，通常 0.5
	 * @return {@code [-1,1]}
	 * @throws IllegalArgumentException 参数越界
	 */
	public double fbm2(double x, double y, int octaves, double lacunarity, double gain) {
		if (octaves < 1) {
			throw new IllegalArgumentException("octaves 必须 ≥ 1，实际: " + octaves);
		}
		if (!(lacunarity > 0) || Double.isInfinite(lacunarity)) {
			throw new IllegalArgumentException("lacunarity 必须为正的有限数，实际: " + lacunarity);
		}
		if (!(gain > 0) || Double.isInfinite(gain)) {
			throw new IllegalArgumentException("gain 必须为正的有限数，实际: " + gain);
		}
		double sum = 0;
		double amplitude = 1;
		double frequency = 1;
		double amplitudeTotal = 0;
		for (int i = 0; i < octaves; i++) {
			sum += noise2(x * frequency, y * frequency) * amplitude;
			amplitudeTotal += amplitude;
			amplitude *= gain;
			frequency *= lacunarity;
		}
		double value = sum / amplitudeTotal;
		return value < -1 ? -1 : (value > 1 ? 1 : value);
	}

	private static int floorToInt(double value) {
		int i = (int) value;
		return value < i ? i - 1 : i;
	}

	/** Perlin 的五次插值曲线：一阶、二阶导在端点连续，避免方格边界出现折痕 */
	private static double fade(double t) {
		return t * t * t * (t * (t * 6 - 15) + 10);
	}

	private static double lerp(double t, double from, double to) {
		return from + t * (to - from);
	}

	private static double grad(int hash, double x, double y) {
		double[] g = GRADIENTS[hash & 7];
		return g[0] * x + g[1] * y;
	}
}
