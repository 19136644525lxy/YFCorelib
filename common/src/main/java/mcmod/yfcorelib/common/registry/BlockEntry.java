package mcmod.yfcorelib.common.registry;

import java.util.Optional;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** 方块注册结果：方块本体 + 可选同名 BlockItem */
public final class BlockEntry<T extends Block> {
	private final String path;
	private final RegistrationRef<T> block;
	private final RegistrationRef<Item> item;

	BlockEntry(String path, RegistrationRef<T> block, RegistrationRef<Item> item) {
		this.path = path;
		this.block = block;
		this.item = item;
	}

	public String path() {
		return path;
	}

	public RegistrationRef<T> block() {
		return block;
	}

	public Optional<RegistrationRef<Item>> item() {
		return Optional.ofNullable(item);
	}

	public T get() {
		return block.get();
	}

	/** 方块对应的物品实例；未生成 BlockItem 或 Forge 尚未注册完成时为空 */
	public Optional<Item> asItem() {
		// Forge 延迟注册：注册事件前 get() 会抛错，这里先判存在性
		return item().filter(RegistrationRef::isPresent).map(RegistrationRef::get);
	}
}
