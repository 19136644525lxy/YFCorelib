package mcmod.yfcorelib.api.network;

import java.util.UUID;
import java.util.function.Consumer;

import mcmod.yfcorelib.api.Side;

/**
 * 包处理上下文：sender 为发送方玩家（服务器收到 C2S 时非空）。
 * <p>reply 原路回包：服务端回发给来源玩家，客户端回发到服务器。</p>
 */
public record PacketContext(UUID sender, Side side, Consumer<Packet> reply) {
	public PacketContext {
		java.util.Objects.requireNonNull(side, "side");
		java.util.Objects.requireNonNull(reply, "reply");
	}

	public void reply(Packet packet) {
		reply.accept(packet);
	}
}
