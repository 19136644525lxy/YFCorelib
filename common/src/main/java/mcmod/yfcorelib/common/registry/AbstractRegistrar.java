package mcmod.yfcorelib.common.registry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import mcmod.yfcorelib.api.registry.RegistryPaths;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * 注册器公共骨架：id 校验、重复检测、声明顺序记录。
 * <p>具体写入动作由平台子类实现（Fabric 立即写入 / Forge 延迟到注册事件）。</p>
 */
public abstract class AbstractRegistrar<T> implements Registrar<T> {
	private final String modId;
	private final ResourceKey<? extends Registry<T>> key;
	private final Map<String, RegistrationRef<T>> entries = new LinkedHashMap<>();

	protected AbstractRegistrar(String modId, ResourceKey<? extends Registry<T>> key) {
		this.modId = RegistryPaths.requireNamespace(modId);
		this.key = Objects.requireNonNull(key, "key");
	}

	@Override
	public final String modId() {
		return modId;
	}

	@Override
	public final ResourceKey<? extends Registry<T>> key() {
		return key;
	}

	@Override
	public final ResourceLocation id(String path) {
		return newId(RegistryPaths.requirePath(path));
	}

	@Override
	public final RegistrationRef<T> add(String path, Supplier<T> factory) {
		String clean = RegistryPaths.requirePath(path);
		Objects.requireNonNull(factory, "factory");
		synchronized (entries) {
			if (entries.containsKey(clean)) {
				throw new IllegalStateException(
						"重复注册：" + modId + ":" + clean + " 已存在于 " + key.location());
			}
			RegistrationRef<T> ref = doAdd(newId(clean), factory);
			entries.put(clean, ref);
			return ref;
		}
	}

	@Override
	public final boolean contains(String path) {
		synchronized (entries) {
			return entries.containsKey(path);
		}
	}

	@Override
	public final List<String> paths() {
		synchronized (entries) {
			return List.copyOf(entries.keySet());
		}
	}

	@Override
	public final int size() {
		synchronized (entries) {
			return entries.size();
		}
	}

	/** 平台写入实现；失败必须抛错，以便 add() 不留下半成品记录 */
	protected abstract RegistrationRef<T> doAdd(ResourceLocation id, Supplier<T> factory);

	private ResourceLocation newId(String path) {
		ResourceLocation id = ResourceLocation.tryParse(modId + ":" + path);
		if (id == null) {
			throw new IllegalArgumentException("非法注册 id：" + modId + ":" + path);
		}
		return id;
	}
}
