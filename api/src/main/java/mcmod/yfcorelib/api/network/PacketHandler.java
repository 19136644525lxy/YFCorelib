package mcmod.yfcorelib.api.network;

/** 包处理器：总在接收侧主线程回调，可直接安全操作世界状态 */
@FunctionalInterface
public interface PacketHandler<T extends Packet> {
	void handle(T packet, PacketContext context);
}
