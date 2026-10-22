package mcmod.yfcorelib.api.sync;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * 快照同步默认实现：内容比较免重发（相同快照不发送）。
 * <p>发送动作通过 Sender 注入，由业务绑定到 NetworkChannel，保持本类零平台依赖。</p>
 */
public final class SimpleSyncEngine<S> implements SyncEngine<S> {
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
		S old = snapshots.put(playerId, snapshot);
		// old == null 表示首次发布也视为变化
		if (onChange && !snapshot.equals(old)) {
			sender.send(playerId, snapshot);
		}
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
			listener.accept(playerId, snapshot);
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
		if (everyTicks <= 0) {
			return;
		}
		if (tickCounter.incrementAndGet() >= everyTicks) {
			tickCounter.set(0);
			snapshots.forEach(sender::send);
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
