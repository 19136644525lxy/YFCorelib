package mcmod.yfcorelib.api.data;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import mcmod.yfcorelib.api.event.EventBus;
import mcmod.yfcorelib.api.event.PlayerJoinEvent;
import mcmod.yfcorelib.api.event.PlayerLeaveEvent;
import mcmod.yfcorelib.api.event.ServerStoppingEvent;

/**
 * 会话数据默认实现：挂接事件总线，加入即创建、离开即回收。
 * <p>这样两端不需要各自的 Attachment/Capability 注册样板，行为完全一致；
 * 1.20.1 的 Fabric 附件与 Forge Capability 只能挂在玩家实体上，离线时不可达，
 * 无法满足 {@code PlayerDataStore} 的离线读取语义，故持久化仍由 JSON 仓库负责。</p>
 */
public final class SimplePlayerSessionStore<T> implements PlayerSessionStore<T>, AutoCloseable {
	private final Supplier<T> factory;
	private final BiConsumer<UUID, T> onEvict;
	private final ConcurrentHashMap<UUID, T> values = new ConcurrentHashMap<>();
	private final Set<UUID> online = ConcurrentHashMap.newKeySet();
	private final EventBus.Subscription joinSubscription;
	private final EventBus.Subscription leaveSubscription;
	private final EventBus.Subscription stopSubscription;

	public SimplePlayerSessionStore(EventBus bus, Supplier<T> factory) {
		this(bus, factory, null);
	}

	/**
	 * @param factory 每个玩家进入时创建默认值
	 * @param onEvict 离开/清空时的回调（可为 null），用于把会话数据转存到持久化仓库
	 */
	public SimplePlayerSessionStore(EventBus bus, Supplier<T> factory, BiConsumer<UUID, T> onEvict) {
		Objects.requireNonNull(bus, "bus");
		this.factory = Objects.requireNonNull(factory, "factory");
		this.onEvict = onEvict;
		this.joinSubscription = bus.register(PlayerJoinEvent.class, event -> {
			UUID id = event.player().id();
			online.add(id);
			values.computeIfAbsent(id, key -> factory.get());
		});
		this.leaveSubscription = bus.register(PlayerLeaveEvent.class, event -> {
			UUID id = event.player().id();
			online.remove(id);
			evict(id);
		});
		this.stopSubscription = bus.register(ServerStoppingEvent.class, event -> clear());
	}

	@Override
	public T get(UUID player) {
		return values.get(player);
	}

	@Override
	public T getOrCreate(UUID player) {
		Objects.requireNonNull(player, "player");
		return values.computeIfAbsent(player, key -> factory.get());
	}

	@Override
	public void set(UUID player, T value) {
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(value, "value");
		values.put(player, value);
	}

	@Override
	public boolean has(UUID player) {
		return values.containsKey(player);
	}

	@Override
	public void remove(UUID player) {
		evict(player);
	}

	@Override
	public Set<UUID> online() {
		return Set.copyOf(online);
	}

	@Override
	public int size() {
		return values.size();
	}

	@Override
	public void clear() {
		for (UUID id : new HashSet<>(values.keySet())) {
			evict(id);
		}
		values.clear();
		online.clear();
	}

	/** 停止监听；已存在的数据保留，调用方负责按需 clear */
	@Override
	public void close() {
		joinSubscription.close();
		leaveSubscription.close();
		stopSubscription.close();
	}

	private void evict(UUID player) {
		T value = values.remove(player);
		if (value != null && onEvict != null) {
			onEvict.accept(player, value);
		}
		online.remove(player);
	}
}
