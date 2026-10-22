package mcmod.yfcorelib.forge;

import mcmod.yfcorelib.api.platform.Platform;
import mcmod.yfcorelib.api.platform.PlatformProvider;

/** 向 ServiceLoader 暴露 Forge 平台实现。 */
public final class ForgePlatformProvider implements PlatformProvider {
	@Override
	public Platform platform() {
		return new ForgePlatform();
	}
}
