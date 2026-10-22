package mcmod.yfcorelib.api.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleConfigManagerTest {
	@TempDir
	Path dir;

	private FileConfigSource source() {
		// @TempDir 在实例创建后才注入，必须在测试方法内构造
		return new FileConfigSource(dir);
	}

	private ConfigSpec spec() {
		return ConfigBuilder.create("test")
				.define("world.enabled", Boolean.class, true)
				.define("world.temperature", Double.class, 1.0)
				.define("player.name", String.class, "steve")
				.define("player.level", Integer.class, 1)
				.define("player.exp", Long.class, 0L)
				.version(2)
				.build();
	}

	@Test
	void 首次加载补齐全部默认键并写回() {
		SimpleConfigManager manager = new SimpleConfigManager(spec(), source());
		assertTrue(manager.reload());

		assertEquals(true, manager.get(key("world.enabled", Boolean.class)));
		assertEquals(1.0, manager.get(key("world.temperature", Double.class)));
		// 写回的文件包含版本键与全部默认值
		assertTrue(content("test.json").contains("\"__version\""));
		assertTrue(content("test.json").contains("temperature"));
	}

	@Test
	void 用户已改值不被覆盖() {
		write("test.json", """
				{
				  "__version": 2,
				  "world": { "enabled": false, "temperature": 9.5 }
				}
				""");
		SimpleConfigManager manager = new SimpleConfigManager(spec(), source());
		manager.reload();

		assertEquals(false, manager.get(key("world.enabled", Boolean.class)));
		assertEquals(9.5, manager.get(key("world.temperature", Double.class)));
	}

	@Test
	void 缺失键自动补齐且未知键保留() {
		write("test.json", """
				{
				  "__version": 2,
				  "world": { "enabled": false },
				  "legacy": { "oldKey": "keepMe" }
				}
				""");
		SimpleConfigManager manager = new SimpleConfigManager(spec(), source());
		manager.reload();

		// 已有值保留
		assertEquals(false, manager.get(key("world.enabled", Boolean.class)));
		// 缺失键补默认
		assertEquals("steve", manager.get(key("player.name", String.class)));
		// 未知键保留
		assertTrue(content("test.json").contains("keepMe"));
	}

	@Test
	void 类型错误整体替换为默认值() {
		write("test.json", """
				{
				  "__version": 2,
				  "player": { "level": "not_a_number", "exp": true }
				}
				""");
		SimpleConfigManager manager = new SimpleConfigManager(spec(), source());
		assertTrue(manager.reload(), "类型错误不应导致加载失败");

		assertEquals(1, manager.get(key("player.level", Integer.class)));
		assertEquals(0L, manager.get(key("player.exp", Long.class)));
		// 文件已被修正
		assertFalse(content("test.json").contains("not_a_number"));
	}

	@Test
	void 损坏JSON视为空配置不崩溃() {
		write("test.json", "{ this is not valid json !!!");
		SimpleConfigManager manager = new SimpleConfigManager(spec(), source());
		assertTrue(manager.reload());
		assertEquals(true, manager.get(key("world.enabled", Boolean.class)));
	}

	@Test
	void 迁移只执行一次且版本回写() {
		write("test.json", """
				{
				  "__version": 0,
				  "old": { "value": 42 }
				}
				""");
		AtomicInteger runs = new AtomicInteger();
		ConfigMigrator v0To1 = new ConfigMigrator() {
			@Override
			public int fromVersion() {
				return 0;
			}

			@Override
			public int toVersion() {
				return 1;
			}

			@Override
			public void migrate(ConfigNode node) {
				runs.incrementAndGet();
				node.setString("migrated.flag", "yes");
			}
		};
		ConfigMigrator v1To2 = new ConfigMigrator() {
			@Override
			public int fromVersion() {
				return 1;
			}

			@Override
			public int toVersion() {
				return 2;
			}

			@Override
			public void migrate(ConfigNode node) {
				runs.incrementAndGet();
			}
		};
		SimpleConfigManager manager = new SimpleConfigManager(spec(), source(), v0To1, v1To2);

		assertTrue(manager.reload());
		assertEquals(2, runs.get(), "两个迁移器各执行一次");
		assertEquals(2, manager.loadedVersion());
		assertEquals("yes", manager.get(key("migrated.flag", String.class)), "迁移结果落盘");
		assertTrue(content("test.json").contains("\"__version\": 2"));

		// 第二次 reload 不再执行迁移
		assertTrue(manager.reload());
		assertEquals(2, runs.get(), "迁移不得重复执行");
	}

	@Test
	void set写入内存树并随save落盘() {
		SimpleConfigManager manager = new SimpleConfigManager(spec(), source());
		manager.reload();
		manager.set(key("player.name", String.class), "alex");
		manager.save();

		// 新实例读取同一来源应拿到修改值（模拟重启）
		SimpleConfigManager second = new SimpleConfigManager(spec(), source());
		second.reload();
		assertEquals("alex", second.get(key("player.name", String.class)));
	}

	@Test
	void 缺失迁移器时中止并保留现有数据() {
		write("test.json", """
				{
				  "__version": 1,
				  "world": { "enabled": false }
				}
				""");
		// 只有 0 -> 1 的迁移器，无法处理 1 -> 2
		SimpleConfigManager manager = new SimpleConfigManager(spec(), source(), new ConfigMigrator() {
			@Override
			public int fromVersion() {
				return 0;
			}

			@Override
			public int toVersion() {
				return 1;
			}

			@Override
			public void migrate(ConfigNode node) {
			}
		});
		assertTrue(manager.reload());
		assertEquals(false, manager.get(key("world.enabled", Boolean.class)), "迁移中止不丢数据");
	}

	@Test
	void spec键路径重复时立即抛错() {
		ConfigBuilder builder = ConfigBuilder.create("dup")
				.define("a.b", Integer.class, 1)
				.define("a.b", Integer.class, 2);
		org.junit.jupiter.api.Assertions.assertThrows(
				IllegalArgumentException.class, builder::build);
	}

	@Test
	void 键定义顺序稳定() {
		List<ConfigKey<?>> keys = spec().keys();
		assertEquals("world.enabled", keys.get(0).path());
		assertEquals("player.exp", keys.get(keys.size() - 1).path());
	}

	private static <T> ConfigKey<T> key(String path, Class<T> type) {
		// 测试辅助：直接构造与 spec 中同路径同类型的键（ConfigKey 相等性基于 path+type）
		return ConfigKey.of(path, type, defaultValue(type), null);
	}

	@SuppressWarnings("unchecked")
	private static <T> T defaultValue(Class<T> type) {
		return (T) switch (type.getSimpleName()) {
			case "String" -> "";
			case "Boolean" -> Boolean.FALSE;
			case "Integer" -> 0;
			case "Long" -> 0L;
			default -> 0.0;
		};
	}

	private void write(String name, String content) {
		try {
			Files.writeString(dir.resolve(name), content);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private String content(String name) {
		try {
			return Files.readString(dir.resolve(name));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
