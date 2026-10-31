package mcmod.yfcorelib.api.mixin;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import mcmod.yfcorelib.api.YFCoreLib;
import mcmod.yfcorelib.api.event.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mixin 与业务代码之间的唯一通道：Mixin 里只负责"发现时机 + 组事件"，业务逻辑一律写成监听器。
 * <p>这样做的原因很实际：Mixin 代码跑在别人的方法体里，写业务会带来三个后果——
 * 异常直接冒进原版方法、无法单测、多个模组注入同一位置时互相踩。</p>
 *
 * <pre>{@code
 * // Mixin 侧（只做转发，不写业务）
 * @Inject(method = "tick", at = @At("HEAD"))
 * private void yfcorelib$onTick(CallbackInfo ci) {
 *     MixinCallbacks.fire(new EntityTickEvent(this));
 * }
 *
 * // 业务侧（普通监听器，可单测）
 * YFCoreLib.events().register(EntityTickEvent.class, event -> { ... });
 * }</pre>
 *
 * <p><strong>调用时机约束</strong>：事件总线由本库创建，因此 {@code fire} 只能在库初始化之后调用。
 * 早于初始化（例如别的模组的类静态初始化器里）触发的事件会被丢弃，并只记录一次警告——
 * 静默丢弃比在别人的方法体里抛异常安全。</p>
 */
public final class MixinCallbacks {
	private static final Logger LOG = LoggerFactory.getLogger(MixinCallbacks.class);
	private static final AtomicBoolean WARNED_EARLY = new AtomicBoolean();

	private static volatile boolean enabled = true;

	private MixinCallbacks() {
	}

	/**
	 * 把事件转发给全局事件总线。
	 *
	 * @param event 事件对象，通常是 {@code record}
	 * @throws NullPointerException event 为 null
	 */
	public static void fire(Object event) {
		Objects.requireNonNull(event, "event");
		if (!enabled) {
			return;
		}
		try {
			YFCoreLib.events().post(event);
		} catch (RuntimeException ex) {
			// 最可能的来源是"库还没初始化就触发了回调"；在 Mixin 里抛出会直接破坏原版方法
			if (WARNED_EARLY.compareAndSet(false, true)) {
				LOG.warn("Mixin 回调在库初始化前或初始化失败后触发，事件 {} 已丢弃（后续同类情况不再警告）",
						event.getClass().getName(), ex);
			}
		}
	}

	/** 订阅事件的便捷写法，等价于 {@code YFCoreLib.events().register(type, listener)} */
	public static <T> EventBus.Subscription on(Class<T> type, Consumer<T> listener) {
		return YFCoreLib.events().register(type, listener);
	}

	/** 当前是否转发事件（默认开启）；用于紧急排查或在不希望本库介入时整体关掉 */
	public static boolean isEnabled() {
		return enabled;
	}

	public static void setEnabled(boolean value) {
		enabled = value;
	}

	/** 仅供单测重置警告状态 */
	static void resetWarning() {
		WARNED_EARLY.set(false);
	}
}
