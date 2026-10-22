package mcmod.yfcorelib.api.event;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 延迟加载队列：收集需要在"玩家就绪"后执行的业务钩子。
 * <p>晚于玩家就绪注册的钩子会对当前在线玩家补发，保证时序无关。</p>
 */
public final class LifecycleQueue {
	private static final Logger LOG = Logger.getLogger(LifecycleQueue.class.getName());

	private final CopyOnWriteArrayList<LifecycleHook> hooks = new CopyOnWriteArrayList<>();
	private final ConcurrentHashMap<UUID, PlayerRef> online = new ConcurrentHashMap<>();

	public void addHook(LifecycleHook hook) {
		hooks.add(Objects.requireNonNull(hook, "hook"));
		for (PlayerRef ref : online.values()) {
			safeDispatch(hook, ref);
		}
	}

	/** 标记玩家就绪并触发全部钩子；由平台模块在登录完成后调用 */
	public void playerReady(PlayerRef player) {
		Objects.requireNonNull(player, "player");
		online.put(player.id(), player);
		for (LifecycleHook hook : hooks) {
			safeDispatch(hook, player);
		}
	}

	/** 玩家离线：清除在线状态，下次登录重新走就绪流程 */
	public void playerGone(UUID id) {
		online.remove(id);
	}

	private void safeDispatch(LifecycleHook hook, PlayerRef player) {
		try {
			hook.onPlayerReady(player);
		} catch (Throwable ex) {
			LOG.log(Level.SEVERE, "生命周期钩子异常 player=" + player.id(), ex);
		}
	}
}
