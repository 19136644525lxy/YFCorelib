package mcmod.yfcorelib.api.scheduler;

import java.util.Set;

/**
 * 命名计时器：按游戏刻倒计时。
 * <p>M2 为内存实现，跨重启持久化在 M3 落地。</p>
 */
public interface TimerStore {
	/** 剩余刻数；不存在或已过期返回 0 */
	long remaining(String key);

	/** 设置/重置倒计时（单位：刻） */
	void set(String key, long ticks);

	void clear(String key);

	/** 未过期的全部键 */
	Set<String> keys();

	/** 清理过期条目，由平台周期调用防内存泄漏 */
	void sweep();
}
