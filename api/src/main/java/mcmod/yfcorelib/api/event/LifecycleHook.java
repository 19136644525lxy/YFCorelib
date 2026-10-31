package mcmod.yfcorelib.api.event;

/** 生命周期钩子：玩家就绪（登录且完成初始同步）后回调 */
public interface LifecycleHook {
	void onPlayerReady(PlayerRef player);

	/**
	 * 玩家离开时回调，用于释放按玩家维度的资源。
	 * <p>默认空实现，不强制既有实现改写；{@link LifecycleQueue#playerGone} 会调用它。
	 * 抛出的异常由队列捕获记录，不影响其他钩子。</p>
	 */
	default void onPlayerGone(PlayerRef player) {
	}
}
