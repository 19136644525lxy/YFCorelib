package mcmod.yfcorelib.fabric;

import java.nio.file.Path;
import java.util.Optional;

import mcmod.yfcorelib.api.platform.ModInfo;
import mcmod.yfcorelib.api.platform.Platform;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

/** Fabric 平台实现：加载器 API 仅出现在本模块。 */
public final class FabricPlatform implements Platform {

	@Override
	public String loaderName() {
		return "fabric";
	}

	@Override
	public String mcVersion() {
		return containerVersion("minecraft");
	}

	@Override
	public String loaderVersion() {
		return containerVersion("fabricloader");
	}

	@Override
	public boolean isClient() {
		return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
	}

	@Override
	public boolean isDevelopment() {
		return FabricLoader.getInstance().isDevelopmentEnvironment();
	}

	@Override
	public Path gameDir() {
		return FabricLoader.getInstance().getGameDir();
	}

	@Override
	public Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	public Path modsDir() {
		return FabricLoader.getInstance().getGameDir().resolve("mods");
	}

	@Override
	public boolean isModLoaded(String modId) {
		return FabricLoader.getInstance().isModLoaded(modId);
	}

	@Override
	public Optional<ModInfo> modInfo(String modId) {
		return FabricLoader.getInstance().getModContainer(modId).map(c -> new ModInfo(
				modId,
				c.getMetadata().getVersion().getFriendlyString(),
				c.getMetadata().getName(),
				Optional.ofNullable(c.getMetadata().getDescription())));
	}

	/** minecraft / fabricloader 本身也是模组容器，直接取其版本号 */
	private static String containerVersion(String modId) {
		return FabricLoader.getInstance().getModContainer(modId)
				.map(c -> c.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");
	}
}
