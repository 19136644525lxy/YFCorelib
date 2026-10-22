package mcmod.yfcorelib.forge;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.google.gson.Gson;
import com.google.gson.JsonParser;

import mcmod.yfcorelib.api.Side;
import mcmod.yfcorelib.api.network.NetworkChannel;
import mcmod.yfcorelib.api.network.Packet;
import mcmod.yfcorelib.api.network.PacketContext;
import mcmod.yfcorelib.api.network.PacketType;
import mcmod.yfcorelib.api.network.SimpleChannelRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.PacketDistributor.PacketTarget;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forge SimpleChannel 桥：注册消息、编解码与主线程切换。
 * <p>enqueueWork 切主线程后回调业务 handler；S2C 处理器不引用客户端专属类，
 * 专用服务器注册无副作用，双端统一注册。</p>
 */
public final class ForgeNetworkChannel implements NetworkChannel {
	private static final Logger LOG = LoggerFactory.getLogger("yfcorelib");
	private static final Gson GSON = new Gson();
	/** 单包负载上限（1MB UTF），防御恶意超大包 */
	private static final int MAX_PAYLOAD = 1_000_000;

	private final SimpleChannel raw;
	private final SimpleChannelRegistry registry = new SimpleChannelRegistry();
	private final AtomicInteger nextId = new AtomicInteger();

	ForgeNetworkChannel(String name) {
		this.raw = NetworkRegistry.newSimpleChannel(parseId(name),
				() -> "1", "1"::equals, "1"::equals);
	}

	@Override
	public void register(PacketType<?>... types) {
		registry.register(types);
		for (PacketType<?> type : types) {
			registerMessage(type);
		}
	}

	@Override
	public void sendToServer(Packet packet) {
		PacketType<?> type = registry.require(packet.typeId());
		requireDirection(type, Side.SERVER, "sendToServer");
		raw.sendToServer(packet);
	}

	@Override
	public void sendToPlayer(UUID playerId, Packet packet) {
		PacketType<?> type = registry.require(packet.typeId());
		requireDirection(type, Side.CLIENT, "sendToPlayer");
		ServerPlayer player = requireServer().getPlayerList().getPlayer(playerId);
		if (player == null) {
			LOG.debug("玩家已离线，丢弃包 {}: {}", type.id(), playerId);
			return;
		}
		sendRaw(PacketDistributor.PLAYER.with(() -> player), packet);
	}

	@Override
	public void broadcast(Packet packet) {
		PacketType<?> type = registry.require(packet.typeId());
		requireDirection(type, Side.CLIENT, "broadcast");
		sendRaw(PacketDistributor.ALL.noArg(), packet);
	}

	@Override
	public void sendToNear(String dimension, double x, double y, double z, double radius, Packet packet) {
		PacketType<?> type = registry.require(packet.typeId());
		requireDirection(type, Side.CLIENT, "sendToNear");
		MinecraftServer server = requireServer();
		ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, parseId(dimension));
		ServerLevel level = server.getLevel(key);
		if (level == null) {
			throw new IllegalArgumentException("维度不存在: " + dimension);
		}
		List<ServerPlayer> targets = new ArrayList<>();
		double radiusSqr = radius * radius;
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(x, y, z) <= radiusSqr) {
				targets.add(player);
			}
		}
		for (ServerPlayer player : targets) {
			sendRaw(PacketDistributor.PLAYER.with(() -> player), packet);
		}
	}

	@Override
	public boolean isRegistered(PacketType<?> type) {
		return registry.isRegistered(type);
	}

	private <T extends Packet> void registerMessage(PacketType<T> type) {
		Optional<NetworkDirection> direction = switch (type.receiver()) {
			case SERVER -> Optional.of(NetworkDirection.PLAY_TO_SERVER);
			case CLIENT -> Optional.of(NetworkDirection.PLAY_TO_CLIENT);
			case COMMON -> Optional.empty();
		};
		raw.registerMessage(nextId.getAndIncrement(), type.packetClass(),
				(packet, buf) -> buf.writeUtf(GSON.toJson(type.codec().encode(packet)), MAX_PAYLOAD),
				buf -> type.codec().decode(JsonParser.parseString(buf.readUtf(MAX_PAYLOAD))),
				(packet, ctx) -> dispatch(type, packet, ctx.get()),
				direction);
	}

	/** 切主线程后按来源分发：带 sender 为服务器侧，否则为客户端侧 */
	private <T extends Packet> void dispatch(PacketType<T> type, T packet, NetworkEvent.Context ctx) {
		ctx.enqueueWork(() -> {
			try {
				ServerPlayer sender = ctx.getSender();
				if (sender != null) {
					UUID senderId = sender.getUUID();
					type.handler().handle(packet, new PacketContext(senderId, Side.SERVER,
							reply -> sendToPlayer(senderId, reply)));
				} else {
					type.handler().handle(packet, new PacketContext(null, Side.CLIENT, this::sendToServer));
				}
			} catch (Exception e) {
				// 单包失败不影响连接，仅记录便于排查
				LOG.warn("处理网络包失败: {}", type.id(), e);
			}
		});
		ctx.setPacketHandled(true);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private void sendRaw(PacketTarget target, Packet packet) {
		// 运行时按 packet 实际类查已注册编码器，此处 raw type 仅为绕开编译期泛型
		raw.send(target, packet);
	}

	/** 解析 "命名空间:路径"：tryParse 无效输入返回 null，这里转为明确异常 */
	private static ResourceLocation parseId(String id) {
		ResourceLocation loc = ResourceLocation.tryParse(id);
		if (loc == null) {
			throw new IllegalArgumentException("非法网络 id: " + id);
		}
		return loc;
	}

	private MinecraftServer requireServer() {
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server == null) {
			throw new IllegalStateException("服务器未启动，无法发送服务器侧网络包");
		}
		return server;
	}

	private static void requireDirection(PacketType<?> type, Side side, String op) {
		boolean ok = switch (side) {
			case SERVER -> type.receiver() == Side.SERVER;
			case CLIENT -> type.receiver() == Side.CLIENT || type.receiver() == Side.COMMON;
			default -> false;
		};
		if (!ok) {
			throw new IllegalArgumentException(op + " 不允许发送 receiver=" + type.receiver() + " 的包: " + type.id());
		}
	}
}
