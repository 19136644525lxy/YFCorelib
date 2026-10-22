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

	/** 方块对应的物品实例；未生成 BlockItem 时为空 */
	public Optional<Item> asItem() {
		return item().map(RegistrationRef::get);
	}
}
