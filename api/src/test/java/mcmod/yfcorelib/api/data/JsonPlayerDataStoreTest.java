package mcmod.yfcorelib.api.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonPlayerDataStoreTest {
	@TempDir
	Path dir;

	@Test
	void 写入后新实例可读_模拟重启() {
		UUID player = UUID.randomUUID();
		JsonPlayerDataStore<String> store = new JsonPlayerDataStore<>(dir, DataCodecs.STRING);
		store.set(player, "saved-value");
		store.save(player);

		JsonPlayerDataStore<String> second = new JsonPlayerDataStore<>(dir, DataCodecs.STRING);
		assertEquals("saved-value", second.get(player), "重启后（新实例）应能读回");
	}

	@Test
	void 无数据返回null() {
		JsonPlayerDataStore<String> store = new JsonPlayerDataStore<>(dir, DataCodecs.STRING);
		assertEquals(null, store.get(UUID.randomUUID()));
	}

	@Test
	void getOrDefault兜底() {
		JsonPlayerDataStore<String> store = new JsonPlayerDataStore<>(dir, DataCodecs.STRING);
		assertEquals("fallback", store.getOrDefault(UUID.randomUUID(), "fallback"));
	}

	@Test
	void 损坏条目跳过并返回null不崩溃() throws Exception {
		UUID player = UUID.randomUUID();
		Files.writeString(dir.resolve(player + ".json"), "{ corrupt !!!");

		JsonPlayerDataStore<String> store = new JsonPlayerDataStore<>(dir, DataCodecs.STRING);
		assertEquals(null, store.get(player));
	}

	@Test
	void 类型不符条目跳过() throws Exception {
		UUID player = UUID.randomUUID();
		Files.writeString(dir.resolve(player + ".json"), "{\"a\": 1}");

		JsonPlayerDataStore<String> store = new JsonPlayerDataStore<>(dir, DataCodecs.STRING);
		assertEquals(null, store.get(player));
	}

	@Test
	void remove删除内存与磁盘() {
		UUID player = UUID.randomUUID();
		JsonPlayerDataStore<String> store = new JsonPlayerDataStore<>(dir, DataCodecs.STRING);
		store.set(player, "v");
		store.save(player);
		store.remove(player);

		assertEquals(null, store.get(player));
		assertFalse(Files.exists(dir.resolve(player + ".json")));
	}

	@Test
	void unload释放缓存且先保存() {
		UUID player = UUID.randomUUID();
		JsonPlayerDataStore<String> store = new JsonPlayerDataStore<>(dir, DataCodecs.STRING);
		store.set(player, "offline-data");
		assertTrue(store.cachedCount() == 1);

		store.unload(player);
		assertEquals(0, store.cachedCount(), "离线后缓存应释放");

		// 数据已落盘，新实例可读
		JsonPlayerDataStore<String> second = new JsonPlayerDataStore<>(dir, DataCodecs.STRING);
		assertEquals("offline-data", second.get(player));
	}

	@Test
	void saveAll单条失败不影响整体() throws Exception {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		// 目标路径是目录 → 写入必然失败，模拟单条损坏
		Files.createDirectories(dir.resolve(a + ".json"));
		JsonPlayerDataStore<String> store = new JsonPlayerDataStore<>(dir, DataCodecs.STRING);
		store.set(a, "fail");
		store.set(b, "ok");
		store.saveAll();

		// b 正常落盘，未因 a 失败而中断
		assertEquals("ok", new JsonPlayerDataStore<>(dir, DataCodecs.STRING).get(b));
	}
}
