package mcmod.yfcorelib.common.registry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import mcmod.yfcorelib.api.registry.RegistryPaths;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** 单个模组的内容命名空间：按注册表缓存 Registrar，并提供常用类型的便捷方法 */
public final class ContentNamespace {
	private final String modId;
	private final Map<ResourceLocation, Registrar<?>> registrars = new ConcurrentHashMap<>();

	ContentNamespace(String modId) {
		this.modId = RegistryPaths.requireNamespace(modId);
	}

	public String modId() {
		return modId;
	}

	/** 通用入口：任意静态注册表，供未提供便捷方法的类型使用 */
	@SuppressWarnings("unchecked")
	public <T> Registrar<T> registrar(ResourceKey<? extends Registry<T>> key) {
		return (Registrar<T>) registrars.computeIfAbsent(key.location(),
				ignored -> YFRegistries.create(modId, key));
	}

	public Registrar<Item> items() {
		return registrar(Registries.ITEM);
	}

	public Registrar<Block> blocks() {
		return registrar(Registries.BLOCK);
	}

	public Registrar<BlockEntityType<?>> blockEntities() {
		return registrar(Registries.BLOCK_ENTITY_TYPE);
	}

	public Registrar<EntityType<?>> entities() {
		return registrar(Registries.ENTITY_TYPE);
	}

	public Registrar<MobEffect> effects() {
		return registrar(Registries.MOB_EFFECT);
	}

	public Registrar<SoundEvent> sounds() {
		return registrar(Registries.SOUND_EVENT);
	}

	public Registrar<ParticleType<?>> particles() {
		return registrar(Registries.PARTICLE_TYPE);
	}

	public Registrar<MenuType<?>> menus() {
		return registrar(Registries.MENU);
	}

	public Registrar<CreativeModeTab> tabs() {
		return registrar(Registries.CREATIVE_MODE_TAB);
	}

	public Registrar<Enchantment> enchantments() {
		return registrar(Registries.ENCHANTMENT);
	}

	public Registrar<RecipeSerializer<?>> recipeSerializers() {
		return registrar(Registries.RECIPE_SERIALIZER);
	}

	public Registrar<RecipeType<?>> recipeTypes() {
		return registrar(Registries.RECIPE_TYPE);
	}

	/** 注册方块并自动生成同名 BlockItem */
	public <T extends Block> BlockEntry<T> block(String path,
			Function<BlockBehaviour.Properties, T> factory, BlockBehaviour.Properties properties) {
		return block(path, factory, properties, true);
	}

	/** 注册方块；withItem=false 时不生成物品（如仅用于技术性方块） */
	@SuppressWarnings("unchecked")
	public <T extends Block> BlockEntry<T> block(String path,
			Function<BlockBehaviour.Properties, T> factory, BlockBehaviour.Properties properties,
			boolean withItem) {
		RegistrationRef<T> blockRef = (RegistrationRef<T>) (RegistrationRef<?>) blocks()
				.add(path, () -> factory.apply(properties));
		RegistrationRef<Item> itemRef = withItem
				? items().add(path, () -> new BlockItem(blockRef.get(), new Item.Properties()))
				: null;
		return new BlockEntry<>(path, blockRef, itemRef);
	}

	/** 可变音域音效（最常用） */
	public RegistrationRef<SoundEvent> sound(String path) {
		return sound(path, true);
	}

	public RegistrationRef<SoundEvent> sound(String path, boolean variableRange) {
		return sounds().add(path, () -> variableRange
				? SoundEvent.createVariableRangeEvent(sounds().id(path))
				: SoundEvent.createFixedRangeEvent(sounds().id(path), 16.0F));
	}

	@SuppressWarnings("unchecked")
	public <T extends AbstractContainerMenu> RegistrationRef<MenuType<T>> menu(
			String path, MenuType.MenuSupplier<T> supplier) {
		return (RegistrationRef<MenuType<T>>) (RegistrationRef<?>) menus()
				.add(path, () -> new MenuType<>(supplier, FeatureFlags.VANILLA_SET));
	}

	@SuppressWarnings("unchecked")
	public <T extends Entity> RegistrationRef<EntityType<T>> entity(
			String path, EntityType.Builder<T> builder) {
		return (RegistrationRef<EntityType<T>>) (RegistrationRef<?>) entities()
				.add(path, () -> builder.build(id(path).toString()));
	}

	/** 完整 id：modId:path */
	public ResourceLocation id(String path) {
		ResourceLocation id = ResourceLocation.tryParse(modId + ":" + RegistryPaths.requirePath(path));
		if (id == null) {
			throw new IllegalArgumentException("非法注册 id：" + modId + ":" + path);
		}
		return id;
	}
}
