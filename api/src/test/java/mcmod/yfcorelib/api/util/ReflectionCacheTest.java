package mcmod.yfcorelib.api.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

/** ReflectionCache：继承链查找、候选名提示、异常包装、缓存复用 */
class ReflectionCacheTest {
	private static class Parent {
		private int hidden = 7;
	}

	private static class Child extends Parent {
		private String label = "kid";
		private static final String CONSTANT = "const";

		private int add(int a, int b) {
			return a + b;
		}

		private String concat(String prefix) {
			return prefix + label;
		}

		private int divide(int a, int b) {
			return a / b;
		}
	}

	@Test
	void readShouldWalkSuperclassChain() {
		Child child = new Child();
		assertEquals(7, ReflectionCache.read(child, "hidden"));
		assertEquals("kid", ReflectionCache.read(child, "label"));
	}

	@Test
	void writeShouldUpdateField() {
		Child child = new Child();
		ReflectionCache.write(child, "label", "changed");
		assertEquals("changed", child.label);
	}

	@Test
	void invokeShouldSupportArgumentsAndNulls() {
		Child child = new Child();
		assertEquals(5, ReflectionCache.invoke(child, "add", 2, 3));
		assertEquals("nullkid", ReflectionCache.invoke(child, "concat", (Object) null));
		assertEquals("x-kid", ReflectionCache.invoke(child, "concat", "x-"));
	}

	@Test
	void wrappedInvocationFailureShouldCarryCause() {
		Child child = new Child();
		IllegalStateException ex = assertThrows(IllegalStateException.class,
				() -> ReflectionCache.invoke(child, "divide", 1, 0));
		assertTrue(ex.getCause() instanceof ArithmeticException,
				"原始异常应作为 cause 保留，实际: " + ex.getCause());
	}

	@Test
	void missingMemberShouldListCandidates() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> ReflectionCache.field(Child.class, "walkSpeed"));
		String message = ex.getMessage();
		assertTrue(message.contains("walkSpeed"), message);
		assertTrue(message.contains("hidden") && message.contains("label"),
				"异常消息应列出候选字段名: " + message);
	}

	@Test
	void obfuscatedNameShouldBeCalledOut() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> ReflectionCache.field(Child.class, "field_1472"));
		assertTrue(ex.getMessage().contains("混淆名"), ex.getMessage());
	}

	@Test
	void wrongArityShouldFail() {
		Child child = new Child();
		assertThrows(IllegalArgumentException.class, () -> ReflectionCache.invoke(child, "add", 1));
	}

	@Test
	void lookupsShouldBeCached() {
		Field first = ReflectionCache.field(Child.class, "label");
		Field second = ReflectionCache.field(Child.class, "label");
		assertSame(first, second, "同一成员应复用缓存的 Field 实例");

		Method method = ReflectionCache.method(Child.class, "add", int.class, int.class);
		assertSame(method, ReflectionCache.method(Child.class, "add", int.class, int.class));
	}

	@Test
	void findShouldReturnEmptyInsteadOfThrowing() {
		assertFalse(ReflectionCache.findField(Child.class, "nope").isPresent());
		assertFalse(ReflectionCache.findMethod(Child.class, "nope", String.class).isPresent());
		assertTrue(ReflectionCache.findField(Child.class, "hidden").isPresent());
	}

	@Test
	void staticFinalFieldShouldBeReadable() throws Exception {
		Field constant = ReflectionCache.field(Child.class, "CONSTANT");
		assertEquals("const", constant.get(null));
	}

	@Test
	void clearShouldKeepCacheUsable() {
		ReflectionCache.field(Child.class, "label");
		ReflectionCache.clear();
		assertEquals("kid", ReflectionCache.read(new Child(), "label"));
	}
}
