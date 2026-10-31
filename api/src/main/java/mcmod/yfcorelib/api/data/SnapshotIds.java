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
		if (modId.isEmpty()) {
			throw new IllegalArgumentException("modId 不能为空");
		}
		if (modId.indexOf(':') >= 0) {
			// 派生串是 modId + ":" + key：modId 里带冒号会与 key 的分隔符混淆，
			// ("a:b","c") 与 ("a","b:c") 会算出同一 UUID
			throw new IllegalArgumentException("modId 不得包含冒号: '" + modId + "'");
		}
		return UUID.nameUUIDFromBytes((modId + ":" + key).getBytes(StandardCharsets.UTF_8));
	}
}
