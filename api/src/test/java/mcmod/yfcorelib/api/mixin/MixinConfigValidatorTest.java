package mcmod.yfcorelib.api.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** MixinConfigValidator：只报告"配置写的和实际有没有对上" */
class MixinConfigValidatorTest {
	private static final String PACKAGE = "mcmod.yfcorelib.api.mixin";

	private static MixinConfig config(List<String> mixins, List<String> client, List<String> server,
			String refmap, String plugin) {
		return new MixinConfig("test.mixins.json", PACKAGE, mixins, client, server, refmap, plugin,
				List.of(), null);
	}

	@Test
	void cleanConfigShouldHaveNoProblems() {
		List<MixinConfigValidator.Problem> problems = MixinConfigValidator.validate(
				config(List.of("FakeMixin"), List.of(), List.of(), null, null),
				getClass().getClassLoader());
		assertTrue(problems.isEmpty(), problems.toString());
	}

	@Test
	void missingMixinClassShouldBeReported() {
		List<MixinConfigValidator.Problem> problems = MixinConfigValidator.validate(
				config(List.of("GhostMixin"), List.of(), List.of(), null, null),
				getClass().getClassLoader());
		assertEquals(1, problems.size(), problems.toString());
		assertTrue(problems.get(0).message().contains("不存在"), problems.get(0).message());
		assertTrue(problems.get(0).target().contains("GhostMixin"), problems.get(0).toString());
	}

	@Test
	void missingRefmapShouldBeReported() {
		List<MixinConfigValidator.Problem> problems = MixinConfigValidator.validate(
				config(List.of("FakeMixin"), List.of(), List.of(), "not-there.refmap.json", null),
				getClass().getClassLoader(),
				path -> false);
		assertEquals(1, problems.size(), problems.toString());
		assertTrue(problems.get(0).message().contains("refmap"), problems.get(0).message());
	}

	@Test
	void presentRefmapShouldPass() {
		assertTrue(MixinConfigValidator.validate(
				config(List.of("FakeMixin"), List.of(), List.of(), "yfcorelib.refmap.json", null),
				getClass().getClassLoader(),
				path -> true).isEmpty());
	}

	@Test
	void missingPluginClassShouldBeReported() {
		List<MixinConfigValidator.Problem> problems = MixinConfigValidator.validate(
				config(List.of("FakeMixin"), List.of(), List.of(), null, "no.such.Plugin"),
				getClass().getClassLoader());
		assertEquals(1, problems.size(), problems.toString());
		assertTrue(problems.get(0).message().contains("插件"), problems.get(0).message());
	}

	@Test
	void duplicateEntryShouldBeReported() {
		List<MixinConfigValidator.Problem> problems = MixinConfigValidator.validate(
				config(List.of("FakeMixin"), List.of("FakeMixin"), List.of(), null, null),
				getClass().getClassLoader());
		assertEquals(1, problems.size(), problems.toString());
		assertTrue(problems.get(0).message().contains("多次"), problems.get(0).message());
	}

	@Test
	void emptyConfigShouldBeReported() {
		List<MixinConfigValidator.Problem> problems = MixinConfigValidator.validate(
				config(List.of(), List.of(), List.of(), null, null),
				getClass().getClassLoader());
		assertEquals(1, problems.size(), problems.toString());
		assertTrue(problems.get(0).message().contains("没有声明"), problems.get(0).message());
	}

	@Test
	void absentResourceShouldBeReported() {
		List<MixinConfigValidator.Problem> problems =
				MixinConfigValidator.validateLoadedConfig("absolutely-absent.mixins.json");
		assertEquals(1, problems.size(), problems.toString());
		assertTrue(problems.get(0).message().contains("类路径"), problems.get(0).message());
	}

	@Test
	void parseFailureShouldBeReportedNotThrown() {
		// 校验器的职责是"收集全部问题"，因此解析失败也要变成 Problem 而不是异常
		List<MixinConfigValidator.Problem> problems =
				MixinConfigValidator.validateLoadedConfig("absolutely-absent.mixins.json");
		assertFalse(problems.isEmpty());
		assertThrows(IllegalArgumentException.class,
				() -> MixinConfigParser.parse("bad.mixins.json", "{}"));
	}
}
