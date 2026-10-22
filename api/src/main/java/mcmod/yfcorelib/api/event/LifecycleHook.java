package mcmod.yfcorelib.api.event;

/** 生命周期钩子：玩家就绪（登录且完成初始同步）后回调 */
public interface LifecycleHook {
	void onPlayerReady(PlayerRef player);
}
