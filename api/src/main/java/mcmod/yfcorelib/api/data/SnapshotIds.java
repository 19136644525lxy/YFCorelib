package mcmod.yfcorelib.api.data;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * 属性快照稳定 UUID：由 {@code modId:key} 派生，跨重启不变，避免重复叠加修改器。
 * <p>common 层的 AttributeModifierSnapshot 使用本工具生成修改器 UUID。</p>
 */
public final class SnapshotIds {
	private SnapshotIds() {
	}

	public static UUID stable(String modId, String key) {
		Objects.requireNonNull(modId, "modId");
		Objects.requireNonNull(key, "key");
		return UUID.nameUUIDFromBytes((modId + ":" + key).getBytes(StandardCharsets.UTF_8));
	}
}
