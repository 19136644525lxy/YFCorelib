package mcmod.yfcorelib.api.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicFilesTest {
	@TempDir
	Path dir;

	@Test
	void 原子写入并读取() {
		Path target = dir.resolve("a/b.json");

		assertTrue(AtomicFiles.writeAtomic(target, "content"));

		assertEquals("content", AtomicFiles.readWithFallback(target));
	}

	@Test
	void backupThenWrite保留旧版本() throws Exception {
		Path target = dir.resolve("f.json");
		AtomicFiles.writeAtomic(target, "v1");

		AtomicFiles.backupThenWrite(target, "v2");

		assertEquals("v2", Files.readString(target));
		assertEquals("v1", Files.readString(dir.resolve("f.json.bak")));
	}

	@Test
	void 主文件缺失时从bak读取() throws Exception {
		Path target = dir.resolve("f.json");
		Files.writeString(dir.resolve("f.json.bak"), "backup-content");

		assertEquals("backup-content", AtomicFiles.readWithFallback(target));
	}

	@Test
	void 两者皆缺失返回null() {
		assertEquals(null, AtomicFiles.readWithFallback(dir.resolve("none.json")));
	}

	@Test
	void 临时文件不残留() {
		Path target = dir.resolve("t.json");

		AtomicFiles.writeAtomic(target, "data");

		assertFalse(Files.exists(dir.resolve("t.json.tmp")));
	}
}
