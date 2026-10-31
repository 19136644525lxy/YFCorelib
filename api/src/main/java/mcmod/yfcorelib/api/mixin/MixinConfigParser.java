package mcmod.yfcorelib.api.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * 解析 {@code *.mixins.json}。
 * <p>只做"结构解析 + 基础校验"，不碰类加载——检查类是否真的存在是
 * {@link MixinConfigValidator} 的事，两者分开是为了让解析能被单测直接喂字符串。</p>
 */
public final class MixinConfigParser {
	private MixinConfigParser() {
	}

	/**
	 * @param configName 配置名，仅用于报错信息（例如 {@code mymod.mixins.json}）
	 * @param json       配置内容
	 * @throws IllegalArgumentException 不是合法 JSON，或缺少 {@code package}，或字段类型不对
	 */
	public static MixinConfig parse(String configName, String json) {
		Objects.requireNonNull(configName, "configName");
		Objects.requireNonNull(json, "json");
		JsonElement parsed;
		try {
			parsed = JsonParser.parseString(json);
		} catch (JsonParseException ex) {
			throw new IllegalArgumentException("Mixin 配置 " + configName + " 不是合法 JSON: " + ex.getMessage(), ex);
		}
		if (!parsed.isJsonObject()) {
			throw new IllegalArgumentException("Mixin 配置 " + configName + " 的顶层必须是 JSON 对象");
		}
		JsonObject root = parsed.getAsJsonObject();
		if (!root.has("package")) {
			// 少了 package，Mixin 会拿不到类名，启动时报的错离真正原因很远
			throw new IllegalArgumentException("Mixin 配置 " + configName + " 缺少必填字段 package");
		}
		String packageName = optionalString(root, "package", configName);
		if (packageName == null || packageName.isBlank()) {
			throw new IllegalArgumentException("Mixin 配置 " + configName + " 的 package 不得为空");
		}
		return new MixinConfig(
				configName,
				packageName,
				stringArray(root, "mixins", configName),
				stringArray(root, "client", configName),
				stringArray(root, "server", configName),
				optionalString(root, "refmap", configName),
				optionalString(root, "plugin", configName),
				stringArray(root, "requiredMods", configName),
				optionalString(root, "compatibilityLevel", configName));
	}

	private static String optionalString(JsonObject root, String key, String configName) {
		JsonElement element = root.get(key);
		if (element == null || element.isJsonNull()) {
			return null;
		}
		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			throw new IllegalArgumentException(
					"Mixin 配置 " + configName + " 的 " + key + " 必须是字符串");
		}
		return element.getAsString();
	}

	private static List<String> stringArray(JsonObject root, String key, String configName) {
		JsonElement element = root.get(key);
		if (element == null || element.isJsonNull()) {
			return List.of();
		}
		if (!element.isJsonArray()) {
			throw new IllegalArgumentException(
					"Mixin 配置 " + configName + " 的 " + key + " 必须是字符串数组");
		}
		JsonArray array = element.getAsJsonArray();
		List<String> values = new ArrayList<>(array.size());
		for (int i = 0; i < array.size(); i++) {
			JsonElement item = array.get(i);
			if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
				throw new IllegalArgumentException(
						"Mixin 配置 " + configName + " 的 " + key + "[" + i + "] 必须是字符串");
			}
			values.add(item.getAsString());
		}
		return values;
	}
}
