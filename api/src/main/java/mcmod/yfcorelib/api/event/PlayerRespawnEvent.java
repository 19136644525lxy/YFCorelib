package mcmod.yfcorelib.api.event;

/** 玩家重生；endPortal 表示经由末地折跃门返回 */
public record PlayerRespawnEvent(PlayerRef player, boolean endPortal) {
}
