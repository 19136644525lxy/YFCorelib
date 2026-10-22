package mcmod.yfcorelib.common.attribute;

import mcmod.yfcorelib.api.data.SnapshotIds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 属性修改快照：稳定 UUID + 修改前 baseValue + 精确还原。
 * <p>两种修改模式：</p>
 * <ul>
 *   <li>{@link #applyModifier}：挂固定数值的修改器（不动 baseValue），反复施加只替换不叠加，天然不漂移</li>
 *   <li>{@link #overrideBase}：覆盖 baseValue，首次捕获快照，还原时精确恢复</li>
 * </ul>
 * <p>覆盖式快照存于内存，跨重启需业务用 {@code PlayerDataStore} 持久化
 * {@link #capturedBase} 并通过 {@link #restoreBase} 还原。实例由业务持有，每个模组一个。</p>
 */
public final class AttributeModifierSnapshot {
	private final String modId;
	/** 实体 UUID -> (快照键 -> 修改前 baseValue)；快照键 = 属性注册名 + ":" + 业务键 */
	private final Map<UUID, Map<String, Double>> baseSnapshots = new ConcurrentHashMap<>();

	public AttributeModifierSnapshot(String modId) {
		this.modId = modId;
	}

	/**
	 * 挂修改器：先用稳定 UUID 移除旧修改器再施加，数值固定不叠加。
	 * <p>原理：baseValue 从未被修改，任何次数的施加/还原都不会漂移。</p>
	 */
	public void applyModifier(LivingEntity entity, Attribute attribute, String key,
			double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = instance(entity, attribute, key);
		UUID uuid = modifierId(key);
		instance.removeModifier(uuid);
		instance.addTransientModifier(new AttributeModifier(uuid, modifierName(key), amount, operation));
	}

	/** 移除指定业务键的修改器（baseValue 未被动过，无需还原） */
	public void removeModifier(LivingEntity entity, Attribute attribute, String key) {
		AttributeInstance instance = instance(entity, attribute, key);
		instance.removeModifier(modifierId(key));
	}

	/**
	 * 覆盖 baseValue：首次调用捕获当前值作为快照，重复调用不覆盖首快照。
	 * <p>还原走 {@link #restore}（会话内）或 {@link #restoreBase}（跨重启）。</p>
	 */
	public void overrideBase(LivingEntity entity, Attribute attribute, String key, double newBase) {
		AttributeInstance instance = instance(entity, attribute, key);
		String snapshotKey = snapshotKey(attribute, key);
		baseSnapshots.computeIfAbsent(entity.getUUID(), id -> new ConcurrentHashMap<>())
				.putIfAbsent(snapshotKey, instance.getBaseValue());
		instance.setBaseValue(newBase);
	}

	/** 会话内还原：移除修改器并把 baseValue 恢复为快照值，随后清除快照 */
	public void restore(LivingEntity entity, Attribute attribute, String key) {
		AttributeInstance instance = instance(entity, attribute, key);
		instance.removeModifier(modifierId(key));
		Map<String, Double> snapshots = baseSnapshots.get(entity.getUUID());
		if (snapshots != null) {
			Double base = snapshots.remove(snapshotKey(attribute, key));
			if (base != null) {
				instance.setBaseValue(base);
			}
		}
	}

	/** 跨重启还原：用业务持久化的快照值恢复 baseValue */
	public void restoreBase(LivingEntity entity, Attribute attribute, String key, double savedBase) {
		instance(entity, attribute, key).setBaseValue(savedBase);
	}

	/** 读取当前快照值（供业务持久化）；无快照返回 empty */
	public Optional<Double> capturedBase(LivingEntity entity, Attribute attribute, String key) {
		Map<String, Double> snapshots = baseSnapshots.get(entity.getUUID());
		if (snapshots == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(snapshots.get(snapshotKey(attribute, key)));
	}

	/** 实体离场时清理其全部快照记录，防内存泄漏 */
	public void clear(LivingEntity entity) {
		baseSnapshots.remove(entity.getUUID());
	}

	/** 清空全部快照记录 */
	public void clear() {
		baseSnapshots.clear();
	}

	/** 稳定修改器 UUID：由 modId:key 派生，跨重启不变 */
	public UUID modifierId(String key) {
		return SnapshotIds.stable(modId, key);
	}

	private String modifierName(String key) {
		return modId + ":" + key;
	}

	private String snapshotKey(Attribute attribute, String key) {
		return attributeId(attribute) + ":" + key;
	}

	private static String attributeId(Attribute attribute) {
		return BuiltInRegistries.ATTRIBUTE.getKey(attribute).toString();
	}

	private static AttributeInstance instance(LivingEntity entity, Attribute attribute, String key) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance == null) {
			throw new IllegalArgumentException("实体不持有属性 " + attributeId(attribute) + " (key=" + key + ")");
		}
		return instance;
	}
}
