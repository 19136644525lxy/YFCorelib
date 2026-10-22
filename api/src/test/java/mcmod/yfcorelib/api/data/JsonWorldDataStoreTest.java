package mcmod.yfcorelib.api.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonWorldDataStoreTest {
	@TempDir
	Path dir;

	private Path file() {
		return dir.resolve("world.json");
	}

	@Test
	void 写入后新实例可读_保持插入顺序() {
		JsonWorldDataStore<String> store = new JsonWorldDataStore<>(file(), DataCodecs.STRING);
		store.set("b", "two");
		store.set("a", "one");
		store.save();

		JsonWorldDataStore<String> second = new JsonWorldDataStore<>(file(), DataCodecs.STRING);
		assertEquals("two", second.get("b").orElseThrow());
		assertEquals("one", second.get("a").orElseThrow());
	}

	@Test
	void 缺失键返回empty() {
		JsonWorldDataStore<String> store = new JsonWorldDataStore<>(file(), DataCodecs.STRING);
		assertTrue(store.get("missing").isEmpty());
	}

	@Test
	void remove删除键值() {
		JsonWorldDataStore<String> store = new JsonWorldDataStore<>(file(), DataCodecs.STRING);
		store.set("k", "v");
		store.remove("k");

		assertTrue(store.get("k").isEmpty());
	}

	@Test
	void 主文件损坏从bak回退() throws Exception {
		// 两轮写入后才存在 .bak（首轮 save 时主文件不存在，不产生备份）
		JsonWorldDataStore<String> first = new JsonWorldDataStore<>(file(), DataCodecs.STRING);
		first.set("k", "v1");
		first.save();
		first.set("k", "v2");
		first.save();

		// 模拟写入中断：主文件被截断损坏，.bak 保留上一成功版本 v1
		Files.writeString(file(), "{ broken");
		assertTrue(Files.exists(dir.resolve("world.json.bak")));

		JsonWorldDataStore<String> second = new JsonWorldDataStore<>(file(), DataCodecs.STRING);
		assertEquals("v1", second.get("k").orElseThrow(), "应从 .bak 回退");
	}

	@Test
	void 主文件与bak都损坏时空表启动() throws Exception {
		Files.writeString(file(), "{ broken");
		Files.writeString(dir.resolve("world.json.bak"), "{ also broken");

		JsonWorldDataStore<String> store = new JsonWorldDataStore<>(file(), DataCodecs.STRING);
		assertTrue(store.get("k").isEmpty());
		assertFalse(store.isDirty());
	}

	@Test
	void save覆盖后bak保存上一版本() throws Exception {
		JsonWorldDataStore<String> store = new JsonWorldDataStore<>(file(), DataCodecs.STRING);
		store.set("k", "v1");
		store.save();
		store.set("k", "v2");
		store.save();

		// 主文件是 v2，bak 是 v1
		assertEquals("v2", new JsonWorldDataStore<>(file(), DataCodecs.STRING).get("k").orElseThrow());
		assertTrue(Files.readString(dir.resolve("world.json.bak")).contains("v1"));
	}

	@Test
	void 单条损坏其余可读() throws Exception {
		Files.writeString(file(), """
				{
				  "good": "ok",
				  "bad": {"unexpected": "structure"}
				}
				""");
		JsonWorldDataStore<String> store = new JsonWorldDataStore<>(file(), DataCodecs.STRING);
		assertEquals("ok", store.get("good").orElseThrow());
		assertTrue(store.get("bad").isEmpty(), "损坏条目跳过");
	}
}
