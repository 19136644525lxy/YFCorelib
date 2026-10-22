package mcmod.yfcorelib.core;

import mcmod.yfcorelib.api.Ids;

/** 共享实现示例：Fabric 与 Forge 复用同一份代码。 */
public final class CoreInfo {
	private CoreInfo() {
	}

	public static String banner() {
		return "YFCoreLib spike core=" + Ids.id("core");
	}
}
