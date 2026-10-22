package mcmod.yfcorelib.common.registry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import mcmod.yfcorelib.api.platform.Services;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

/** 注册 DSL 门面：按 modId 缓存命名空间，避免重复创建注册后端 */
public final class YFRegistries {
	private static final Map<String, ContentNamespace> NAMESPACES = new ConcurrentHashMap<>();
	private static volatile RegistrarFactory factory;

	private YFRegistries() {
	}

	/**
	 * 取得模组的内容命名空间。
	 * <p>Forge 下必须在模组构造期间首次调用（延迟注册表需要挂接模组事件总线）。</p>
	 */
	public static ContentNamespace namespace(String modId) {
		return NAMESPACES.computeIfAbsent(modId, ContentNamespace::new);
	}

	static <T> Registrar<T> create(String modId, ResourceKey<? extends Registry<T>> key) {
		return factory().create(modId, key);
	}

	private static RegistrarFactory factory() {
		RegistrarFactory current = factory;
		if (current == null) {
			synchronized (YFRegistries.class) {
				if (factory == null) {
					factory = Services.load(RegistrarFactory.class).orElseThrow(() -> new IllegalStateException(
							"未找到 RegistrarFactory 实现：请确认平台模块已注册 META-INF/services"));
				}
				current = factory;
			}
		}
		return current;
	}
}
