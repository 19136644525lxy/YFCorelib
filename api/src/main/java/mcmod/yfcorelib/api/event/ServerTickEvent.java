package mcmod.yfcorelib.api.event;

import mcmod.yfcorelib.api.Side;

/** 服务端/客户端刻事件，side 标明来源 */
public record ServerTickEvent(Side side) {
}
