package mcmod.yfcorelib.api.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 配置合并内核。行为约定（源自 PdopnConfig 已验证逻辑）：
 * <ul>
 *   <li>以文件实际内容为基础补默认键，绝不覆盖用户已改值</li>
 *   <li>未知键原样保留</li>
 *   <li>类型不符的键整体替换为默认值并记录 warn，不导致启动失败</li>
 *   <li>版本号驱动迁移，执行完回写新版本号，保证只执行一次</li>
 * </ul>
 */
public final class SimpleConfigManager implements ConfigManager {
	private static final Logger LOG = LoggerFactory.getLogger(SimpleConfigManager.class);
	/** 版本保留键：业务键不得使用该路径 */
	public static final String VERSION_KEY = "__version";

	private final ConfigSpec spec;
	private final ConfigSource source;
	private final ConfigMigrator[] migrators;
	private final Object lock = new Object();

	private JsonObject data;
	private int loadedVersion;

	public SimpleConfigManager(ConfigSpec spec, ConfigSource source, ConfigMigrator... migrators) {
		this.spec = spec;
		this.source = source;
		this.migrators = migrators;
	}

	@Override
	public <T> T get(ConfigKey<T> key) {
		synchronized (lock) {
			requireLoaded();
			JsonElement element = node().read(key.path()).orElse(null);
			if (element == null || !isPrimitive(element)) {
				return key.defaultValue();
			}
			try {
				return narrow(element, key.type());
			} catch (RuntimeException e) {
				LOG.warn("配置键读取失败，回退默认值: {} ({})", key.path(), e.toString());
				return key.defaultValue();
			}
		}
	}

	@Override
	public <T> void set(ConfigKey<T> key, T value) {
		synchronized (lock) {
			requireLoaded();
			node().set(key.path(), toJson(value));
		}
	}

	@Override
	public boolean reload() {
		synchronized (lock) {
			try {
				return doReload();
			} catch (RuntimeException e) {
				LOG.error("配置加载失败: {}", spec.name(), e);
				if (data == null) {
					data = new JsonObject();
					loadedVersion = spec.version();
				}
				return false;
			}
		}
	}

	@Override
	public void save() {
		synchronized (lock) {
			requireLoaded();
			source.write(spec.name(), GsonFormats.pretty(data));
		}
	}

	@Override
	public int loadedVersion() {
		synchronized (lock) {
			return loadedVersion;
		}
	}

	private boolean doReload() {
		String content = source.read(spec.name()).orElse(null);
		boolean fileExists = content != null;
		JsonObject file = parse(content);
		int fileVersion = readVersion(file, fileExists);

		boolean changed = mergeDefaults(file);
		ensureVersionKey(file, fileVersion);
		fileVersion = runMigrations(file, fileVersion);

		data = file;
		loadedVersion = fileVersion;
		if (changed) {
			source.write(spec.name(), GsonFormats.pretty(data));
		}
		return true;
	}

	/** 补默认键 + 类型不符整体替换；未知键天然保留（始终在文件对象上操作） */
	private boolean mergeDefaults(JsonObject file) {
		boolean changed = false;
		for (ConfigKey<?> key : spec.keys()) {
			JsonElement existing = node(file).read(key.path()).orElse(null);
			if (existing == null) {
				node(file).set(key.path(), toJson(key.defaultValue()));
				changed = true;
			} else if (typeMismatch(existing, key.type())) {
				LOG.warn("配置键类型不符，已替换为默认值: {} 期望 {}，实际 {}",
						key.path(), key.type().getSimpleName(), existing);
				node(file).set(key.path(), toJson(key.defaultValue()));
				changed = true;
			}
		}
		return changed;
	}

	private int readVersion(JsonObject file, boolean fileExists) {
		JsonElement version = file.get(VERSION_KEY);
		if (version != null && isPrimitive(version) && version.getAsJsonPrimitive().isNumber()) {
			return version.getAsInt();
		}
		if (fileExists) {
			// 旧格式文件缺少版本键：从 0 开始迁移
			LOG.info("配置缺少版本键，按版本 0 处理: {}", spec.name());
		}
		return fileExists ? 0 : spec.version();
	}

	private void ensureVersionKey(JsonObject file, int version) {
		node(file).setNumber(VERSION_KEY, version);
	}

	/** 逐级执行迁移链；找不到匹配迁移器时中止并警告 */
	private int runMigrations(JsonObject file, int fromVersion) {
		int version = fromVersion;
		while (version < spec.version()) {
			ConfigMigrator next = findMigrator(version);
			if (next == null) {
				LOG.warn("缺少版本 {} -> {} 的迁移器，配置迁移中止: {}", version, spec.version(), spec.name());
				break;
			}
			try {
				next.migrate(node(file));
			} catch (RuntimeException e) {
				LOG.error("迁移器执行失败: {} -> {}", next.fromVersion(), next.toVersion(), e);
				break;
			}
			version = next.toVersion();
			node(file).setNumber(VERSION_KEY, version);
		}
		if (version > spec.version()) {
			LOG.warn("配置版本 {} 高于目标版本 {}，可能来自更新版本的模组: {}", version, spec.version(), spec.name());
		}
		return version;
	}

	private ConfigMigrator findMigrator(int fromVersion) {
		for (ConfigMigrator migrator : migrators) {
			if (migrator.fromVersion() == fromVersion) {
				return migrator;
			}
		}
		return null;
	}

	private JsonObject parse(String content) {
		if (content == null || content.isBlank()) {
			return new JsonObject();
		}
		try {
			JsonElement parsed = JsonParser.parseString(content);
			if (parsed.isJsonObject()) {
				return parsed.getAsJsonObject();
			}
			LOG.warn("配置文件顶层不是对象，已视为空配置: {}", spec.name());
		} catch (RuntimeException e) {
			LOG.warn("配置文件解析失败，已视为空配置: {} ({})", spec.name(), e.toString());
		}
		return new JsonObject();
	}

	private JsonConfigNode node() {
		return node(data);
	}

	private static JsonConfigNode node(JsonObject raw) {
		return new JsonConfigNode(raw);
	}

	private void requireLoaded() {
		if (data == null) {
			throw new IllegalStateException("配置尚未加载，请先调用 reload(): " + spec.name());
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T narrow(JsonElement element, Class<T> type) {
		JsonPrimitive primitive = element.getAsJsonPrimitive();
		Object value;
		if (type == String.class) {
			value = primitive.getAsString();
		} else if (type == Boolean.class) {
			value = primitive.getAsBoolean();
		} else if (type == Integer.class) {
			value = primitive.getAsInt();
		} else if (type == Long.class) {
			value = primitive.getAsLong();
		} else {
			value = primitive.getAsDouble();
		}
		return (T) value;
	}

	private static JsonElement toJson(Object value) {
		if (value instanceof String s) {
			return new JsonPrimitive(s);
		}
		if (value instanceof Boolean b) {
			return new JsonPrimitive(b);
		}
		if (value instanceof Number n) {
			return new JsonPrimitive(n);
		}
		throw new IllegalArgumentException("不支持的配置值类型: " + value.getClass().getName());
	}

	private static boolean typeMismatch(JsonElement element, Class<?> type) {
		if (!isPrimitive(element)) {
			return true;
		}
		JsonPrimitive primitive = element.getAsJsonPrimitive();
		if (type == String.class) {
			return !primitive.isString();
		}
		if (type == Boolean.class) {
			return !primitive.isBoolean();
		}
		if (type == Integer.class || type == Long.class || type == Double.class) {
			return !primitive.isNumber();
		}
		return true;
	}

	private static boolean isPrimitive(JsonElement element) {
		return element.isJsonPrimitive();
	}
}
