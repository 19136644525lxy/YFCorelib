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

	private static <T> T roundTrip(DataCodec<T> codec, T value) {
		return codec.decode(JsonParser.parseString(codec.encode(value).toString()));
	}
}
