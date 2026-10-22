package mcmod.yfcorelib.forge;

import mcmod.yfcorelib.api.platform.Platform;
import mcmod.yfcorelib.api.platform.Services;
import mcmod.yfcorelib.common.core.TimerPersistence;
import mcmod.yfcorelib.common.demo.DemoContent;
import mcmod.yfcorelib.core.CoreInfo;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("yfcorelib")
public final class YFCoreLibForge {
	private static final Logger LOG = LoggerFactory.getLogger("yfcorelib");

	public YFCoreLibForge() {
		Platform platform = Services.platform();
		LOG.info("{} | loader={} mc={} dev={}", CoreInfo.banner(),
				platform.loaderName(), platform.mcVersion(), platform.isDevelopment());
		ForgeLifecycleBridge.register(MinecraftForge.EVENT_BUS);
		TimerPersistence.register();
		DemoContent.register();
	}
}
