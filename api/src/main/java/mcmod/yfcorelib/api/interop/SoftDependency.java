package mcmod.yfcorelib.api.interop;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import mcmod.yfcorelib.api.YFCoreLib;
import mcmod.yfcorelib.api.platform.ModInfo;

/**
 * 软依赖探测：判断别的模组在不在，从而决定要不要启用某段可选功能。
 * <p>错误用法是 {@code @Mod} 里直接引用对方类——那叫硬依赖，对方不在就崩。
 * 正确用法是先用本类判断，再通过反射或独立适配类访问对方 API。</p>
 *
 * <pre>{@code
 * if (SoftDependency.isLoaded("curios")) {
 *     // 只有确认存在时才触碰它的类
 * }
 * }</pre>
 *
 * <p>{@code allLoaded}/{@code anyLoaded} 还提供了接受 {@code Predicate} 的重载，
 * 便于把判定逻辑从平台实现里剥出来单测。</p>
 */
public final class SoftDependency {
	private SoftDependency() {
	}

	/**
	 * 指定模组是否已加载。
	 *
	 * @throws IllegalArgumentException modId 为 null 或空白
	 */
	public static boolean isLoaded(String modId) {
		requireId(modId);
		return YFCoreLib.platform().isModLoaded(modId);
	}

	/** 全部加载才为 true；空数组返回 true */
	public static boolean allLoaded(String... modIds) {
		return allLoaded(YFCoreLib.platform()::isModLoaded, modIds);
	}

	/** 任意一个加载即 true；空数组返回 false */
	public static boolean anyLoaded(String... modIds) {
		return anyLoaded(YFCoreLib.platform()::isModLoaded, modIds);
	}

	/** 可注入探测器的版本，用于单测与"临时屏蔽某模组"的排查场景 */
	public static boolean allLoaded(Predicate<String> probe, String... modIds) {
		Objects.requireNonNull(probe, "probe");
		Objects.requireNonNull(modIds, "modIds");
		for (String modId : modIds) {
			if (!probe.test(requireId(modId))) {
				return false;
			}
		}
		return true;
	}

	/** 可注入探测器的版本 */
	public static boolean anyLoaded(Predicate<String> probe, String... modIds) {
		Objects.requireNonNull(probe, "probe");
		Objects.requireNonNull(modIds, "modIds");
		for (String modId : modIds) {
			if (probe.test(requireId(modId))) {
				return true;
			}
		}
		return false;
	}

	/** 已加载模组的基本信息；不在则为空 */
	public static Optional<ModInfo> info(String modId) {
		requireId(modId);
		return YFCoreLib.platform().modInfo(modId);
	}

	private static String requireId(String modId) {
		if (modId == null || modId.isBlank()) {
			throw new IllegalArgumentException("modId 不得为空");
		}
		return modId;
	}
}
