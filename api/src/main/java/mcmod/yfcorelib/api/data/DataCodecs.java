package mcmod.yfcorelib.api.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** 基础标量编解码器集合 */
public final class DataCodecs {
	public static final DataCodec<String> STRING = of(
			v -> new JsonPrimitive(v),
			e -> requirePrimitive(e).getAsString());

	public static final DataCodec<Integer> INTEGER = of(
			v -> new JsonPrimitive(v),
			e -> requirePrimitive(e).getAsInt());

	public static final DataCodec<Long> LONG = of(
			v -> new JsonPrimitive(v),
			e -> requirePrimitive(e).getAsLong());

	public static final DataCodec<Double> DOUBLE = of(
			v -> new JsonPrimitive(v),
			e -> requirePrimitive(e).getAsDouble());

	public static final DataCodec<Boolean> BOOLEAN = of(
			v -> new JsonPrimitive(v),
			e -> requirePrimitive(e).getAsBoolean());

	/** 透传编解码：存储原始 JSON 结构，适合由业务自行解析的场景 */
	public static final DataCodec<JsonElement> RAW = of(
			e -> e.deepCopy(),
			e -> e.deepCopy());

	private DataCodecs() {
	}

	private static <T> DataCodec<T> of(java.util.function.Function<T, JsonElement> encoder,
			java.util.function.Function<JsonElement, T> decoder) {
		return new DataCodec<>() {
			@Override
			public JsonElement encode(T value) {
				return encoder.apply(value);
			}

			@Override
			public T decode(JsonElement json) {
				return decoder.apply(json);
			}
		};
	}

	private static JsonPrimitive requirePrimitive(JsonElement element) {
		if (element == null || !element.isJsonPrimitive()) {
			throw new IllegalArgumentException("期望 JSON 标量，实际: " + (element == null ? "null" : element));
		}
		return element.getAsJsonPrimitive();
	}
}
