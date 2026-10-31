package mcmod.yfcorelib.api.math;

import java.nio.charset.StandardCharsets;

/**
 * 种子混合工具：把"主种子 + 字符串"折成稳定的 64 位派生种子。
 * <p>用 splitmix64 收尾，避免相邻种子产生相邻序列；
 * 字符串按 UTF-8 参与哈希，因此中文 id 同样可复现。</p>
 * <p>有意不用 {@link String#hashCode()}：它只有 32 位，派生空间太窄且实现细节随 JDK 版本变化。</p>
 */
final class Rngs {
	private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

	private Rngs() {
	}

	/** 把种子与字符串键混合成新种子 */
	static long mixSeed(long seed, String key) {
		long h = mix(seed ^ GOLDEN_GAMMA);
		for (byte b : key.getBytes(StandardCharsets.UTF_8)) {
			h ^= b & 0xFFL;
			h = mix(h);
		}
		h ^= key.length();
		return mix(h);
	}

	/** splitmix64 收尾器：一个输入位的变化会扩散到全部输出位 */
	static long mix(long value) {
		long z = value;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/** 把两个坐标折进种子：{@code (x, z)} 相同的方块永远得到同一随机源 */
	static long mixPosition(long seed, int x, int z) {
		long h = mix(seed ^ GOLDEN_GAMMA);
		h = mix(h ^ x);
		return mix(h ^ z);
	}
}
