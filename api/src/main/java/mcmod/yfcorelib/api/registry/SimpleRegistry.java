package mcmod.yfcorelib.api.registry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry 默认实现：写操作持锁，读操作读不可变的 {@link State} 快照。
 * <p>{@code map} 与 {@code order} 打包在同一份快照里一起替换，
 * 读线程不会看到"get() 有值但 values() 缺项"的中间态。</p>
 * <p>监听器在锁外回调：监听器里再次调用 register/freeze 不会死锁，也不会看到半成品状态。</p>
 */
public final class SimpleRegistry<T> implements Registry<T> {
	private static final Logger LOG = LoggerFactory.getLogger(SimpleRegistry.class);

	private final String name;
	private final ReentrantLock lock = new ReentrantLock();
	private final ConcurrentHashMap<String, CopyOnWriteArrayList<String>> tagIndex = new ConcurrentHashMap<>();
	private final CopyOnWriteArrayList<RegistryListener<? super T>> listeners = new CopyOnWriteArrayList<>();
	private volatile State<T> state = State.empty();
	private volatile boolean frozen;

	public SimpleRegistry(String name) {
		this.name = Objects.requireNonNull(name, "name");
	}

	/** 一次不可变发布单元：值与顺序必须同时生效 */
	private record State<T>(Map<String, Registration<T>> map, List<String> order) {
		static <T> State<T> empty() {
			return new State<>(Map.of(), List.of());
		}
	}

	public void addListener(RegistryListener<? super T> listener) {
		listeners.add(Objects.requireNonNull(listener));
	}

	/** 退订监听器；注册表是长生命周期对象，不退订会让监听器（连同其捕获的上下文）永久存活 */
	public boolean removeListener(RegistryListener<? super T> listener) {
		return listeners.remove(listener);
	}

	@Override
	public Registration<T> register(String id, T value) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(value, "value");
		Registration<T> fresh;
		lock.lock();
		try {
			if (frozen) {
				throw new IllegalStateException("注册表 " + name + " 已冻结，拒绝注册 " + id);
			}
			State<T> current = state;
			if (current.map().containsKey(id)) {
				throw new IllegalStateException("注册冲突：id '" + id + "' 已存在于注册表 " + name);
			}
			fresh = new Registration<>(id, value);
			Map<String, Registration<T>> nextMap = new LinkedHashMap<>(current.map());
			nextMap.put(id, fresh);
			List<String> nextOrder = new ArrayList<>(current.order().size() + 1);
			nextOrder.addAll(current.order());
			nextOrder.add(id);
			state = new State<>(Map.copyOf(nextMap), List.copyOf(nextOrder));
		} finally {
			lock.unlock();
		}
		// 锁外回调：监听器抛异常或重入都不会把注册表卡住
		notifyRegister(id, value);
		return fresh;
	}

	@Override
	public void tag(String id, String tag) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(tag, "tag");
		lock.lock();
		try {
			if (frozen) {
				throw new IllegalStateException("注册表 " + name + " 已冻结，拒绝打标签 " + id);
			}
			if (!state.map().containsKey(id)) {
				throw new IllegalArgumentException("注册表 " + name + " 中不存在 id '" + id + "'");
			}
			tagIndex.computeIfAbsent(tag, k -> new CopyOnWriteArrayList<>()).addIfAbsent(id);
		} finally {
			lock.unlock();
		}
	}

	/** 解除标签；不存在时返回 false。冻结后同样拒绝（冻结意味着内容不可再变） */
	public boolean untag(String id, String tag) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(tag, "tag");
		lock.lock();
		try {
			if (frozen) {
				throw new IllegalStateException("注册表 " + name + " 已冻结，拒绝解除标签 " + id);
			}
			CopyOnWriteArrayList<String> ids = tagIndex.get(tag);
			if (ids == null) {
				return false;
			}
			boolean removed = ids.remove(id);
			if (ids.isEmpty()) {
				tagIndex.remove(tag, ids);
			}
			return removed;
		} finally {
			lock.unlock();
		}
	}

	@Override
	public Optional<T> get(String id) {
		Objects.requireNonNull(id, "id");
		Registration<T> reg = state.map().get(id);
		return reg == null ? Optional.empty() : Optional.of(reg.get());
	}

	@Override
	public T getOrThrow(String id) {
		Objects.requireNonNull(id, "id");
		Registration<T> reg = state.map().get(id);
		if (reg == null) {
			throw new NoSuchElementException("注册表 " + name + " 中不存在 id '" + id + "'");
		}
		return reg.get();
	}

	@Override
	public List<T> values() {
		State<T> snapshot = state;
		List<T> out = new ArrayList<>(snapshot.order().size());
		for (String id : snapshot.order()) {
			Registration<T> reg = snapshot.map().get(id);
			if (reg != null) {
				out.add(reg.get());
			}
		}
		return out;
	}

	@Override
	public List<T> byTag(String tag) {
		State<T> snapshot = state;
		CopyOnWriteArrayList<String> ids = tagIndex.get(tag);
		if (ids == null) {
			return List.of();
		}
		Set<String> tagged = new HashSet<>(ids);
		List<T> out = new ArrayList<>(tagged.size());
		for (String id : snapshot.order()) {
			if (tagged.contains(id)) {
				Registration<T> reg = snapshot.map().get(id);
				if (reg != null) {
					out.add(reg.get());
				}
			}
		}
		return out;
	}

	@Override
	public Set<String> ids() {
		return new LinkedHashSet<>(state.order());
	}

	@Override
	public void freeze() {
		lock.lock();
		try {
			if (frozen) {
				return;
			}
			frozen = true;
		} finally {
			lock.unlock();
		}
		// 锁外回调，避免监听器在持有注册表锁时再去注册别的内容而死锁
		notifyFreeze();
	}

	@Override
	public boolean isFrozen() {
		return frozen;
	}

	private void notifyRegister(String id, T value) {
		for (RegistryListener<? super T> listener : listeners) {
			try {
				listener.onRegister(id, value);
			} catch (Throwable ex) {
				LOG.error("注册监听器异常 registry={} id={}", name, id, ex);
			}
		}
	}

	private void notifyFreeze() {
		for (RegistryListener<? super T> listener : listeners) {
			try {
				listener.onFreeze();
			} catch (Throwable ex) {
				LOG.error("冻结监听器异常 registry={}", name, ex);
			}
		}
	}
}
