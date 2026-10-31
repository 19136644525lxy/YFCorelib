package mcmod.yfcorelib.api.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** WeightedTable：权重比例、空表、构造校验 */
class WeightedTableTest {
	@Test
	void weightsShouldShapeDistribution() {
		WeightedTable<String> table = WeightedTable.<String>create()
				.add("common", 3)
				.add("rare", 1)
				.build();
		assertEquals(2, table.size());
		assertEquals(4, table.totalWeight());

		Rng rng = Rng.of(1234);
		int common = 0;
		int draws = 20_000;
		for (int i = 0; i < draws; i++) {
			if ("common".equals(table.pick(rng).orElseThrow())) {
				common++;
			}
		}
		// 3:1 应为 75%，留 2 个百分点容差
		assertEquals(0.75, common / (double) draws, 0.02);
	}

	@Test
	void singleEntryShouldAlwaysBePicked() {
		WeightedTable<String> table = WeightedTable.<String>create().add("only", 5).build();
		Rng rng = Rng.of(7);
		for (int i = 0; i < 100; i++) {
			assertEquals("only", table.pick(rng).orElseThrow());
		}
	}

	@Test
	void emptyTableShouldReturnEmptyAndFallback() {
		WeightedTable<String> table = WeightedTable.<String>create().build();
		assertTrue(table.isEmpty());
		assertEquals(0, table.totalWeight());
		assertTrue(table.pick(Rng.of(1)).isEmpty());
		assertEquals("fallback", table.pickOr(Rng.of(1), "fallback"));
	}

	@Test
	void invalidEntriesShouldBeRejected() {
		assertThrows(NullPointerException.class, () -> WeightedTable.<String>create().add(null, 1));
		assertThrows(IllegalArgumentException.class, () -> WeightedTable.<String>create().add("x", 0));
		assertThrows(IllegalArgumentException.class, () -> WeightedTable.<String>create().add("x", -1));
		assertThrows(NullPointerException.class, () -> WeightedTable.<String>create().build().pick(null));
	}

	@Test
	void weightOverflowShouldBeReported() {
		WeightedTable.Builder<String> builder = WeightedTable.<String>create()
				.add("a", Integer.MAX_VALUE)
				.add("b", 1);
		assertThrows(ArithmeticException.class, builder::build);
	}
}
