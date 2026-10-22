package mcmod.yfcorelib.api.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import mcmod.yfcorelib.api.Side;
import mcmod.yfcorelib.api.data.DataCodec;

/** SimpleChannelRegistry：id 校验、冲突检测与查找 */
class SimpleChannelRegistryTest {
	private static final DataCodec<TestPacket> CODEC = new DataCodec<>() {
		@Override
		public JsonElement encode(TestPacket value) {
			JsonObject json = new JsonObject();
			json.addProperty("msg", value.msg());
			return json;
		}

		@Override
		public TestPacket decode(JsonElement json) {
			return new TestPacket(json.getAsJsonObject().get("msg").getAsString());
		}
	};

	record TestPacket(String msg) implements Packet {
		@Override
		public String typeId() {
			return "test:hello";
		}
	}

	private static PacketType<TestPacket> type(String id) {
		return new PacketType<>(id, Side.SERVER, TestPacket.class, CODEC, (p, c) -> {
		});
	}

	@Test
	void validIdShouldRegister() {
		SimpleChannelRegistry registry = new SimpleChannelRegistry();
		PacketType<TestPacket> type = type("test:hello");
		registry.register(type);
		assertEquals(1, registry.size());
		assertTrue(registry.isRegistered(type));
		assertEquals(type, registry.require("test:hello"));
	}

	@Test
	void idWithoutNamespaceShouldThrow() {
		assertThrows(IllegalArgumentException.class, () -> type("hello"));
		assertThrows(IllegalArgumentException.class, () -> type(":hello"));
		assertThrows(IllegalArgumentException.class, () -> type("hello:"));
		assertThrows(IllegalArgumentException.class, () -> type(null));
	}

	@Test
	void duplicateIdShouldThrow() {
		SimpleChannelRegistry registry = new SimpleChannelRegistry();
		registry.register(type("test:hello"));
		assertThrows(IllegalStateException.class, () -> registry.register(type("test:hello")));
	}

	@Test
	void unregisteredIdShouldFailFast() {
		SimpleChannelRegistry registry = new SimpleChannelRegistry();
		assertThrows(IllegalStateException.class, () -> registry.require("test:missing"));
		assertFalse(registry.isRegistered(type("test:hello")));
	}

	@Test
	void packetTypeShouldRequireAllParts() {
		assertThrows(NullPointerException.class, () -> new PacketType<>("test:a", null, TestPacket.class, CODEC, (p, c) -> {
		}));
		assertThrows(NullPointerException.class, () -> new PacketType<>("test:a", Side.SERVER, null, CODEC, (p, c) -> {
		}));
		assertThrows(NullPointerException.class, () -> new PacketType<>("test:a", Side.SERVER, TestPacket.class, null, (p, c) -> {
		}));
		assertThrows(NullPointerException.class, () -> new PacketType<>("test:a", Side.SERVER, TestPacket.class, CODEC, null));
	}
}
