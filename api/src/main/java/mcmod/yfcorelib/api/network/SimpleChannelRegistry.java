package mcmod.yfcorelib.api.network;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 通道内包类型注册表：id 校验、冲突即抛错、按 id 查找 */
public final class SimpleChannelRegistry {
	private final Map<String, PacketType<?>> types = new ConcurrentHashMap<>();

	public void register(PacketType<?>... packetTypes) {
		for (PacketType<?> type : packetTypes) {
			PacketIds.requireValid(type.id());
			PacketType<?> prev = types.putIfAbsent(type.id(), type);
			if (prev != null) {
				throw new IllegalStateException("包类型 id 冲突: " + type.id());
			}
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
		return types.get(type.id()) == type;
	}

	public int size() {
		return types.size();
	}
}
