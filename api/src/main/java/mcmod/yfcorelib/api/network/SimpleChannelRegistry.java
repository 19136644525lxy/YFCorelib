package mcmod.yfcorelib.api.network;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通道内包类型注册表：id 校验、冲突即抛错、按 id 查找。
 * <p>注册是两阶段（先全量校验、再统一写入），中途冲突不会留下"注册了一半"的通道。</p>
 */
public final class SimpleChannelRegistry {
	private final Map<String, PacketType<?>> types = new ConcurrentHashMap<>();

	/** 注册一批包类型；任一 id 非法或冲突则整批不生效 */
	public synchronized void register(PacketType<?>... packetTypes) {
		Objects.requireNonNull(packetTypes, "packetTypes");
		Set<String> seen = new HashSet<>();
		for (PacketType<?> type : packetTypes) {
			Objects.requireNonNull(type, "packetType");
			PacketIds.requireValid(type.id());
			if (!seen.add(type.id()) || types.containsKey(type.id())) {
				throw new IllegalStateException("包类型 id 冲突: " + type.id());
			}
		}
		for (PacketType<?> type : packetTypes) {
			types.put(type.id(), type);
		}
	}

	/** 查找已注册类型，未注册抛出明确错误 */
	public PacketType<?> require(String id) {
		PacketType<?> type = types.get(id);
		if (type == null) {
			throw new IllegalStateException("包类型未注册，请先在通道 register: " + id);
		}
		return type;
	}

	public boolean isRegistered(PacketType<?> type) {
		if (type == null) {
			return false;
		}
		// PacketType 是 record：按值比较，而不是引用比较
		return type.equals(types.get(type.id()));
	}

	public int size() {
		return types.size();
	}
}
