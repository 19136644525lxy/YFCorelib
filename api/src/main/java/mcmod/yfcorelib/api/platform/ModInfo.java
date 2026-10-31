package mcmod.yfcorelib.api.platform;

import java.util.Objects;
import java.util.Optional;

/** 已加载模组的基础信息。 */
public record ModInfo(String modId, String version, String name, Optional<String> description) {
	public ModInfo {
		// 平台实现常从加载器对象里逐字段拷出来，缺字段是常态；在这里一次性归一，
		// 免得下游每个使用点都要判 null
		modId = Objects.requireNonNull(modId, "modId");
		version = version == null ? "" : version;
		name = name == null || name.isBlank() ? modId : name;
		description = description == null ? Optional.empty() : description;
	}
}
