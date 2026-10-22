package mcmod.yfcorelib.fabric;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import mcmod.yfcorelib.api.network.NetworkChannel;
import mcmod.yfcorelib.api.network.NetworkProvider;

/** 网络通道服务实现：按名缓存通道单例 */
public final class FabricNetworkProvider implements NetworkProvider {
	private final Map<String, NetworkChannel> channels = new ConcurrentHashMap<>();

	@Override
	public NetworkChannel channel(String name) {
		return channels.computeIfAbsent(name, FabricNetworkChannel::new);
	}
}
