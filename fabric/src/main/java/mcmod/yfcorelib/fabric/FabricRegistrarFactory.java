package mcmod.yfcorelib.fabric;

import java.util.function.Supplier;

import mcmod.yfcorelib.common.registry.AbstractRegistrar;
import mcmod.yfcorelib.common.registry.Registrar;
import mcmod.yfcorelib.common.registry.RegistrarFactory;
import mcmod.yfcorelib.common.registry.RegistrationRef;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/** Fabric 注册后端：声明即写入静态注册表 */
public final class FabricRegistrarFactory implements RegistrarFactory {
	@SuppressWarnings("unchecked")
	@Override
	public <T> Registrar<T> create(String modId, ResourceKey<? extends Registry<T>> key) {
		Registry<?> registry = BuiltInRegistries.REGISTRY.get(key.location());
		if (registry == null) {
			throw new IllegalStateException(
					"不支持的注册表：" + key.location() + "（注册 DSL 仅覆盖静态注册表）");
		}
		return new FabricRegistrar<>(modId, key, (Registry<T>) registry);
	}

	private static final class FabricRegistrar<T> extends AbstractRegistrar<T> {
		private final Registry<T> registry;

		private FabricRegistrar(String modId, ResourceKey<? extends Registry<T>> key, Registry<T> registry) {
			super(modId, key);
			this.registry = registry;
		}

		@Override
		protected RegistrationRef<T> doAdd(ResourceLocation id, Supplier<T> factory) {
			T value = factory.get();
			Registry.register(registry, id, value);
			return RegistrationRef.of(id, value);
		}
	}
}
