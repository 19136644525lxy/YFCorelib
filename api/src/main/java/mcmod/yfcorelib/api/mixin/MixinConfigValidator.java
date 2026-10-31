package mcmod.yfcorelib.api.mixin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Mixin 配置自检：在启动阶段确认"配置里写的东西是真的"。
 * <p>它防的是这类事故——配置里声明了一个根本不存在的 Mixin 类，而真正的生产 Mixin 忘了注册；
 * 或者声明了 refmap 但构建产物里没有，导致 Mixin 静默不生效。这两种情况 Minecraft 都不会报错，
 * 只会在很久以后表现为"这个功能没反应"。</p>
 *
 * <pre>{@code
 * for (MixinConfigValidator.Problem problem : MixinConfigValidator.validateLoadedConfig("mymod.mixins.json")) {
 *     LOG.error("Mixin 配置问题: {}", problem);
 * }
 * }</pre>
 */
public final class MixinConfigValidator {
	/** 检查一个资源是否存在（refmap 用） */
	@FunctionalInterface
	public interface ResourceLookup {
		boolean exists(String path);
	}

	/** 一条配置问题 */
	public record Problem(String configName, String target, String message) {
		@Override
		public String toString() {
			return "[" + configName + "] " + target + ": " + message;
		}
	}

	private MixinConfigValidator() {
	}

	/** 用类自身所在的类加载器校验 */
	public static List<Problem> validateLoadedConfig(String resourceName) {
		return validateLoadedConfig(resourceName, MixinConfigValidator.class.getClassLoader());
	}

	/**
	 * 读取并校验一份配置。读不到或解析失败都以 {@link Problem} 返回，不抛异常——
	 * 自检的用途是"报告全部问题"，半路抛异常只能看到第一条。
	 */
	public static List<Problem> validateLoadedConfig(String resourceName, ClassLoader loader) {
		Objects.requireNonNull(resourceName, "resourceName");
		Objects.requireNonNull(loader, "loader");
		String json = readResource(resourceName, loader);
		if (json == null) {
			return List.of(new Problem(resourceName, resourceName, "配置资源不在类路径上"));
		}
		MixinConfig config;
		try {
			config = MixinConfigParser.parse(resourceName, json);
		} catch (IllegalArgumentException ex) {
			return List.of(new Problem(resourceName, resourceName, "无法解析: " + ex.getMessage()));
		}
		return validate(config, loader);
	}

	/** 用默认的资源查找（{@code loader.getResource(path) != null}）校验 */
	public static List<Problem> validate(MixinConfig config, ClassLoader loader) {
		Objects.requireNonNull(loader, "loader");
		return validate(config, loader, path -> loader.getResource(path) != null);
	}

	/** 用默认的资源查找校验 */
	public static List<Problem> validate(MixinConfig config, ClassLoader loader, ResourceLookup lookup) {
		return validate(config, loader, lookup, false);
	}

	/**
	 * 校验一份已解析的配置。
	 *
	 * @param config      配置
	 * @param loader      用来查找类文件与 refmap 的类加载器
	 * @param lookup      refmap 资源查找
	 * @param loadClasses {@code true} 时用 {@link Class#forName} 真正加载类来验证存在性；
	 *                    默认 {@code false}，只查 {@code .class} 资源
	 * @return 全部问题；为空表示没发现问题
	 */
	public static List<Problem> validate(MixinConfig config, ClassLoader loader, ResourceLookup lookup,
			boolean loadClasses) {
		Objects.requireNonNull(config, "config");
		Objects.requireNonNull(loader, "loader");
		Objects.requireNonNull(lookup, "lookup");
		List<Problem> problems = new ArrayList<>();

		checkDuplicates(config, problems);
		for (String entry : config.allEntries()) {
			checkClass(config, entry, config.qualifiedName(entry), loader, "Mixin 类", problems, loadClasses);
		}
		if (config.plugin() != null) {
			checkClass(config, "plugin", config.plugin(), loader, "Mixin 插件类", problems, loadClasses);
		}
		if (config.refmap() != null && !lookup.exists(config.refmap())) {
			problems.add(new Problem(config.name(), config.refmap(),
					"refmap 资源不存在：混淆环境下 Mixin 会静默不生效，请确认构建确实生成了它"));
		}
		if (config.allEntries().isEmpty() && config.plugin() == null) {
			// 空壳配置最典型的症状：以为注册了，其实一条都没有
			problems.add(new Problem(config.name(), "-",
					"配置没有声明任何 Mixin，也没有 plugin：确认这是有意为之"));
		}
		return problems;
	}

	private static void checkDuplicates(MixinConfig config, List<Problem> problems) {
		Set<String> seen = new LinkedHashSet<>();
		for (String entry : config.allEntries()) {
			if (!seen.add(entry)) {
				problems.add(new Problem(config.name(), entry,
						"同一个 Mixin 类被声明了多次（mixins/client/server 之间重复）"));
			}
		}
	}

	private static void checkClass(MixinConfig config, String entry, String qualified,
			ClassLoader loader, String what, List<Problem> problems, boolean loadClasses) {
		if (!loadClasses) {
			// 只用资源查类是否存在，绝不 Class.forName 加载 Mixin 类：
			// Fabric/Forge 的类加载器会把"加载 Mixin 类"交给 Mixin 自己去准备，
			// 一旦那一步失败，抛出来的是 "Mixin transformation of ... failed"，
			// 恰好把自检真正想说的"这个类根本不存在"掩盖掉。
			String resourcePath = qualified.replace('.', '/') + ".class";
			if (loader.getResource(resourcePath) == null) {
				problems.add(new Problem(config.name(), entry,
						what + " " + qualified + " 不存在：配置里写了但源码里没有，或包名与 package 字段不一致"));
			}
			return;
		}
		try {
			// initialize=false：只验证类存在，绝不触发别人的静态初始化
			Class.forName(qualified, false, loader);
		} catch (ClassNotFoundException ex) {
			problems.add(new Problem(config.name(), entry,
					what + " " + qualified + " 不存在：配置里写了但源码里没有，或包名与 package 字段不一致"));
		} catch (LinkageError ex) {
			problems.add(new Problem(config.name(), entry,
					what + " " + qualified + " 无法链接（多半是它引用的目标类缺失）: " + ex));
		}
	}

	/** 读类路径资源；不存在返回 null */
	public static String readResource(String path, ClassLoader loader) {
		try (InputStream stream = loader.getResourceAsStream(path)) {
			if (stream == null) {
				return null;
			}
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException ex) {
			return null;
		}
	}

	/** 便捷方法：配置存在则返回解析结果，不存在返回空 */
	public static Optional<MixinConfig> loadConfig(String resourceName, ClassLoader loader) {
		String json = readResource(resourceName, loader);
		if (json == null) {
			return Optional.empty();
		}
		return Optional.of(MixinConfigParser.parse(resourceName, json));
	}
}
