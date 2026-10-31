package mcmod.yfcorelib.api.config;

import java.util.Objects;
import java.util.Optional;

/**
 * 配置键定义：路径、类型、默认值与注释。
 * <p>仅支持 {@code String/Boolean/Integer/Long/Double} 五种标量类型，JSON 后端可直接映射。</p>
 */
public final class ConfigKey<T> {
	private final String path;
	private final Class<T> type;
	private final T defaultValue;
	private final String comment;

	private ConfigKey(String path, Class<T> type, T defaultValue, String comment) {
		this.path = path;
		this.type = type;
		this.defaultValue = defaultValue;
		this.comment = comment;
	}

	/**
	 * 定义一个配置键（无注释）。
	 * <p>推荐把键声明成 {@code public static final} 常量，再用
	 * {@link ConfigBuilder#define(ConfigKey)} 装进 spec；这样业务代码拿到的就是带类型的键，
	 * 不需要按路径反查。</p>
	 *
	 * @throws IllegalArgumentException 路径为空/含空段/使用保留键 {@code __version}、
	 *                                  类型不受支持、默认值类型与声明不符
	 */
	public static <T> ConfigKey<T> of(String path, Class<T> type, T defaultValue) {
		return of(path, type, defaultValue, null);
	}

	/** 定义一个配置键（带注释，注释会写进生成的配置文件） */
	public static <T> ConfigKey<T> of(String path, Class<T> type, T defaultValue, String comment) {
		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(type, "type");
		if (path.isEmpty()) {
			throw new IllegalArgumentException("配置路径不能为空");
		}
		if (path.equals(SimpleConfigManager.VERSION_KEY)) {
			// 这个键由管理器自己维护（文件版本号），业务定义同名键会把版本信息覆盖掉
			throw new IllegalArgumentException("配置路径 " + SimpleConfigManager.VERSION_KEY + " 为保留键");
		}
		for (String segment : path.split("\\.", -1)) {
			if (segment.isEmpty()) {
				throw new IllegalArgumentException("配置路径存在空段: '" + path + "'");
			}
		}
		if (!isSupported(type)) {
			throw new IllegalArgumentException("不支持的配置类型: " + type.getName());
		}
		if (defaultValue != null && !type.isInstance(defaultValue)) {
			// 声明 Integer 却给了 "abc" 这类错误会在写入默认值时静默变成类型不符，
			// 直到下次 reload 才以"类型不符已替换"的告警形式暴露，太晚
			throw new IllegalArgumentException("默认值类型与声明不符: " + path
					+ " 声明 " + type.getSimpleName() + "，实际 " + defaultValue.getClass().getSimpleName());
		}
		return new ConfigKey<>(path, type, defaultValue, comment);
	}

	private static boolean isSupported(Class<?> type) {
		return type == String.class || type == Boolean.class
				|| type == Integer.class || type == Long.class || type == Double.class;
	}

	/** 点分路径，如 "world.temperature.enabled" */
	public String path() {
		return path;
	}

	public Class<T> type() {
		return type;
	}

	public T defaultValue() {
		return defaultValue;
	}

	public Optional<String> comment() {
		return Optional.ofNullable(comment);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ConfigKey<?> other)) {
			return false;
		}
		return path.equals(other.path) && type == other.type;
	}

	@Override
	public int hashCode() {
		return Objects.hash(path, type);
	}

	@Override
	public String toString() {
		return path + ":" + type.getSimpleName() + "=" + defaultValue;
	}
}
