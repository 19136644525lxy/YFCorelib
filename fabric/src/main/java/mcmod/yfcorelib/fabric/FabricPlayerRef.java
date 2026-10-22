package mcmod.yfcorelib.fabric;

import java.util.UUID;

import mcmod.yfcorelib.api.event.PlayerRef;
import net.minecraft.server.level.ServerPlayer;

/** ServerPlayer 适配：api 层只见 UUID 与名字 */
final class FabricPlayerRef implements PlayerRef {
	private final ServerPlayer player;

	FabricPlayerRef(ServerPlayer player) {
		this.player = player;
	}

	@Override
	public UUID id() {
		return player.getUUID();
	}

	@Override
	public String name() {
		return player.getGameProfile().getName();
	}

	@Override
	public boolean isOnline() {
		return player.connection != null;
	}
}
