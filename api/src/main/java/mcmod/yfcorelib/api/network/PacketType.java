package mcmod.yfcorelib.api.network;

import java.util.Objects;

import mcmod.yfcorelib.api.Side;
import mcmod.yfcorelib.api.data.DataCodec;

/**
 * 包类型声明：id + 接收侧 + 消息类 + 编解码器 + 处理器。
 * <p>receiver=SERVER 表示客户端发给服务端，CLIENT 表示服务端发给客户端，COMMON 双向。
 * packetClass 供平台加载器（如 Forge SimpleChannel）做消息类型映射。</p>
 */
public record PacketType<T extends Packet>(String id, Side receiver, Class<T> packetClass,
		DataCodec<T> codec, PacketHandler<T> handler) {
	public PacketType {
		PacketIds.requireValid(id);
		Objects.requireNonNull(receiver, "receiver");
		Objects.requireNonNull(packetClass, "packetClass");
		Objects.requireNonNull(codec, "codec");
		Objects.requireNonNull(handler, "handler");
	}
}
