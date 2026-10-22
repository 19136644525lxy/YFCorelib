package mcmod.yfcorelib.api.event;

/** 玩家跨维度；from/to 为维度 id 字符串，如 minecraft:overworld */
public record PlayerDimensionChangeEvent(PlayerRef player, String from, String to) {
}
