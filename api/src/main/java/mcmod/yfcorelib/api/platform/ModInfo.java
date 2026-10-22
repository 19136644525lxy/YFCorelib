package mcmod.yfcorelib.api.platform;

import java.util.Optional;

/** 已加载模组的基础信息。 */
public record ModInfo(String modId, String version, String name, Optional<String> description) {
	public ModInfo {
		description = description == null ? Optional.empty() : description;
	}
}
