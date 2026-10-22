package mcmod.yfcorelib.api.registry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Registry 默认实现：写操作持锁保证顺序与冲突检测原子性，读操作走不可变快照 */
public final class SimpleRegistry<T> implements Registry<T> {
	private static final Logger LOG = Logger.getLogger(SimpleRegistry.class.getName());

	private final String name;
	private final ConcurrentHashMap<String, Registration<T>> map = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, CopyOnWriteArrayList<String>> tagIndex = new ConcurrentHashMap<>();
	private final CopyOnWriteArrayList<RegistryListener<? super T>> listeners = new CopyOnWriteArrayList<>();
	private final ReentrantLock lock = new ReentrantLock();
	private volatile List<String> order = List.of();
	private volatile boolean frozen;

	public SimpleRegistry(String name) {
		this.name = Objects.requireNonNull(name, "name");
	}

	public void addListener(RegistryListener<? super T> listener) {
		listeners.add(Objects.requireNonNull(listener));
	}

	@Override
	public Registration<T> register(String id, T value) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(value, "value");
		lock.lock();
		try {
			if (frozen) {
				throw new IllegalStateException("注册表 " + name + " 已冻结，拒绝注册 " + id);
			}
			Registration<T> fresh = new Registration<>(id, value);
			Registration<T> prev = map.putIfAbsent(id, fresh);
			if (prev != null) {
				throw new IllegalStateException("注册冲突：id '" + id + "' 已存在于注册表 " + name);
			}
			List<String> next = new ArrayList<>(order.size() + 1);
			next.addAll(order);
			next.add(id);
			order = List.copyOf(next);
			notifyRegister(id, value);
			return fresh;
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void tag(String id, String tag) {
		Objects.requireNonNull(tag, "tag");
		lock.lock();
		try {
			if (frozen) {
				throw new IllegalStateException("注册表 " + name + " 已冻结，拒绝打标签 " + id);
			}
			if (!map.containsKey(id)) {
				throw new IllegalArgumentException("注册表 " + name + " 中不存在 id '" + id + "'");
			}
			tagIndex.computeIfAbsent(tag, k -> new CopyOnWriteArrayList<>()).addIfAbsent(id);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public Optional<T> get(String id) {
		Registration<T> reg = map.get(id);
		return reg == null ? Optional.empty() : Optional.of(reg.get());
	}

	@Override
	public T getOrThrow(String id) {
		Registration<T> reg = map.get(id);
		if (reg == null) {
			throw new NoSuchElementException("注册表 " + name + " 中不存在 id '" + id + "'");
		}
		return reg.get();
	}

	@Override
	public List<T> values() {
		List<T> out = new ArrayList<>(order.size());
		for (String id : order) {
			Registration<T> reg = map.get(id);
			if (reg != null) {
				out.add(reg.get());
			}
		}
		return out;
	}

	@Override
	public List<T> byTag(String tag) {
		CopyOnWriteArrayList<String> ids = tagIndex.get(tag);
		if (ids == null) {
			return List.of();
		}
		Set<String> tagged = new HashSet<>(ids);
		List<T> out = new ArrayList<>(ids.size());
		for (String id : order) {
			if (tagged.contains(id)) {
				Registration<T> reg = map.get(id);
				if (reg != null) {
					out.add(reg.get());
				}
			}
		}
		return out;
	}

	@Override
	public Set<String> ids() {
		return new LinkedHashSet<>(order);
	}

	@Override
	public void freeze() {
		lock.lock();
		try {
			if (frozen) {
				return;
			}
			frozen = true;
			notifyFreeze();
		} finally {
			lock.unlock();
		}
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
				LOG.log(Level.SEVERE, "注册监听器异常 registry=" + name + " id=" + id, ex);
			}
		}
	}

	private void notifyFreeze() {
		for (RegistryListener<? super T> listener : listeners) {
			try {
				listener.onFreeze();
			} catch (Throwable ex) {
				LOG.log(Level.SEVERE, "冻结监听器异常 registry=" + name, ex);
			}
		}
	}
}
