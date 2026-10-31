package mcmod.yfcorelib.api.math;

/**
 * 可复现的随机源：同一种子产生同一序列，便于复现 bug 与做确定性单测。
 * <p>{@link #derive(String)} 用来把"主种子 + 名字"派生成独立子序列：
 * 各功能互不干扰，且不会因为别处多取了一个数就整体错位。</p>
 * <p>实现不保证线程安全；需要并发时每线程各持一个实例。</p>
 */
public interface Rng {
	/** 下一个 64 位随机数 */
	long nextLong();

	/** 下一个 {@code [0, bound)} 的随机整数 */
	int nextInt(int bound);

	/** 下一个 {@code [origin, bound)} 的随机整数 */
	int nextInt(int origin, int bound);

	/** 下一个 {@code [0, 1)} 的随机双精度数 */
	double nextDouble();

	/** 下一个 {@code [0, 1)} 的随机单精度数 */
	float nextFloat();

	/** 下一个布尔值 */
	boolean nextBoolean();

	/** 本随机源的种子（仅 {@link SimpleRng} 这类有种子实现才有意义） */
	long seed();

	/**
	 * 派生出与当前种子绑定的子随机源：同名重复调用得到同一子序列，不同名字互不影响。
	 *
	 * @param key 子序列名字，通常用 {@code namespace:path}
	 */
	Rng derive(String key);

	/** 用指定种子创建随机源 */
	static Rng of(long seed) {
		return new SimpleRng(seed);
	}

	/**
	 * 用"主种子 + 若干名字片段"创建派生随机源。
	 * <p>等价于 {@code of(seed).derive(part0).derive(part1)...}，但只哈希一次。</p>
	 */
	static Rng of(long seed, String... parts) {
		long mixed = seed;
		for (String part : parts) {
			mixed = Rngs.mixSeed(mixed, part);
		}
		return new SimpleRng(mixed);
	}

	/** 用系统熵创建随机源，仅用于"每次启动都不同"的场合 */
	static Rng random() {
		return new SimpleRng(System.nanoTime() ^ System.currentTimeMillis());
	}

	/**
	 * 按"世界种子 + 方块坐标"创建随机源：同一坐标永远得到同一序列。
	 * <p>适合"每个方块各自随机但世界可复现"的场景（不依赖遍历顺序）。</p>
	 */
	static Rng at(long worldSeed, int x, int z) {
		return new SimpleRng(Rngs.mixPosition(worldSeed, x, z));
	}
}
