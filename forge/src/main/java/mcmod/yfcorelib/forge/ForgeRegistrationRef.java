package mcmod.yfcorelib.forge;

import mcmod.yfcorelib.common.registry.RegistrationRef;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.RegistryObject;

/** Forge 延迟注册句柄：注册事件前 isPresent() 为 false */
final class ForgeRegistrationRef<T> implements RegistrationRef<T> {
	private final RegistryObject<? extends T> object;

	ForgeRegistrationRef(RegistryObject<? extends T> object) {
		this.object = object;
	}

	@Override
	public ResourceLocation id() {
		return object.getId();
	}

	@Override
	public String path() {
		return object.getId().getPath();
	}

	@Override
	public boolean isPresent() {
		return object.isPresent();
	}

	@Override
	public T get() {
		return object.get();
	}

	@Override
	public String toString() {
		return object.getId().toString();
	}
}
