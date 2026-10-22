package mcmod.yfcorelib.forge;

import java.util.function.Supplier;

import mcmod.yfcorelib.common.registry.AbstractRegistrar;
import mcmod.yfcorelib.common.registry.Registrar;
import mcmod.yfcorelib.common.registry.RegistrarFactory;
import mcmod.yfcorelib.common.registry.RegistrationRef;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;

/**
 * Forge 注册后端：使用 DeferredRegister，把注册推迟到各注册表事件。
 * <p>DeferredRegister 必须挂在当前模组的事件总线上，所以首次创建必须发生在模组构造期间。</p>
 */
public final class ForgeRegistrarFactory implements RegistrarFactory {
	// 1.20.1 只能用静态 get() 取当前模组总线（构造器注入方式 1.21.1 才推荐）
	@SuppressWarnings({ "removal", "deprecation" })
	@Override
	public <T> Registrar<T> create(String modId, ResourceKey<? extends Registry<T>> key) {
		IEventBus bus;
		try {
			bus = FMLJavaModLoadingContext.get().getModEventBus();
		} catch (RuntimeException ex) {
			throw new IllegalStateException(
					"Forge 下注册必须在模组构造期间完成（modId=" + modId + "）", ex);
		}
		if (bus == null) {
			throw new IllegalStateException("Forge 模组事件总线不可用：请把注册移到模组构造器内（modId=" + modId + "）");
		}
		return new ForgeRegistrar<>(modId, key, bus);
	}

	private static final class ForgeRegistrar<T> extends AbstractRegistrar<T> {
		private final DeferredRegister<T> deferred;

		private ForgeRegistrar(String modId, ResourceKey<? extends Registry<T>> key, IEventBus bus) {
			super(modId, key);
			this.deferred = DeferredRegister.create(key, modId);
			this.deferred.register(bus);
		}

		@Override
		protected RegistrationRef<T> doAdd(ResourceLocation id, Supplier<T> factory) {
			return new ForgeRegistrationRef<>(deferred.register(id.getPath(), factory));
		}
	}
}
