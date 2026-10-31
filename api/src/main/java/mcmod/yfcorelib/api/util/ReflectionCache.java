package mcmod.yfcorelib.api.util;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 反射查找缓存：把"类 → 成员"的查找结果缓存下来，避免每次调用都走一遍继承链。
 * <p>查找失败时抛出**带候选名单**的异常，而不是裸的 {@link NoSuchFieldException}：
 * 成员名拼错（例如把 {@code walkingSpeed} 写成 {@code walkSpeed}）是最常见的反射 bug，
 * 裸异常只告诉你"找不到"，看不出来是拼写问题。</p>
 * <p>会提示混淆名：如果传入的名字形如 {@code field_1472} / {@code f_12345_}，
 * 说明调用方拿到了 SRG/中间映射名，应当改用 official mapping 名。</p>
 * <p>仅用于确有必要的场合（第三方模组可选 API、缺少编译期依赖的软兼容）。
 * 自己项目的代码优先走正常调用，反射的成本和脆弱性都不值得。</p>
 */
public final class ReflectionCache {
	private static final Map<FieldKey, Field> FIELDS = new ConcurrentHashMap<>();
	private static final Map<MethodKey, Method> METHODS = new ConcurrentHashMap<>();

	private record FieldKey(Class<?> owner, String name) {
	}

	private record MethodKey(Class<?> owner, String name, List<Class<?>> parameterTypes) {
	}

	private ReflectionCache() {
	}

	/**
	 * 查找字段（含父类与接口常量）。
	 *
	 * @throws IllegalArgumentException 找不到字段；异常消息里带该类的候选字段名
	 * @throws NullPointerException     owner 或 name 为 null
	 */
	public static Field field(Class<?> owner, String name) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(name, "name");
		FieldKey key = new FieldKey(owner, name);
		Field cached = FIELDS.get(key);
		if (cached != null) {
			return cached;
		}
		Field found = findFieldInHierarchy(owner, name);
		if (found == null) {
			throw new IllegalArgumentException(notFound("字段", owner, name, fieldCandidates(owner)));
		}
		FIELDS.putIfAbsent(key, found);
		return found;
	}

	/** 查找字段，找不到返回空 */
	public static Optional<Field> findField(Class<?> owner, String name) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(name, "name");
		return Optional.ofNullable(FIELDS.computeIfAbsent(new FieldKey(owner, name),
				k -> findFieldInHierarchy(k.owner(), k.name())));
	}

	/**
	 * 按精确参数类型查找方法（含父类）。
	 *
	 * @throws IllegalArgumentException 找不到方法；异常消息里带候选方法名
	 */
	public static Method method(Class<?> owner, String name, Class<?>... parameterTypes) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(parameterTypes, "parameterTypes");
		MethodKey key = new MethodKey(owner, name, List.of(parameterTypes));
		Method cached = METHODS.get(key);
		if (cached != null) {
			return cached;
		}
		Method found = findMethodInHierarchy(owner, name, key.parameterTypes());
		if (found == null) {
			throw new IllegalArgumentException(notFound("方法", owner, name, methodCandidates(owner)));
		}
		METHODS.putIfAbsent(key, found);
		return found;
	}

	/** 按精确参数类型查找方法，找不到返回空 */
	public static Optional<Method> findMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(parameterTypes, "parameterTypes");
		MethodKey key = new MethodKey(owner, name, List.of(parameterTypes));
		return Optional.ofNullable(METHODS.computeIfAbsent(key,
				k -> findMethodInHierarchy(k.owner(), k.name(), k.parameterTypes())));
	}

	/**
	 * 读取实例字段。静态字段请用 {@link #field(Class, String)} 直接取 {@link Field}。
	 *
	 * @throws IllegalStateException 字段不可访问或读取失败
	 */
	public static Object read(Object target, String name) {
		Objects.requireNonNull(target, "target");
		Field field = field(target.getClass(), name);
		try {
			return field.get(target);
		} catch (IllegalAccessException ex) {
			throw new IllegalStateException("读取字段失败: " + target.getClass().getName() + "#" + name, ex);
		}
	}

	/**
	 * 写入实例字段。
	 *
	 * @throws IllegalStateException 字段不可访问或类型不匹配
	 */
	public static void write(Object target, String name, Object value) {
		Objects.requireNonNull(target, "target");
		Field field = field(target.getClass(), name);
		try {
			field.set(target, value);
		} catch (IllegalAccessException ex) {
			throw new IllegalStateException("写入字段失败: " + target.getClass().getName() + "#" + name, ex);
		}
	}

	/**
	 * 按名字与实参个数调用方法：在继承链里找第一个"参数个数相同且实参可赋值"的声明。
	 *
	 * @throws IllegalArgumentException 找不到匹配的方法
	 * @throws IllegalStateException    调用本身抛异常（原异常放在 cause 里）
	 */
	public static Object invoke(Object target, String name, Object... args) {
		Objects.requireNonNull(target, "target");
		Objects.requireNonNull(name, "name");
		Class<?>[] argTypes = new Class<?>[args.length];
		for (int i = 0; i < args.length; i++) {
			// null 实参无法推断类型，用 Object 兜底（只影响重载选择，不影响 null 传递）
			argTypes[i] = args[i] == null ? Object.class : args[i].getClass();
		}
		Method method = findCompatible(target.getClass(), name, args);
		if (method == null) {
			throw new IllegalArgumentException(notFound("方法", target.getClass(), name, methodCandidates(target.getClass()))
					+ "；实参类型: " + describe(argTypes));
		}
		try {
			return method.invoke(target, args);
		} catch (InvocationTargetException ex) {
			Throwable cause = ex.getCause();
			throw new IllegalStateException("调用方法时抛出异常: "
					+ target.getClass().getName() + "#" + name, cause == null ? ex : cause);
		} catch (IllegalAccessException ex) {
			throw new IllegalStateException("调用方法失败: " + target.getClass().getName() + "#" + name, ex);
		}
	}

	/** 清空缓存：热重载/单测隔离用 */
	public static void clear() {
		FIELDS.clear();
		METHODS.clear();
	}

	private static Field findFieldInHierarchy(Class<?> owner, String name) {
		for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
			try {
				Field field = type.getDeclaredField(name);
				return makeAccessible(field);
			} catch (NoSuchFieldException ignored) {
				// 继续往父类找
			}
		}
		return null;
	}

	private static Method findMethodInHierarchy(Class<?> owner, String name, List<Class<?>> parameterTypes) {
		Class<?>[] types = parameterTypes.toArray(new Class<?>[0]);
		for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
			try {
				return makeAccessible(type.getDeclaredMethod(name, types));
			} catch (NoSuchMethodException ignored) {
				// 继续往父类找
			}
		}
		return null;
	}

	private static Method findCompatible(Class<?> owner, String name, Object[] args) {
		for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
			for (Method method : type.getDeclaredMethods()) {
				if (!method.getName().equals(name) || method.getParameterCount() != args.length) {
					continue;
				}
				if (isCompatible(method.getParameterTypes(), args)) {
					return makeAccessible(method);
				}
			}
		}
		return null;
	}

	private static boolean isCompatible(Class<?>[] parameterTypes, Object[] args) {
		for (int i = 0; i < parameterTypes.length; i++) {
			// null 实参不参与类型匹配：它只可能是引用类型，交给具体方法自己判空
			if (args[i] == null) {
				continue;
			}
			if (!wrap(parameterTypes[i]).isAssignableFrom(args[i].getClass())) {
				return false;
			}
		}
		return true;
	}

	/** 基本类型参数要装箱后才能和实参类型比较 */
	private static Class<?> wrap(Class<?> type) {
		if (!type.isPrimitive()) {
			return type;
		}
		if (type == int.class) {
			return Integer.class;
		}
		if (type == long.class) {
			return Long.class;
		}
		if (type == double.class) {
			return Double.class;
		}
		if (type == float.class) {
			return Float.class;
		}
		if (type == boolean.class) {
			return Boolean.class;
		}
		if (type == byte.class) {
			return Byte.class;
		}
		if (type == short.class) {
			return Short.class;
		}
		if (type == char.class) {
			return Character.class;
		}
		return Void.class;
	}

	private static Field makeAccessible(Field field) {
		// 一律放开：父类里的 protected/private 字段是最常见的反射目标
		field.setAccessible(true);
		return field;
	}

	private static Method makeAccessible(Method method) {
		method.setAccessible(true);
		return method;
	}

	private static String notFound(String kind, Class<?> owner, String name, List<String> candidates) {
		StringBuilder message = new StringBuilder()
				.append("在 ").append(owner.getName()).append(" 上找不到").append(kind)
				.append(" '").append(name).append("'");
		if (looksObfuscated(name)) {
			message.append("；该名字形似混淆名（SRG/中间映射），应改用 official mapping 下的名字");
		}
		if (!candidates.isEmpty()) {
			message.append("。可用候选: ").append(String.join(", ", candidates));
		}
		return message.toString();
	}

	private static boolean looksObfuscated(String name) {
		return name.matches("(f|m|field|method)_\\d+_?");
	}

	private static List<String> fieldCandidates(Class<?> owner) {
		Set<String> names = new LinkedHashSet<>();
		for (Class<?> type = owner; type != null && names.size() < 20; type = type.getSuperclass()) {
			for (Field field : type.getDeclaredFields()) {
				if (!field.isSynthetic()) {
					names.add(field.getName());
				}
			}
		}
		return names.isEmpty() ? Collections.emptyList() : new ArrayList<>(names);
	}

	private static List<String> methodCandidates(Class<?> owner) {
		Set<String> names = new LinkedHashSet<>();
		for (Class<?> type = owner; type != null && names.size() < 20; type = type.getSuperclass()) {
			for (Method method : type.getDeclaredMethods()) {
				if (!method.isSynthetic()) {
					names.add(method.getName());
				}
			}
		}
		return names.isEmpty() ? Collections.emptyList() : new ArrayList<>(names);
	}

	private static String describe(Class<?>[] types) {
		List<String> names = new ArrayList<>(types.length);
		for (Class<?> type : types) {
			names.add(type.getSimpleName());
		}
		return names.toString();
	}
}
