package mcmod.yfcorelib.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Ids 路径拼接规则测试。 */
class IdsTest {
	@Test
	void idShouldContainNamespace() {
		assertEquals("yfcorelib:core", Ids.id("core"));
	}

	@Test
	void modIdShouldBeConstant() {
		assertEquals("yfcorelib", Ids.MOD_ID);
	}
}
