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

	static <T> ConfigKey<T> of(String path, Class<T> type, T defaultValue, String comment) {
		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(type, "type");
		if (!isSupported(type)) {
			throw new IllegalArgumentException("不支持的配置类型: " + type.getName());
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
