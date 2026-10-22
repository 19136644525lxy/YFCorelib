package mcmod.yfcorelib.fabric;

import mcmod.yfcorelib.api.platform.Platform;
import mcmod.yfcorelib.api.platform.Services;
import mcmod.yfcorelib.common.core.TimerPersistence;
import mcmod.yfcorelib.core.CoreInfo;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class YFCoreLibFabric implements ModInitializer {
	private static final Logger LOG = LoggerFactory.getLogger("yfcorelib");

	@Override
	public void onInitialize() {
		Platform platform = Services.platform();
		LOG.info("{} | loader={} mc={} dev={}", CoreInfo.banner(),
				platform.loaderName(), platform.mcVersion(), platform.isDevelopment());
		FabricLifecycleBridge.register();
		TimerPersistence.register();
	}
}
