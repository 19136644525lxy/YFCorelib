package mcmod.yfcorelib.api.math;

/**
 * 缓动函数：把归一化进度 {@code t ∈ [0,1]} 映射成插值系数。
 * <p>纯函数、零状态、零平台依赖，可直接在 api 层单测。</p>
 * <p>除 {@link #EASE_OUT_BACK} 这类"回弹"曲线外，输出同样落在 {@code [0,1]}；
 * 输入始终先经 {@link #clamp(double)}，因此调用方不必自己夹紧。</p>
 */
@FunctionalInterface
public interface Easing {
	/** 线性：{@code t -> t} */
	Easing LINEAR = Easing::clamp;

	/** 二次缓入：起步慢、结束快 */
	Easing EASE_IN_QUAD = t -> {
		double x = clamp(t);
		return x * x;
	};

	/** 二次缓出：起步快、结束慢 */
	Easing EASE_OUT_QUAD = t -> {
		double x = clamp(t);
		return x * (2 - x);
	};

	/** 二次缓入缓出 */
	Easing EASE_IN_OUT_QUAD = t -> {
		double x = clamp(t);
		return x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2;
	};

	/** 三次缓入 */
	Easing EASE_IN_CUBIC = t -> {
		double x = clamp(t);
		return x * x * x;
	};

	/** 三次缓出 */
	Easing EASE_OUT_CUBIC = t -> {
		double x = clamp(t);
		return 1 - Math.pow(1 - x, 3);
	};

	/** 三次缓入缓出 */
	Easing EASE_IN_OUT_CUBIC = t -> {
		double x = clamp(t);
		return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
	};

	/** 正弦缓入 */
	Easing EASE_IN_SINE = t -> 1 - Math.cos(clamp(t) * Math.PI / 2);

	/** 正弦缓出 */
	Easing EASE_OUT_SINE = t -> Math.sin(clamp(t) * Math.PI / 2);

	/** 正弦缓入缓出 */
	Easing EASE_IN_OUT_SINE = t -> -(Math.cos(Math.PI * clamp(t)) - 1) / 2;

	/** 指数缓入 */
	Easing EASE_IN_EXPO = t -> {
		double x = clamp(t);
		return x <= 0 ? 0 : Math.pow(2, 10 * (x - 1));
	};

	/** 指数缓出 */
	Easing EASE_OUT_EXPO = t -> {
		double x = clamp(t);
		return x >= 1 ? 1 : 1 - Math.pow(2, -10 * x);
	};

	/**
	 * 回弹缓出：会短暂越过 1 再回落，用于"弹出"手感。
	 * <p>注意它的输出可以大于 1，插值到需要夹紧的场合要自己再 clamp。</p>
	 */
	Easing EASE_OUT_BACK = t -> {
		double x = clamp(t);
		double c1 = 1.70158;
		double c3 = c1 + 1;
		return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
	};

	/**
	 * 计算缓动系数。
	 *
	 * @param t 归一化进度，允许越界（内部会夹紧）
	 * @return 插值系数
	 */
	double apply(double t);

	/** 把进度夹紧到 {@code [0,1]}；{@code NaN} 返回 0 */
	static double clamp(double t) {
		if (Double.isNaN(t)) {
			return 0;
		}
		if (t < 0) {
			return 0;
		}
		return t > 1 ? 1 : t;
	}

	/**
	 * 时间反演：把曲线沿 {@code (0.5, 0.5)} 翻转，即 {@code 1 - ease(1 - t)}。
	 * <p>因此 {@code reverse(EASE_IN_QUAD)} 与 {@code EASE_OUT_QUAD} 等价。</p>
	 *
	 * @throws NullPointerException easing 为 null
	 */
	static Easing reverse(Easing easing) {
		java.util.Objects.requireNonNull(easing, "easing");
		return t -> 1 - easing.apply(1 - clamp(t));
	}

	/**
	 * 前半段原曲线、后半段镜像，得到关于 {@code (0.5, 0.5)} 对称的缓入缓出。
	 * <p>对已经是 in-out 的曲线调用不会报错，但通常没有意义。</p>
	 *
	 * @throws NullPointerException easing 为 null
	 */
	static Easing inOut(Easing easing) {
		java.util.Objects.requireNonNull(easing, "easing");
		return t -> {
			double x = clamp(t);
			return x < 0.5
					? easing.apply(x * 2) / 2
					: 1 - easing.apply((1 - x) * 2) / 2;
		};
	}

	/**
	 * 把 {@code [from, to]} 的进度经缓动映射后线性插值。
	 *
	 * @param from 起点
	 * @param to   终点
	 * @return 按 {@code t} 插值后的值
	 */
	default double interpolate(double from, double to, double t) {
		return from + (to - from) * apply(t);
	}
}
