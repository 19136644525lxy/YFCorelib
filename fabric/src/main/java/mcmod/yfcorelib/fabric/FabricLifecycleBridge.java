package mcmod.yfcorelib.fabric;

import mcmod.yfcorelib.api.Side;
import mcmod.yfcorelib.api.YFCoreLib;
import mcmod.yfcorelib.api.event.PlayerDimensionChangeEvent;
import mcmod.yfcorelib.api.event.PlayerJoinEvent;
import mcmod.yfcorelib.api.event.PlayerLeaveEvent;
import mcmod.yfcorelib.api.event.PlayerRef;
import mcmod.yfcorelib.api.event.PlayerRespawnEvent;
import mcmod.yfcorelib.api.event.ServerStartedEvent;
import mcmod.yfcorelib.api.event.ServerStoppingEvent;
import mcmod.yfcorelib.api.event.ServerTickEvent;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/** 生命周期事件桥：把 fabric-api 原生事件翻译为 api 事件，不引入 Mixin */
final class FabricLifecycleBridge {
	private FabricLifecycleBridge() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> YFCoreLib.events().post(new ServerStartedEvent()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> YFCoreLib.events().post(new ServerStoppingEvent()));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			YFCoreLib.scheduler(Side.SERVER).tick();
			YFCoreLib.events().post(new ServerTickEvent());
			if (server.getTickCount() % 200 == 0) {
				YFCoreLib.timers().sweep();
			}
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			PlayerRef ref = new FabricPlayerRef(handler.player);
			YFCoreLib.events().post(new PlayerJoinEvent(ref));
			// 延迟 1 刻标记就绪：等客户端完成初始数据同步。
			// 玩家可能在这一刻内就断开（playerGone 已同步执行），此时不能再补发 ready，
			// 否则在线表里会留下一个已经离线的引用
			YFCoreLib.scheduler(Side.SERVER).delay(1, () -> {
				if (ref.isOnline()) {
					YFCoreLib.lifecycle().playerReady(ref);
				}
			});
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			YFCoreLib.events().post(new PlayerLeaveEvent(new FabricPlayerRef(handler.player)));
			YFCoreLib.lifecycle().playerGone(handler.player.getUUID());
		});

		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			// alive=true 表示"活着被重置"（经由末地折跃门返回），与 Forge 的 isEndConquered() 同义；
			// 之前用 oldPlayer 是否在末地来判断，会把"在末地普通死亡"误报成折跃门返回
			YFCoreLib.events().post(new PlayerRespawnEvent(new FabricPlayerRef(newPlayer), alive));
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, dest) ->
				YFCoreLib.events().post(new PlayerDimensionChangeEvent(new FabricPlayerRef(player),
						origin.dimension().location().toString(),
						dest.dimension().location().toString())));
	}
}
