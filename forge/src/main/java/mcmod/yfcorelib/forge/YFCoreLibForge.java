package mcmod.yfcorelib.forge;

import mcmod.yfcorelib.core.CoreInfo;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("yfcorelib")
public final class YFCoreLibForge {
	private static final Logger LOG = LoggerFactory.getLogger("yfcorelib");

	public YFCoreLibForge() {
		LOG.info("{} (forge)", CoreInfo.banner());
	}
}
