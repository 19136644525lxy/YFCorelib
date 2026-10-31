package mcmod.yfcorelib.api.interop;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * {@link CapabilityRegistry} 的默认实现：并发安全、注册后不可替换。
 * <p>选择"不可替换"而不是"后注册者覆盖"：能力注册发生在启动阶段，同一 id 被注册两次
 * 几乎一定是两个模组都以为自己是唯一提供者，此时静默覆盖只会把问题推到运行期。</p>
 */
public final class SimpleCapabilityRegistry implements CapabilityRegistry {
	private record Entry<T>(Class<T> type, T provider) {
	}

	private final ConcurrentMap<String, Entry<?>> entries = new ConcurrentHashMap<>();

	@Override
	public <T> void register(CapabilityKey<T> key, T provider) {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(provider, "provider");
		if (!key.type().isInstance(provider)) {
			throw new IllegalArgumentException("能力 " + key.id() + " 声明类型为 "
					+ key.type().getName() + "，实际实现是 " + provider.getClass().getName());
		}
		Entry<T> entry = new Entry<>(key.type(), provider);
		Entry<?> previous = entries.putIfAbsent(key.id(), entry);
		if (previous != null) {
			throw new IllegalStateException("能力 " + key.id() + " 已经被 "
					+ previous.provider().getClass().getName() + " 注册，拒绝重复注册");
		}
	}

	@Override
	public <T> Optional<T> get(CapabilityKey<T> key) {
		Objects.requireNonNull(key, "key");
		Entry<?> entry = entries.get(key.id());
		if (entry == null) {
			return Optional.empty();
		}
		if (entry.type() != key.type()) {
			throw new IllegalArgumentException("能力 " + key.id() + " 注册的类型为 "
					+ entry.type().getName() + "，查询用的 key 声明为 " + key.type().getName());
		}
		return Optional.of(key.type().cast(entry.provider()));
	}

	@Override
	public boolean isRegistered(CapabilityKey<?> key) {
		Objects.requireNonNull(key, "key");
		return entries.containsKey(key.id());
	}

	@Override
	public Set<String> ids() {
		return Set.copyOf(entries.keySet());
	}
}
