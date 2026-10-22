package mcmod.yfcorelib.api.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Gson JsonObject 实现的配置节点：点分路径寻址，中间节点自动创建。
 * <p>类型判定宽松规则：数字家族互相兼容（按目标类型窄化），其余严格匹配。</p>
 */
public final class JsonConfigNode implements ConfigNode {
	private final JsonObject raw;

	public JsonConfigNode(JsonObject raw) {
		this.raw = raw;
	}

	public JsonObject raw() {
		return raw;
	}

	@Override
	public boolean has(String path) {
		return resolve(path).isPresent();
	}

	@Override
	public Optional<String> getString(String path) {
		return read(path).filter(JsonElement::isJsonPrimitive).map(e -> e.getAsString());
	}

	/** 读取数值；类型不符返回 empty（不抛异常，由调用方决定兜底策略） */
	public Optional<Double> getNumber(String path) {
		return read(path).filter(JsonElement::isJsonPrimitive)
				.filter(e -> e.getAsJsonPrimitive().isNumber())
				.map(JsonElement::getAsDouble);
	}

	public Optional<Boolean> getBoolean(String path) {
		return read(path).filter(JsonElement::isJsonPrimitive)
				.filter(e -> e.getAsJsonPrimitive().isBoolean())
				.map(JsonElement::getAsBoolean);
	}

	@Override
	public void setString(String path, String value) {
		set(path, new JsonPrimitive(value));
	}

	public void setNumber(String path, Number value) {
		set(path, new JsonPrimitive(value));
	}

	public void setBoolean(String path, boolean value) {
		set(path, new JsonPrimitive(value));
	}

	/** 写入任意 JSON 值；中间节点非对象时整体替换为对象 */
	public void set(String path, JsonElement value) {
		JsonObject current = raw;
		String[] segments = split(path);
		for (int i = 0; i < segments.length - 1; i++) {
			JsonElement next = current.get(segments[i]);
			if (next == null || !next.isJsonObject()) {
				JsonObject created = new JsonObject();
				current.add(segments[i], created);
				current = created;
			} else {
				current = next.getAsJsonObject();
			}
		}
		current.add(segments[segments.length - 1], value);
	}

	/** 移除指定路径的值（含父级空对象不清理，保留结构） */
	public void remove(String path) {
		String[] segments = split(path);
		JsonObject current = raw;
		for (int i = 0; i < segments.length - 1; i++) {
			JsonElement next = current.get(segments[i]);
			if (next == null || !next.isJsonObject()) {
				return;
			}
			current = next.getAsJsonObject();
		}
		current.remove(segments[segments.length - 1]);
	}

	@Override
	public Set<String> keys() {
		SortedSet<String> out = new TreeSet<>();
		raw.keySet().forEach(out::add);
		return out;
	}

	@Override
	public ConfigNode child(String path) {
		JsonObject current = raw;
		for (String segment : split(path)) {
			JsonElement next = current.get(segment);
			if (next == null || !next.isJsonObject()) {
				JsonObject created = new JsonObject();
				current.add(segment, created);
				current = created;
			} else {
				current = next.getAsJsonObject();
			}
		}
		return new JsonConfigNode(current);
	}

	/** 读取原始 JSON 元素 */
	public Optional<JsonElement> read(String path) {
		return resolve(path);
	}

	private Optional<JsonElement> resolve(String path) {
		String[] segments = split(path);
		JsonObject current = raw;
		for (int i = 0; i < segments.length - 1; i++) {
			JsonElement next = current.get(segments[i]);
			if (next == null || !next.isJsonObject()) {
				return Optional.empty();
			}
			current = next.getAsJsonObject();
		}
		return Optional.ofNullable(current.get(segments[segments.length - 1]));
	}

	private static String[] split(String path) {
		return path.split("\\.");
	}
}
