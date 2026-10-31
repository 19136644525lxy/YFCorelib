package mcmod.yfcorelib.api.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** ConfigSpec 的不可变实现 */
public final class SimpleConfigSpec implements ConfigSpec {
	private final String name;
	private final int version;
	private final List<ConfigKey<?>> keys;
	private final Map<String, ConfigKey<?>> byPath;

	SimpleConfigSpec(String name, int version, List<ConfigKey<?>> keys) {
		this.name = name;
		this.version = version;
		this.keys = List.copyOf(keys);
		Map<String, ConfigKey<?>> index = new HashMap<>();
		for (ConfigKey<?> key : this.keys) {
			ConfigKey<?> prev = index.put(key.path(), key);
			if (prev != null) {
				throw new IllegalArgumentException("配置键路径重复: " + key.path());
			}
		}
		this.byPath = Map.copyOf(index);
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public int version() {
		return version;
	}

	@Override
	public List<ConfigKey<?>> keys() {
		return keys;
	}

	/** 按路径查找键定义 */
	public ConfigKey<?> key(String path) {
		return byPath.get(path);
	}

	static Builder builder(String name) {
		return new Builder(name);
	}

	/** 仅供 ConfigBuilder 使用的装配器 */
	static final class Builder {
		private final String name;
		private int version;
		private final List<ConfigKey<?>> keys = new ArrayList<>();
		private final Map<String, String> comments = new HashMap<>();

		Builder(String name) {
			this.name = name;
		}

		<T> Builder define(String path, Class<T> type, T defaultValue) {
			keys.add(ConfigKey.of(path, type, defaultValue, null));
			return this;
		}

		<T> Builder define(ConfigKey<T> key) {
			keys.add(key);
			return this;
		}

		Builder comment(String path, String comment) {
			comments.put(path, comment);
			return this;
		}

		Builder version(int version) {
			this.version = version;
			return this;
		}

		SimpleConfigSpec build() {
			List<ConfigKey<?>> withComments = new ArrayList<>(keys.size());
			Set<String> defined = new HashSet<>();
			for (ConfigKey<?> key : keys) {
				defined.add(key.path());
				String comment = comments.get(key.path());
				if (comment == null) {
					withComments.add(key);
				} else {
					withComments.add(rebuildWithComment(key, comment));
				}
			}
			// 注释挂在拼错的路径上会被静默丢弃，写配置时才发现"注释没生效"
			for (String path : comments.keySet()) {
				if (!defined.contains(path)) {
					throw new IllegalArgumentException("注释指向未定义的配置键: " + path);
				}
			}
			if (version < 0) {
				throw new IllegalArgumentException("配置版本不能为负: " + version);
			}
			return new SimpleConfigSpec(name, version, withComments);
		}

		@SuppressWarnings("unchecked")
		private static <T> ConfigKey<T> rebuildWithComment(ConfigKey<T> key, String comment) {
			return ConfigKey.of(key.path(), key.type(), key.defaultValue(), comment);
		}
	}
}
