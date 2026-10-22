package mcmod.yfcorelib.api.network;

/** 包/通道 id 校验：必须为 "命名空间:路径" 形式 */
final class PacketIds {
	private PacketIds() {
	}

	static void requireValid(String id) {
		if (id == null || id.indexOf(':') <= 0 || id.indexOf(':') == id.length() - 1) {
			throw new IllegalArgumentException("网络 id 必须为 \"命名空间:路径\" 形式，实际: " + id);
		}
	}
}
