package mcmod.yfcorelib.common.registry;

import java.util.Objects;
import java.util.function.Supplier;

import net.minecraft.resources.ResourceLocation;

/**
 * 注册条目句柄。
 * <p>Fabric 声明即可用；Forge 走延迟注册，注册事件前 {@link #get()} 会抛错，
 * 因此禁止在模组构造期提前取值。</p>
 */
public interface RegistrationRef<T> extends Supplier<T> {
	ResourceLocation id();

	String path();

	/** Forge 在注册事件前返回 false；Fabric 恒为 true */
	boolean isPresent();

	static <T> RegistrationRef<T> of(ResourceLocation id, T value) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(value, "value");
		return new RegistrationRef<>() {
			@Override
			public ResourceLocation id() {
				return id;
			}

			@Override
			public String path() {
				return id.getPath();
			}

			@Override
			public boolean isPresent() {
				return true;
			}

			@Override
			public T get() {
				return value;
			}

			@Override
			public String toString() {
				return id.toString();
			}
		};
	}
}
