package mcmod.yfcorelib.api.math;

import java.util.Objects;
import java.util.Random;

/**
 * {@link Rng} 的默认实现：基于 {@link Random}，同种子同序列。
 * <p>不直接用 {@code Random} 的原因是：接口化之后单测可以注入固定序列的实现，
 * 而派生种子（{@link #derive(String)}）需要我们自己定哈希，换实现时序列才不会漂移。</p>
 */
public final class SimpleRng implements Rng {
	private final long seed;
	private final Random random;

	public SimpleRng(long seed) {
		this.seed = seed;
		this.random = new Random(seed);
	}

	@Override
	public long nextLong() {
		return random.nextLong();
	}

	@Override
	public int nextInt(int bound) {
		if (bound <= 0) {
			throw new IllegalArgumentException("bound 必须为正数，实际: " + bound);
		}
		return random.nextInt(bound);
	}

	@Override
	public int nextInt(int origin, int bound) {
		if (bound <= origin) {
			throw new IllegalArgumentException("bound(" + bound + ") 必须大于 origin(" + origin + ")");
		}
		return origin + random.nextInt(bound - origin);
	}

	@Override
	public double nextDouble() {
		return random.nextDouble();
	}

	@Override
	public float nextFloat() {
		return random.nextFloat();
	}

	@Override
	public boolean nextBoolean() {
		return random.nextBoolean();
	}

	@Override
	public long seed() {
		return seed;
	}

	@Override
	public Rng derive(String key) {
		return new SimpleRng(Rngs.mixSeed(seed, key));
	}

	@Override
	public String toString() {
		return "SimpleRng[seed=" + seed + "]";
	}
}
