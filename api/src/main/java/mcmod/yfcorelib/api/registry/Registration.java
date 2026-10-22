package mcmod.yfcorelib.api.registry;

import java.util.Objects;
import java.util.function.Supplier;

/** 注册结果句柄；当前直接持有值，为延迟求值预留扩展位 */
public final class Registration<T> implements Supplier<T> {
	private final String id;
	private final T value;

	public Registration(String id, T value) {
		this.id = Objects.requireNonNull(id, "id");
		this.value = Objects.requireNonNull(value, "value");
	}

	public String id() {
		return id;
	}

	@Override
	public T get() {
		return value;
	}

	/** 直接持有值恒为 true，为延迟注册预留 */
	public boolean isPresent() {
		return value != null;
	}
}
