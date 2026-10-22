package mcmod.yfcorelib.common.registry;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/** 单一内容注册表入口：一个 (modId, 注册表) 对应一个实例 */
public interface Registrar<T> {
	String modId();

	ResourceKey<? extends Registry<T>> key();

	/** 拼出本注册表内的完整 id，path 会做合法性校验 */
	ResourceLocation id(String path);

	/** 声明一个条目；同 path 重复声明立即抛错 */
	RegistrationRef<T> add(String path, Supplier<T> factory);

	boolean contains(String path);

	/** 已声明 path，按声明顺序 */
	List<String> paths();

	int size();
}
