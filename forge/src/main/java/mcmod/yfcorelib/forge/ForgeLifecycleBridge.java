package mcmod.yfcorelib.forge;

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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/** FML 事件桥：把 FML 事件翻译为 api 事件，与 fabric 桥行为对齐 */
public final class ForgeLifecycleBridge {
	private ForgeLifecycleBridge() {
	}

	public static void register(IEventBus bus) {
		bus.addListener((TickEvent.ServerTickEvent e) -> {
			if (e.phase == TickEvent.Phase.END) {
				YFCoreLib.scheduler(Side.SERVER).tick();
				YFCoreLib.events().post(new ServerTickEvent(Side.SERVER));
				if (e.getServer().getTickCount() % 200 == 0) {
					YFCoreLib.timers().sweep();
				}
			}
		});
		// ClientTickEvent 在专用服务器上不会触发，仅类加载无副作用
		bus.addListener((TickEvent.ClientTickEvent e) -> {
			if (e.phase == TickEvent.Phase.END) {
				YFCoreLib.scheduler(Side.CLIENT).tick();
				YFCoreLib.events().post(new ServerTickEvent(Side.CLIENT));
			}
		});
		bus.addListener((net.minecraftforge.event.server.ServerStartedEvent e) ->
				YFCoreLib.events().post(new ServerStartedEvent()));
		bus.addListener((net.minecraftforge.event.server.ServerStoppingEvent e) ->
				YFCoreLib.events().post(new ServerStoppingEvent()));

		bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> onLogin(e.getEntity()));
		bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> onLogout(e.getEntity()));
		bus.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
			if (e.getEntity() instanceof ServerPlayer player) {
				YFCoreLib.events().post(new PlayerRespawnEvent(new ForgePlayerRef(player), e.isEndConquered()));
			}
		});
		bus.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
			if (e.getEntity() instanceof ServerPlayer player) {
				YFCoreLib.events().post(new PlayerDimensionChangeEvent(new ForgePlayerRef(player),
						e.getFrom().location().toString(), e.getTo().location().toString()));
			}
		});
	}

	private static void onLogin(Player player) {
		if (player instanceof ServerPlayer sp) {
			PlayerRef ref = new ForgePlayerRef(sp);
			YFCoreLib.events().post(new PlayerJoinEvent(ref));
			// 延迟 1 刻标记就绪：等客户端完成初始数据同步
			YFCoreLib.scheduler(Side.SERVER).delay(1, () -> YFCoreLib.lifecycle().playerReady(ref));
		}
	}

	private static void onLogout(Player player) {
		if (player instanceof ServerPlayer sp) {
			YFCoreLib.events().post(new PlayerLeaveEvent(new ForgePlayerRef(sp)));
			YFCoreLib.lifecycle().playerGone(sp.getUUID());
		}
	}
}
