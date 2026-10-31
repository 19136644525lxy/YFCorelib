package mcmod.yfcorelib.api.mixin;

import java.util.List;
import java.util.Objects;

/**
 * 一份 Mixin 配置（{@code *.mixins.json}）的只读视图。
 * <p>字段与 Mixin 官方 schema 一一对应；未写的字段归一成空列表 / null，
 * 这样校验代码不需要到处判空。</p>
 *
 * @param name              配置名（通常是资源名，例如 {@code mymod.mixins.json}）
 * @param packageName       {@code package} 字段：Mixin 类所在包
 * @param mixins            {@code mixins} 字段：双端共用的 Mixin 类简单名
 * @param client            {@code client} 字段：仅客户端的 Mixin 类简单名
 * @param server            {@code server} 字段：仅服务端的 Mixin 类简单名
 * @param refmap            {@code refmap} 字段：refmap 资源路径，可为 null
 * @param plugin            {@code plugin} 字段：Mixin 插件类全名，可为 null
 * @param requiredMods      {@code requiredMods} 字段：缺失即禁用整份配置
 * @param compatibilityLevel {@code compatibilityLevel} 字段，可为 null（默认 JAVA_6/JAVA_8）
 */
public record MixinConfig(
		String name,
		String packageName,
		List<String> mixins,
		List<String> client,
		List<String> server,
		String refmap,
		String plugin,
		List<String> requiredMods,
		String compatibilityLevel) {

	public MixinConfig {
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(packageName, "packageName");
		mixins = mixins == null ? List.of() : List.copyOf(mixins);
		client = client == null ? List.of() : List.copyOf(client);
		server = server == null ? List.of() : List.copyOf(server);
		requiredMods = requiredMods == null ? List.of() : List.copyOf(requiredMods);
	}

	/** 双端 + 客户端 + 服务端，全部 Mixin 条目（保持声明顺序） */
	public List<String> allEntries() {
		return java.util.stream.Stream.of(mixins, client, server)
				.flatMap(List::stream)
				.toList();
	}

	/** 某个条目在配置里的完整类名 */
	public String qualifiedName(String entry) {
		return packageName + "." + entry;
	}
}
