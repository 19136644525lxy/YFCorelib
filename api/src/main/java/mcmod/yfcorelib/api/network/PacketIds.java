package mcmod.yfcorelib.api.network;

import mcmod.yfcorelib.api.registry.RegistryPaths;

/** 包/通道 id 校验：必须为 "命名空间:路径" 形式，命名空间与路径各自还要过字符规则 */
final class PacketIds {
	private PacketIds() {
	}

	static void requireValid(String id) {
		if (id == null) {
			throw new IllegalArgumentException("网络 id 不能为 null");
		}
		int colon = id.indexOf(':');
		// 冒号前后都必须有内容，且只能有一个冒号：否则 "a:b:c" 会被平台层当成别的 id
		if (colon <= 0 || colon == id.length() - 1 || id.indexOf(':', colon + 1) >= 0) {
			throw new IllegalArgumentException("网络 id 必须为 \"命名空间:路径\" 形式，实际: " + id);
		}
		RegistryPaths.requireNamespace(id.substring(0, colon));
		RegistryPaths.requirePath(id.substring(colon + 1));
	}
}
