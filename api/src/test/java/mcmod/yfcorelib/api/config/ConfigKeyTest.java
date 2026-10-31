package mcmod.yfcorelib.api.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** ConfigKey：路径与默认值在声明期就校验，避免错误拖到 reload 才暴露 */
class ConfigKeyTest {
	@Test
	void legalKeyShouldKeepFields() {
		ConfigKey<Integer> key = ConfigKey.of("world.temperature.max", Integer.class, 40, "上限");
		assertEquals("world.temperature.max", key.path());
		assertEquals(Integer.class, key.type());
		assertEquals(40, key.defaultValue());
		assertTrue(key.comment().isPresent());
	}

	@Test
	void emptyPathOrSegmentShouldThrow() {
		assertThrows(IllegalArgumentException.class,
				() -> ConfigKey.of("", String.class, "x", null));
		assertThrows(IllegalArgumentException.class,
				() -> ConfigKey.of("a..b", String.class, "x", null));
		assertThrows(IllegalArgumentException.class,
				() -> ConfigKey.of(".a", String.class, "x", null));
		assertThrows(IllegalArgumentException.class,
				() -> ConfigKey.of("a.", String.class, "x", null));
	}

	@Test
	@SuppressWarnings({"unchecked", "rawtypes"})
	void defaultTypeMismatchShouldThrowAtDeclaration() {
		// 旧实现允许这种组合：声明 Integer 却给 "abc"，直到 reload 才以告警形式出现
		assertThrows(IllegalArgumentException.class,
				() -> ConfigKey.of("a.b", (Class) Integer.class, "abc", null));
	}

	@Test
	@SuppressWarnings({"unchecked", "rawtypes"})
	void unsupportedTypeShouldThrow() {
		assertThrows(IllegalArgumentException.class,
				() -> ConfigKey.of("a.b", (Class) java.util.List.class, null, null));
	}

	@Test
	void equalityShouldIgnoreDefaultAndComment() {
		ConfigKey<String> a = ConfigKey.of("a.b", String.class, "x", "c1");
		ConfigKey<String> b = ConfigKey.of("a.b", String.class, "y", "c2");
		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
	}

	@Test
	void publicFactoryShouldBeUsableWithoutComment() {
		// 外部模组只能看到 public 成员：这里模拟"声明常量 + 装进 spec"的推荐写法
		ConfigKey<Integer> max = ConfigKey.of("world.temperature.max", Integer.class, 40);
		assertEquals(40, max.defaultValue());
		assertTrue(max.comment().isEmpty());
	}

	@Test
	void builderShouldAcceptPredefinedKey() {
		ConfigKey<Integer> max = ConfigKey.of("world.temperature.max", Integer.class, 40, "上限");
		ConfigSpec spec = ConfigBuilder.create("example")
				.define(max)
				.build();
		assertEquals(1, spec.keys().size());
		assertEquals(max, spec.keys().get(0));
		assertTrue(spec.keys().get(0).comment().isPresent());
	}
}
