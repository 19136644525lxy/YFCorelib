package mcmod.yfcorelib.api.interop;

import java.util.Optional;
import java.util.Set;

/**
 * 能力注册中心：按 {@link CapabilityKey} 存放"某项能力的实现"，供调用方按需取用。
 * <p>与 {@link mcmod.yfcorelib.api.registry.Registry} 的区别：Registry 管的是"游戏内内容"
 * （物品、方块……），带 id 顺序与标签；本类管的是"接口实现"，不参与内容注册、不需要 id 顺序，
 * 查询失败是常态（对方模组没装），因此用 {@link Optional} 而不是抛异常。</p>
 */
public interface CapabilityRegistry {
	/**
	 * 注册能力实现。
	 *
	 * @throws IllegalStateException 同一 key 已被注册过（重复注册通常意味着两边都以为自己是唯一提供者）
	 * @throws IllegalArgumentException 实现对象不是 key 声明的类型
	 */
	<T> void register(CapabilityKey<T> key, T provider);

	/** 取出能力实现；未注册时为空 */
	<T> Optional<T> get(CapabilityKey<T> key);

	/** 是否存在实现 */
	boolean isRegistered(CapabilityKey<?> key);

	/** 已注册的能力 id（快照） */
	Set<String> ids();
}
