package mcmod.yfcorelib.fabric;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonParser;

import mcmod.yfcorelib.api.Side;
import mcmod.yfcorelib.api.network.NetworkChannel;
import mcmod.yfcorelib.api.network.Packet;
import mcmod.yfcorelib.api.network.PacketContext;
import mcmod.yfcorelib.api.network.PacketType;
import mcmod.yfcorelib.api.network.SimpleChannelRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * fabric-networking-api-v0 桥：负责注册收发器、编解码与主线程切换。
 * <p>接收回调位于网络线程，统一 execute 到主线程后再回调业务 handler。</p>
 */
public final class FabricNetworkChannel implements NetworkChannel {
	private static final Logger LOG = LoggerFactory.getLogger("yfcorelib");
	private static final Gson GSON = new Gson();
	/** 单包负载上限（1MB UTF），防御恶意超大包 */
	private static final int MAX_PAYLOAD = 1_000_000;

	/** 当前运行的服务器实例：仅服务器侧发送使用，启动时持有、停止时释放 */
	private static volatile MinecraftServer currentServer;

	/** 客户端入口激活前挂起的 S2C 接收器，激活时统一注册 */
	private record PendingReceiver(ResourceLocation id, ClientPlayNetworking.PlayChannelHandler handler) {
	}

	private static final CopyOnWriteArrayList<PendingReceiver> pendingClient = new CopyOnWriteArrayList<>();
	private static volatile boolean clientReady = false;

	static {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> currentServer = server);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> currentServer = null);
	}

	private final SimpleChannelRegistry registry = new SimpleChannelRegistry();
	private final Map<String, ResourceLocation> idCache = new ConcurrentHashMap<>();

	FabricNetworkChannel(String name) {
		// fabric 模型为"每包类型一个 Identifier"，通道名仅作缓存 key，解析兼做格式校验
		parseId(name);
	}

	/** 客户端入口激活：注册所有挂起的 S2C 接收器（幂等） */
	static void activateClient() {
		clientReady = true;
		for (PendingReceiver pending : pendingClient) {
			ClientPlayNetworking.registerGlobalReceiver(pending.id(), pending.handler());
		}
		pendingClient.clear();
	}

	@Override
	public void register(PacketType<?>... types) {
		for (PacketType<?> type : types) {
			registry.register(type);
			ResourceLocation id = id(type.id());
			if (type.receiver() == Side.SERVER || type.receiver() == Side.COMMON) {
				ServerPlayNetworking.registerGlobalReceiver(id, serverReceiver(type));
			}
			if (type.receiver() == Side.CLIENT || type.receiver() == Side.COMMON) {
				ClientPlayNetworking.PlayChannelHandler handler = clientReceiver(type);
				if (clientReady) {
					ClientPlayNetworking.registerGlobalReceiver(id, handler);
				} else {
					pendingClient.add(new PendingReceiver(id, handler));
				}
			}
		}
	}

	@Override
	public void sendToServer(Packet packet) {
		PacketType<?> type = registry.require(packet.typeId());
		requireDirection(type, Side.SERVER, "sendToServer");
		ClientPlayNetworking.send(id(type.id()), encode(type, packet));
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
		ServerPlayNetworking.send(player, id(type.id()), encode(type, packet));
	}

	@Override
	public void broadcast(Packet packet) {
		PacketType<?> type = registry.require(packet.typeId());
		requireDirection(type, Side.CLIENT, "broadcast");
		FriendlyByteBuf buf = encode(type, packet);
		ResourceLocation id = id(type.id());
		for (ServerPlayer player : requireServer().getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(player, id, new FriendlyByteBuf(buf.copy()));
		}
	}

	@Override
	public void sendToNear(String dimension, double x, double y, double z, double radius, Packet packet) {
		PacketType<?> type = registry.require(packet.typeId());
		requireDirection(type, Side.CLIENT, "sendToNear");
		MinecraftServer server = requireServer();
		ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dimension));
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
		FriendlyByteBuf buf = encode(type, packet);
		ResourceLocation id = id(type.id());
		for (ServerPlayer player : targets) {
			ServerPlayNetworking.send(player, id, new FriendlyByteBuf(buf.copy()));
		}
	}

	@Override
	public boolean isRegistered(PacketType<?> type) {
		return registry.isRegistered(type);
	}

	/** 服务端接收 C2S：网络线程解码占位，切主线程后回调业务 */
	private <T extends Packet> ServerPlayNetworking.PlayChannelHandler serverReceiver(PacketType<T> type) {
		return (server, player, handler, buf, responseSender) -> {
			String payload = buf.readUtf(MAX_PAYLOAD);
			server.execute(() -> {
				try {
					T packet = type.codec().decode(JsonParser.parseString(payload));
					UUID sender = player.getUUID();
					type.handler().handle(packet, new PacketContext(sender, Side.SERVER,
							reply -> sendToPlayer(sender, reply)));
				} catch (Exception e) {
					// 单包失败不拖垮连接，仅记录便于排查
					LOG.warn("处理 C2S 包失败: {}", type.id(), e);
				}
			});
		};
	}

	/** 客户端接收 S2C：切客户端主线程后回调业务 */
	private <T extends Packet> ClientPlayNetworking.PlayChannelHandler clientReceiver(PacketType<T> type) {
		return (client, handler, buf, responseSender) -> {
			String payload = buf.readUtf(MAX_PAYLOAD);
			client.execute(() -> {
				try {
					T packet = type.codec().decode(JsonParser.parseString(payload));
					type.handler().handle(packet, new PacketContext(null, Side.CLIENT, this::sendToServer));
				} catch (Exception e) {
					LOG.warn("处理 S2C 包失败: {}", type.id(), e);
				}
			});
		};
	}

	private MinecraftServer requireServer() {
		MinecraftServer server = currentServer;
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

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static FriendlyByteBuf encode(PacketType type, Packet packet) {
		String json = GSON.toJson(type.codec().encode(packet));
		FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
		buf.writeUtf(json);
		return buf;
	}

	private ResourceLocation id(String packetId) {
		return idCache.computeIfAbsent(packetId, FabricNetworkChannel::parseId);
	}

	/** 解析 "命名空间:路径"：tryParse 无效输入返回 null，这里转为明确异常 */
	private static ResourceLocation parseId(String id) {
		ResourceLocation loc = ResourceLocation.tryParse(id);
		if (loc == null) {
			throw new IllegalArgumentException("非法网络 id: " + id);
		}
		return loc;
	}
}
