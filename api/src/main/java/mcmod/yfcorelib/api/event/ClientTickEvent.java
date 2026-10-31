package mcmod.yfcorelib.api.event;

/**
 * 客户端刻事件：每客户端刻末尾发布一次（相当于 Fabric 的 END_CLIENT_TICK / Forge 的 ClientTickEvent.Phase.END）。
 * <p>与 {@link ServerTickEvent} 分成两个类型，是因为 {@code SimpleEventBus} 按精确类型分发：
 * 若沿用 {@code ServerTickEvent(Side.CLIENT)}，监听服务端刻的代码会同时被客户端刻触发，只能靠判断 side 兜底。</p>
 */
public record ClientTickEvent() {
}
