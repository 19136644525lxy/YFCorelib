package mcmod.yfcorelib.fabric;

import mcmod.yfcorelib.api.platform.Platform;
import mcmod.yfcorelib.api.platform.PlatformProvider;

/** 向 ServiceLoader 暴露 Fabric 平台实现。 */
public final class FabricPlatformProvider implements PlatformProvider {
	@Override
	public Platform platform() {
		return new FabricPlatform();
	}
}
