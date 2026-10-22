package mcmod.yfcorelib.core;

import mcmod.yfcorelib.api.Ids;

/** 共享横幅：两端通过同一份代码输出，用于验证 common 复用。 */
public final class CoreInfo {
	private CoreInfo() {
	}

	public static String banner() {
		return "YFCoreLib core=" + Ids.id("core");
	}
}
