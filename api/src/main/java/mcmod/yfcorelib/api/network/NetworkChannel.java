package mcmod.yfcorelib.api.network;

import java.util.UUID;

/**
 * 网络通道：注册包类型后收发包。
 * <p>所有接收侧处理器已在主线程回调；发送侧要求在主线程调用（与 MC 包发送惯例一致）。</p>
 */
public interface NetworkChannel {
	/** 注册包类型；id 冲突立即抛错，必须在首次发送前完成 */
	void register(PacketType<?>... types);

	/** 客户端 -> 服务器 */
	void sendToServer(Packet packet);

	/** 服务器 -> 指定玩家 */
	void sendToPlayer(UUID playerId, Packet packet);

	/** 服务器 -> 全部玩家 */
	void broadcast(Packet packet);

	/** 服务器 -> 指定维度坐标附近 radius 格内的玩家 */
	void sendToNear(String dimension, double x, double y, double z, double radius, Packet packet);

	boolean isRegistered(PacketType<?> type);
}
