package mcmod.yfcorelib.api.math;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 加权随机表：按权重比例抽取，构造后不可变。
 * <p>抽取复杂度是 O(log n)（权重前缀和 + 二分），因此条目多时也不会线性变慢。</p>
 * <p>典型用法是"战利品/掉落池"：{@code create().add(A, 70).add(B, 25).add(C, 5)}。</p>
 *
 * @param <T> 条目类型
 */
public final class WeightedTable<T> {
	private final List<T> values;
	private final int[] cumulative;
	private final int total;

	private WeightedTable(List<T> values, int[] cumulative, int total) {
		this.values = values;
		this.cumulative = cumulative;
		this.total = total;
	}

	public static <T> Builder<T> create() {
		return new Builder<>();
	}

	/** 条目数量 */
	public int size() {
		return values.size();
	}

	/** 权重总和；空表为 0 */
	public int totalWeight() {
		return total;
	}

	public boolean isEmpty() {
		return values.isEmpty();
	}

	/**
	 * 按权重抽一个条目；空表返回 {@link Optional#empty()}。
	 *
	 * @throws NullPointerException rng 为 null
	 */
	public Optional<T> pick(Rng rng) {
		if (rng == null) {
			throw new NullPointerException("rng");
		}
		if (values.isEmpty()) {
			return Optional.empty();
		}
		int roll = rng.nextInt(total);
		// 找第一个 cumulative[i] > roll 的下标：roll 落在这个条目的区间内
		int low = 0;
		int high = cumulative.length - 1;
		while (low < high) {
			int mid = (low + high) >>> 1;
			if (cumulative[mid] > roll) {
				high = mid;
			} else {
				low = mid + 1;
			}
		}
		return Optional.of(values.get(low));
	}

	/** 按权重抽一个条目，空表返回 {@code fallback} */
	public T pickOr(Rng rng, T fallback) {
		return pick(rng).orElse(fallback);
	}

	public static final class Builder<T> {
		private final List<T> values = new ArrayList<>();
		private final List<Integer> weights = new ArrayList<>();

		private Builder() {
		}

		/**
		 * 追加条目。
		 *
		 * @param value  条目，不可为 null
		 * @param weight 权重，必须为正数（0 或负权重会让比例含义变得不可靠）
		 */
		public Builder<T> add(T value, int weight) {
			if (value == null) {
				throw new NullPointerException("value");
			}
			if (weight <= 0) {
				throw new IllegalArgumentException("权重必须为正数，实际: " + weight);
			}
			values.add(value);
			weights.add(weight);
			return this;
		}

		/**
		 * 构建不可变表。空表合法（{@link #pick(Rng)} 返回空），便于"配置里一条都没写"的场合。
		 *
		 * @throws ArithmeticException 权重总和超过 {@code Integer.MAX_VALUE}
		 */
		public WeightedTable<T> build() {
			int[] cumulative = new int[weights.size()];
			int sum = 0;
			for (int i = 0; i < weights.size(); i++) {
				sum = Math.addExact(sum, weights.get(i));
				cumulative[i] = sum;
			}
			return new WeightedTable<>(List.copyOf(values), cumulative, sum);
		}
	}
}
