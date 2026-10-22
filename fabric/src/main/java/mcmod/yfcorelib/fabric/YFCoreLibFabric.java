package mcmod.yfcorelib.fabric;

import mcmod.yfcorelib.core.CoreInfo;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class YFCoreLibFabric implements ModInitializer {
	private static final Logger LOG = LoggerFactory.getLogger("yfcorelib");

	@Override
	public void onInitialize() {
		LOG.info("{} (fabric)", CoreInfo.banner());
	}
}
