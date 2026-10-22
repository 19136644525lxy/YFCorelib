package mcmod.yfcorelib.fabric;

import mcmod.yfcorelib.api.Side;
import mcmod.yfcorelib.api.YFCoreLib;
import mcmod.yfcorelib.api.event.ServerTickEvent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** 客户端入口：驱动客户端调度器、转发刻事件并激活网络 S2C 接收 */
public final class YFCoreLibClientFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// 激活网络桥在客户端入口挂起的 S2C 接收器
		FabricNetworkChannel.activateClient();
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			YFCoreLib.scheduler(Side.CLIENT).tick();
			YFCoreLib.events().post(new ServerTickEvent(Side.CLIENT));
		});
	}
}
