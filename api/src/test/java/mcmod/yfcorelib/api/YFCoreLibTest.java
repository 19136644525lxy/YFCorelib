package mcmod.yfcorelib.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** 门面约束：COMMON 侧无刻驱动，必须显式拒绝而不是静默受理任务 */
class YFCoreLibTest {
	@Test
	void commonSideSchedulerIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> YFCoreLib.scheduler(Side.COMMON));
	}

	@Test
	void clientAndServerSchedulersAreDistinctAndStable() {
		assertNotSame(YFCoreLib.scheduler(Side.CLIENT), YFCoreLib.scheduler(Side.SERVER));
		assertSame(YFCoreLib.scheduler(Side.CLIENT), YFCoreLib.scheduler(Side.CLIENT));
		assertSame(YFCoreLib.scheduler(Side.SERVER), YFCoreLib.scheduler(Side.SERVER));
	}

	@Test
	void modIdHasSingleSourceOfTruth() {
		assertEquals(Ids.MOD_ID, YFCoreLib.MOD_ID);
		assertEquals("yfcorelib:core", Ids.id("core"));
	}
}
