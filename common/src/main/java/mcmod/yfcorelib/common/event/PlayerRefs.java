package mcmod.yfcorelib.common.event;

import java.util.Objects;
import java.util.UUID;

import mcmod.yfcorelib.api.event.PlayerRef;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 把游戏内的 {@link Player} 现场包装成 {@link PlayerRef}。
 * <p>本库自带的 Mixin（{@code LivingEntityDeathMixin}）需要在注入点直接构造引用，
 * 而平台的 {@code FabricPlayerRef}/{@code ForgePlayerRef} 只在各自入口内部使用，
 * 因此这里提供一个共用的轻量实现。</p>
 */
public final class PlayerRefs {
	private PlayerRefs() {
	}

	public static PlayerRef of(Player player) {
		Objects.requireNonNull(player, "player");
		return new LivePlayerRef(player);
	}

	/** 直接持有游戏对象：只在同步回调里短暂存活，不要长期缓存 */
	private record LivePlayerRef(Player player) implements PlayerRef {
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
			// ServerPlayer 的在线与否看连接；此时死亡事件刚触发，通常仍在线
			return player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null;
		}
	}
}
