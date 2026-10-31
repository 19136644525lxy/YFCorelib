package mcmod.yfcorelib.api.interop;

import java.util.Objects;

/**
 * 能力键：用"字符串 id + 类型"唯一标识一项可插拔能力。
 * <p>典型场景：本库或基础模组定义 {@code CapabilityKey<EnergyStorage> ENERGY}，
 * 由安装了某个科技模组的附属去注册实现；没有实现时调用方拿到 {@code Optional.empty()}，
 * 从而在不硬依赖对方的前提下按需启用功能。</p>
 *
 * @param id   能力 id，建议 {@code namespace:path}
 * @param type 能力接口类型，注册与获取时都用它做类型校验
 * @param <T>  能力接口类型
 */
public record CapabilityKey<T>(String id, Class<T> type) {
	public CapabilityKey {
		if (id == null || id.isBlank()) {
			throw new IllegalArgumentException("能力 id 不得为空");
		}
		Objects.requireNonNull(type, "type");
	}

	public static <T> CapabilityKey<T> of(String id, Class<T> type) {
		return new CapabilityKey<>(id, type);
	}
}
