package mcmod.yfcorelib.forge;

import java.nio.file.Path;
import java.util.Optional;

import mcmod.yfcorelib.api.platform.ModInfo;
import mcmod.yfcorelib.api.platform.Platform;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

/** Forge 平台实现：加载器 API 仅出现在本模块。 */
public final class ForgePlatform implements Platform {

	@Override
	public String loaderName() {
		return "forge";
	}

	@Override
	public String mcVersion() {
		return containerVersion("minecraft");
	}

	@Override
	public String loaderVersion() {
		return containerVersion("forge");
	}

	@Override
	public boolean isClient() {
		return FMLEnvironment.dist == Dist.CLIENT;
	}

	@Override
	public boolean isDevelopment() {
		return !FMLEnvironment.production;
	}

	@Override
	public Path gameDir() {
		return FMLPaths.GAMEDIR.get();
	}

	@Override
	public Path configDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public Path modsDir() {
		return FMLPaths.GAMEDIR.get().resolve("mods");
	}

	@Override
	public boolean isModLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}

	@Override
	public Optional<ModInfo> modInfo(String modId) {
		return ModList.get().getModContainerById(modId).map(c -> new ModInfo(
				modId,
				c.getModInfo().getVersion().toString(),
				c.getModInfo().getDisplayName(),
				Optional.ofNullable(c.getModInfo().getDescription())));
	}

	/** minecraft / forge 本身也是模组容器，直接取其版本号 */
	private static String containerVersion(String modId) {
		return ModList.get().getModContainerById(modId)
				.map(c -> c.getModInfo().getVersion().toString())
				.orElse("unknown");
	}
}
