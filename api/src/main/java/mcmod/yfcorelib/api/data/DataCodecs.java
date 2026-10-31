package mcmod.yfcorelib.api.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;

/**
 * 基础标量编解码器集合。
 * <p>解码是<b>严格</b>的：类型不符或数值不能精确表示为目标类型时抛 {@link IllegalArgumentException}，
 * 符合 {@link DataCodec} "解码失败抛异常"的契约。这里不做静默强转——把 {@code "1"} 当 true、
 * 把 {@code 3.9} 截成 3 这类宽松处理只会把数据错误推迟到更难排查的地方。</p>
 */
public final class DataCodecs {
	public static final DataCodec<String> STRING = of(
			v -> new JsonPrimitive(v),
			e -> requireString(requirePrimitive(e)));

	public static final DataCodec<Integer> INTEGER = of(
			v -> new JsonPrimitive(v),
			e -> requireIntExact(requirePrimitive(e)));

	public static final DataCodec<Long> LONG = of(
			v -> new JsonPrimitive(v),
			e -> requireLongExact(requirePrimitive(e)));

	public static final DataCodec<Double> DOUBLE = of(
			v -> new JsonPrimitive(v),
			e -> requireNumber(requirePrimitive(e)).doubleValue());

	public static final DataCodec<Boolean> BOOLEAN = of(
			v -> new JsonPrimitive(v),
			e -> requireBoolean(requirePrimitive(e)));

	/** 透传编解码：存储原始 JSON 结构，适合由业务自行解析的场景 */
	public static final DataCodec<JsonElement> RAW = of(
			JsonElement::deepCopy,
			JsonElement::deepCopy);

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

	private static String requireString(JsonPrimitive primitive) {
		if (!primitive.isString()) {
			throw new IllegalArgumentException("期望字符串，实际: " + primitive);
		}
		return primitive.getAsString();
	}

	private static BigDecimal requireNumber(JsonPrimitive primitive) {
		if (!primitive.isNumber()) {
			throw new IllegalArgumentException("期望数值，实际: " + primitive);
		}
		return primitive.getAsBigDecimal();
	}

	private static int requireIntExact(JsonPrimitive primitive) {
		try {
			return requireNumber(primitive).intValueExact();
		} catch (ArithmeticException e) {
			// 3.9 或超出 int 范围的数值：截断会静默改数据
			throw new IllegalArgumentException("期望 int 精度整数，实际: " + primitive);
		}
	}

	private static long requireLongExact(JsonPrimitive primitive) {
		try {
			return requireNumber(primitive).longValueExact();
		} catch (ArithmeticException e) {
			throw new IllegalArgumentException("期望 long 精度整数，实际: " + primitive);
		}
	}

	private static boolean requireBoolean(JsonPrimitive primitive) {
		if (!primitive.isBoolean()) {
			throw new IllegalArgumentException("期望布尔，实际: " + primitive);
		}
		return primitive.getAsBoolean();
	}
}
