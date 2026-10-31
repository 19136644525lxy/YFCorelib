package mcmod.yfcorelib.api.event;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 延迟加载队列：收集需要在"玩家就绪"后执行的业务钩子。
 * <p>晚于玩家就绪注册的钩子会对当前在线玩家补发，保证时序无关。</p>
 * <p>注册与就绪派发互斥（{@code synchronized}）：否则 addHook 刚加入列表、
 * 还没遍历完在线玩家时，另一线程的 playerReady 会让同一钩子对同一玩家触发两次。
 * 钩子内部再调用 addHook / playerReady 时是可重入的，不会自锁。</p>
 */
public final class LifecycleQueue {
	private static final Logger LOG = LoggerFactory.getLogger(LifecycleQueue.class);

	private final CopyOnWriteArrayList<LifecycleHook> hooks = new CopyOnWriteArrayList<>();
	private final ConcurrentHashMap<UUID, PlayerRef> online = new ConcurrentHashMap<>();

	/**
	 * 注册钩子，并对当前已就绪的玩家立即补发。
	 *
	 * @return 订阅句柄，close 后钩子不再触发；可重复 close
	 */
	public synchronized EventBus.Subscription addHook(LifecycleHook hook) {
		Objects.requireNonNull(hook, "hook");
		hooks.add(hook);
		for (PlayerRef ref : online.values()) {
			safeDispatch(hook, ref);
		}
		return () -> hooks.remove(hook);
	}

	/** 标记玩家就绪并触发全部钩子；由平台模块在登录完成后调用 */
	public synchronized void playerReady(PlayerRef player) {
		Objects.requireNonNull(player, "player");
		online.put(player.id(), player);
		for (LifecycleHook hook : hooks) {
			safeDispatch(hook, player);
		}
	}

	/** 玩家离线：清除在线状态并通知钩子，下次登录重新走就绪流程 */
	public synchronized void playerGone(UUID id) {
		Objects.requireNonNull(id, "id");
		PlayerRef ref = online.remove(id);
		if (ref == null) {
			return;
		}
		for (LifecycleHook hook : hooks) {
			try {
				hook.onPlayerGone(ref);
			} catch (Throwable ex) {
				LOG.error("生命周期钩子离场异常 player={}", id, ex);
			}
		}
	}

	private void safeDispatch(LifecycleHook hook, PlayerRef player) {
		try {
			hook.onPlayerReady(player);
		} catch (Throwable ex) {
			LOG.error("生命周期钩子异常 player={}", player.id(), ex);
		}
	}
}
