package mcmod.yfcorelib.api;

import java.util.Objects;

/** 纯 Java 契约层示例：不得引用 Minecraft 或加载器类型。 */
public final class Ids {
	public static final String MOD_ID = "yfcorelib";

	private Ids() {
	}

	/**
	 * 拼接本模组的完整 id。
	 *
	 * @throws NullPointerException path 为 null（改用 {@link Objects#requireNonNull} 后报错点更近）
	 */
	public static String id(String path) {
		return MOD_ID + ":" + Objects.requireNonNull(path, "path");
	}
}
