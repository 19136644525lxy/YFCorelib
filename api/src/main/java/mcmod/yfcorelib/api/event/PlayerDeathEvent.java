package mcmod.yfcorelib.api.event;

import java.util.Objects;

/**
 * 玩家死亡事件：由本库自带的 Mixin 在 {@code LivingEntity#die} 头部触发（仅服务端玩家）。
 * <p>此时玩家还活着（血量可能已归零但实体尚未被移除），适合做"死亡瞬间"的结算。</p>
 *
 * <pre>{@code
 * YFCoreLib.events().register(PlayerDeathEvent.class, event -> {
 *     UUID id = event.player().id();
 *     ...
 * });
 * }</pre>
 *
 * <p>注意：事件按精确类型分发，监听 {@code Object.class} 不会收到它。</p>
 */
public record PlayerDeathEvent(PlayerRef player) {
	public PlayerDeathEvent {
		Objects.requireNonNull(player, "player");
	}
}
