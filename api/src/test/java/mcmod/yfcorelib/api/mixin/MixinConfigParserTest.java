package mcmod.yfcorelib.api.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** MixinConfigParser：合法/非法配置的解析行为 */
class MixinConfigParserTest {
	@Test
	void shouldParseFullConfig() {
		String json = """
				{
				  "required": true,
				  "package": "mymod.mixin",
				  "compatibilityLevel": "JAVA_17",
				  "refmap": "mymod.refmap.json",
				  "plugin": "mymod.mixin.MyPlugin",
				  "requiredMods": ["fabric-api"],
				  "mixins": ["CommonMixin", "OtherMixin"],
				  "client": ["ClientMixin"],
				  "server": ["ServerMixin"]
				}
				""";
		MixinConfig config = MixinConfigParser.parse("mymod.mixins.json", json);
		assertEquals("mymod.mixins.json", config.name());
		assertEquals("mymod.mixin", config.packageName());
		assertEquals(List.of("CommonMixin", "OtherMixin"), config.mixins());
		assertEquals(List.of("ClientMixin"), config.client());
		assertEquals(List.of("ServerMixin"), config.server());
		assertEquals("mymod.refmap.json", config.refmap());
		assertEquals("mymod.mixin.MyPlugin", config.plugin());
		assertEquals(List.of("fabric-api"), config.requiredMods());
		assertEquals("JAVA_17", config.compatibilityLevel());
		assertEquals(4, config.allEntries().size());
		assertEquals("mymod.mixin.ClientMixin", config.qualifiedName("ClientMixin"));
	}

	@Test
	void shouldDefaultMissingFieldsToEmpty() {
		String json = """
				{"package": "mymod.mixin"}
				""";
		MixinConfig config = MixinConfigParser.parse("mymod.mixins.json", json);
		assertTrue(config.mixins().isEmpty());
		assertTrue(config.client().isEmpty());
		assertTrue(config.server().isEmpty());
		assertTrue(config.requiredMods().isEmpty());
		assertNull(config.refmap());
		assertNull(config.plugin());
		assertNull(config.compatibilityLevel());
	}

	@Test
	void missingPackageShouldThrow() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> MixinConfigParser.parse("x.mixins.json", "{\"mixins\": [\"A\"]}"));
		assertTrue(ex.getMessage().contains("package"), ex.getMessage());
	}

	@Test
	void blankPackageShouldThrow() {
		assertThrows(IllegalArgumentException.class,
				() -> MixinConfigParser.parse("x.mixins.json", "{\"package\": \"  \"}"));
	}

	@Test
	void malformedJsonShouldThrow() {
		assertThrows(IllegalArgumentException.class,
				() -> MixinConfigParser.parse("x.mixins.json", "{oops"));
	}

	@Test
	void nonObjectRootShouldThrow() {
		assertThrows(IllegalArgumentException.class,
				() -> MixinConfigParser.parse("x.mixins.json", "[]"));
	}

	@Test
	void wrongFieldTypesShouldThrow() {
		assertThrows(IllegalArgumentException.class,
				() -> MixinConfigParser.parse("x.mixins.json", "{\"package\": \"a\", \"mixins\": \"A\"}"));
		assertThrows(IllegalArgumentException.class,
				() -> MixinConfigParser.parse("x.mixins.json", "{\"package\": \"a\", \"mixins\": [1]}"));
		assertThrows(IllegalArgumentException.class,
				() -> MixinConfigParser.parse("x.mixins.json", "{\"package\": 7}"));
	}

	@Test
	void loadConfigShouldReturnEmptyWhenMissing() {
		assertFalse(MixinConfigValidator.loadConfig("totally-absent.mixins.json",
				MixinConfigParserTest.class.getClassLoader()).isPresent());
	}
}
