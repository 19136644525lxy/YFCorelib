package mcmod.yfcorelib.api.registry;

import java.util.regex.Pattern;

/**
 * 注册 id 校验：纯字符串逻辑（不依赖 MC）。
 * <p>提前校验可以避免错误 id 拖到注册事件阶段才抛出，定位困难。</p>
 */
public final class RegistryPaths {
	private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
	private static final Pattern PATH = Pattern.compile("[a-z0-9_./-]+");

	private RegistryPaths() {
	}

	public static String requireNamespace(String namespace) {
		if (namespace == null || !NAMESPACE.matcher(namespace).matches()) {
			throw new IllegalArgumentException("非法命名空间：'" + namespace + "'，仅允许小写字母、数字、_ . -");
		}
		return namespace;
	}

	/** 注册路径不得携带命名空间，禁止大写与冒号 */
	public static String requirePath(String path) {
		if (path == null || !PATH.matcher(path).matches()) {
			throw new IllegalArgumentException("非法注册路径：'" + path + "'，仅允许小写字母、数字、_ . - /");
		}
		return path;
	}
}
