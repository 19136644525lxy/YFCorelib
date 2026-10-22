package mcmod.yfcorelib.api.platform;

import java.nio.file.Path;
import java.util.Optional;

/**
 * 平台环境抽象：屏蔽 Fabric/Forge 差异。
 * <p>业务代码只依赖本接口，加载器 API 仅允许出现在平台模块的实现里。</p>
 */
public interface Platform {
	/** 加载器标识：fabric / forge / neoforge */
	String loaderName();

	/** Minecraft 版本 */
	String mcVersion();

	/** 加载器版本 */
	String loaderVersion();

	boolean isClient();

	boolean isDevelopment();

	Path gameDir();

	Path configDir();

	Path modsDir();

	/** 是否加载了指定模组（软依赖探测） */
	boolean isModLoaded(String modId);

	/** 查询指定模组的基础信息 */
	Optional<ModInfo> modInfo(String modId);
}
