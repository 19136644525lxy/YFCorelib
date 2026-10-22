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
import net.minecraft.world.level.Level;

/** 生命周期事件桥：把 fabric-api 原生事件翻译为 api 事件，不引入 Mixin */
final class FabricLifecycleBridge {
	private FabricLifecycleBridge() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> YFCoreLib.events().post(new ServerStartedEvent()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> YFCoreLib.events().post(new ServerStoppingEvent()));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			YFCoreLib.scheduler(Side.SERVER).tick();
			YFCoreLib.events().post(new ServerTickEvent(Side.SERVER));
			if (server.getTickCount() % 200 == 0) {
				YFCoreLib.timers().sweep();
			}
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			PlayerRef ref = new FabricPlayerRef(handler.player);
			YFCoreLib.events().post(new PlayerJoinEvent(ref));
			// 延迟 1 刻标记就绪：等客户端完成初始数据同步
			YFCoreLib.scheduler(Side.SERVER).delay(1, () -> YFCoreLib.lifecycle().playerReady(ref));
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			YFCoreLib.events().post(new PlayerLeaveEvent(new FabricPlayerRef(handler.player)));
			YFCoreLib.lifecycle().playerGone(handler.player.getUUID());
		});

		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			boolean fromEnd = oldPlayer.level().dimension() == Level.END;
			YFCoreLib.events().post(new PlayerRespawnEvent(new FabricPlayerRef(newPlayer), fromEnd));
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, dest) ->
				YFCoreLib.events().post(new PlayerDimensionChangeEvent(new FabricPlayerRef(player),
						origin.dimension().location().toString(),
						dest.dimension().location().toString())));
	}
}
