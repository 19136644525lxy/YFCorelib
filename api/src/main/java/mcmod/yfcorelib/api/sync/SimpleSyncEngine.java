package mcmod.yfcorelib.api.sync;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 快照同步默认实现：内容比较免重发（相同快照不发送）。
 * <p>发送动作通过 Sender 注入，由业务绑定到 NetworkChannel，保持本类零平台依赖。</p>
 * <p>只有发送成功后才记录新快照：发送失败时下一次发布仍会被判定为"有变化"，不会静默丢同步。</p>
 */
public final class SimpleSyncEngine<S> implements SyncEngine<S> {
	private static final Logger LOG = LoggerFactory.getLogger(SimpleSyncEngine.class);

	/** 发送动作抽象：测试时可注入假实现 */
	public interface Sender<S> {
		void send(UUID playerId, S snapshot);
	}

	private final Sender<S> sender;
	private final ConcurrentHashMap<UUID, S> snapshots = new ConcurrentHashMap<>();
	private final CopyOnWriteArrayList<BiConsumer<UUID, S>> listeners = new CopyOnWriteArrayList<>();
	private volatile boolean onLogin = true;
	private volatile boolean onChange = true;
	private volatile int everyTicks = 0;
	private final AtomicInteger tickCounter = new AtomicInteger();

	public SimpleSyncEngine(Sender<S> sender) {
		this.sender = Objects.requireNonNull(sender, "sender");
	}

	@Override
	public void publish(UUID playerId, S snapshot) {
		Objects.requireNonNull(playerId, "playerId");
		Objects.requireNonNull(snapshot, "snapshot");
		S old = snapshots.get(playerId);
		// old == null 表示首次发布也视为变化
		boolean changed = !snapshot.equals(old);
		if (onChange && changed) {
			// 先发后记：send 抛异常时快照不被记为"已同步"，
			// 否则相同内容再也不会重发，客户端永久停在旧状态
			sender.send(playerId, snapshot);
		}
		snapshots.put(playerId, snapshot);
	}

	@Override
	public S snapshot(UUID playerId) {
		return snapshots.get(playerId);
	}

	@Override
	public AutoCloseable subscribe(BiConsumer<UUID, S> listener) {
		listeners.add(Objects.requireNonNull(listener, "listener"));
		return () -> listeners.remove(listener);
	}

	@Override
	public void receive(UUID playerId, S snapshot) {
		for (BiConsumer<UUID, S> listener : listeners) {
			try {
				listener.accept(playerId, snapshot);
			} catch (Throwable ex) {
				// 单个订阅者出错不能拖垮其余订阅者
				LOG.error("快照订阅者异常 player={}", playerId, ex);
			}
		}
	}

	@Override
	public void onJoin(UUID playerId) {
		if (onLogin) {
			S snapshot = snapshots.get(playerId);
			if (snapshot != null) {
				sender.send(playerId, snapshot);
			}
		}
	}

	@Override
	public void onLeave(UUID playerId) {
		snapshots.remove(playerId);
	}

	@Override
	public void tick() {
		int every = everyTicks;
		if (every <= 0) {
			return;
		}
		// 取模而不是 incrementAndGet 再 set(0)：两步之间被并发 tick 打断会丢掉一次整轮同步
		if (tickCounter.incrementAndGet() % every != 0) {
			return;
		}
		for (var entry : snapshots.entrySet()) {
			try {
				sender.send(entry.getKey(), entry.getValue());
			} catch (Throwable ex) {
				// 单个玩家发送失败不影响其他玩家
				LOG.error("定时同步发送失败 player={}", entry.getKey(), ex);
			}
		}
	}

	@Override
	public SyncEngine<S> syncOnLogin(boolean value) {
		this.onLogin = value;
		return this;
	}

	@Override
	public SyncEngine<S> syncOnChange(boolean value) {
		this.onChange = value;
		return this;
	}

	@Override
	public SyncEngine<S> syncEveryTicks(int value) {
		if (value < 0) {
			throw new IllegalArgumentException("syncEveryTicks 不得为负数");
		}
		this.everyTicks = value;
		return this;
	}

	@Override
	public boolean syncOnLogin() {
		return onLogin;
	}

	@Override
	public boolean syncOnChange() {
		return onChange;
	}

	@Override
	public int syncEveryTicks() {
		return everyTicks;
	}
}
