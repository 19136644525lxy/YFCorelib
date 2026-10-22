package mcmod.yfcorelib.api.data;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SnapshotIdsTest {
	@Test
	void 同输入产出稳定UUID() {
		UUID first = SnapshotIds.stable("mymod", "speed-boost");
		UUID second = SnapshotIds.stable("mymod", "speed-boost");

		assertEquals(first, second, "稳定 UUID 必须跨调用一致");
	}

	@Test
	void 不同key产出不同UUID() {
		assertNotEquals(SnapshotIds.stable("mymod", "a"), SnapshotIds.stable("mymod", "b"));
		assertNotEquals(SnapshotIds.stable("mymod", "a"), SnapshotIds.stable("othermod", "a"));
	}

	@Test
	void 输出为合法UUIDv3格式() {
		UUID id = SnapshotIds.stable("mymod", "k");
		assertEquals(3, id.version(), "nameUUIDFromBytes 产出 v3");
	}
}
