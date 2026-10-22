package mcmod.yfcorelib.api;

/** 纯 Java 契约层示例：不得引用 Minecraft 或加载器类型。 */
public final class Ids {
	public static final String MOD_ID = "yfcorelib";

	private Ids() {
	}

	public static String id(String path) {
		return MOD_ID + ":" + path;
	}
}
