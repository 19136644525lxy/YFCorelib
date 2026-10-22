package mcmod.yfcorelib.api.config;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonConfigNodeTest {
	@Test
	void 点分路径自动创建中间节点() {
		JsonConfigNode node = new JsonConfigNode(new JsonObject());
		node.setNumber("a.b.c", 7);

		assertTrue(node.has("a.b.c"));
		assertEquals(7.0, node.getNumber("a.b.c").orElseThrow());
	}

	@Test
	void 类型不符读取返回空() {
		JsonConfigNode node = new JsonConfigNode(new JsonObject());
		node.setString("k", "text");

		assertTrue(node.getString("k").isPresent());
		assertTrue(node.getNumber("k").isEmpty(), "字符串不是数字");
		assertTrue(node.getBoolean("k").isEmpty(), "字符串不是布尔");
	}

	@Test
	void 缺失路径返回空且不创建结构() {
		JsonConfigNode node = new JsonConfigNode(new JsonObject());

		assertTrue(node.getString("x.y.z").isEmpty());
		assertFalse(node.raw().has("x"));
	}

	@Test
	void child与keys行为() {
		JsonConfigNode node = new JsonConfigNode(new JsonObject());
		node.child("parent").setString("leaf", "v");

		assertEquals("v", node.getString("parent.leaf").orElseThrow());
		assertTrue(node.keys().contains("parent"));
	}

	@Test
	void remove清除指定路径() {
		JsonConfigNode node = new JsonConfigNode(new JsonObject());
		node.setString("a.b", "v");
		node.remove("a.b");

		assertFalse(node.has("a.b"));
	}
}
