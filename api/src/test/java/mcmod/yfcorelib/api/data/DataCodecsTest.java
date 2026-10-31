package mcmod.yfcorelib.api.data;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataCodecsTest {
	@Test
	void 标量往返() {
		assertEquals("hi", roundTrip(DataCodecs.STRING, "hi"));
		assertEquals(42, roundTrip(DataCodecs.INTEGER, 42));
		assertEquals(42L, roundTrip(DataCodecs.LONG, 42L));
		assertEquals(1.5, roundTrip(DataCodecs.DOUBLE, 1.5));
		assertEquals(true, roundTrip(DataCodecs.BOOLEAN, true));
	}

	@Test
	void raw透传保持结构() {
		com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
		obj.addProperty("a", 1);
		assertEquals(obj, roundTrip(DataCodecs.RAW, obj));
	}

	@Test
	void 非标量输入抛异常() {
		assertThrows(IllegalArgumentException.class,
				() -> DataCodecs.STRING.decode(new com.google.gson.JsonObject()));
		assertThrows(IllegalArgumentException.class,
				() -> DataCodecs.INTEGER.decode(new JsonPrimitive("text")));
	}

	@Test
	void 类型不符必须抛异常而不是静默强转() {
		// 旧实现会把 "1" 当 false、把 3.9 截成 3，调用方拿到的是错值而不是错误
		assertThrows(IllegalArgumentException.class,
				() -> DataCodecs.BOOLEAN.decode(new JsonPrimitive("1")));
		assertThrows(IllegalArgumentException.class,
				() -> DataCodecs.BOOLEAN.decode(new JsonPrimitive(1)));
		assertThrows(IllegalArgumentException.class,
				() -> DataCodecs.INTEGER.decode(new JsonPrimitive(3.9)));
		assertThrows(IllegalArgumentException.class,
				() -> DataCodecs.INTEGER.decode(new JsonPrimitive(true)));
		assertThrows(IllegalArgumentException.class,
				() -> DataCodecs.LONG.decode(new JsonPrimitive(1.5)));
		assertThrows(IllegalArgumentException.class,
				() -> DataCodecs.DOUBLE.decode(new JsonPrimitive("1.5")));
		assertThrows(IllegalArgumentException.class,
				() -> DataCodecs.STRING.decode(new JsonPrimitive(1)));
	}

	private static <T> T roundTrip(DataCodec<T> codec, T value) {
		return codec.decode(JsonParser.parseString(codec.encode(value).toString()));
	}
}
