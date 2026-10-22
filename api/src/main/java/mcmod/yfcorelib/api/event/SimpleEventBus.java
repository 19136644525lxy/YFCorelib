package mcmod.yfcorelib.api.event;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** EventBus 默认实现：每类事件一条 COW 列表，注册/退订与投递并发安全 */
public final class SimpleEventBus implements EventBus {
	private static final Logger LOG = Logger.getLogger(SimpleEventBus.class.getName());

	private final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<Consumer<?>>> listeners = new ConcurrentHashMap<>();

	@Override
	public <E> Subscription register(Class<E> type, Consumer<E> listener) {
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(listener, "listener");
		listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(listener);
		return () -> {
			CopyOnWriteArrayList<Consumer<?>> list = listeners.get(type);
			if (list != null) {
				list.remove(listener);
			}
		};
	}

	@Override
	@SuppressWarnings("unchecked")
	public <E> void post(E event) {
		Objects.requireNonNull(event, "event");
		CopyOnWriteArrayList<Consumer<?>> list = listeners.get(event.getClass());
		if (list == null) {
			return;
		}
		for (Consumer<?> raw : list) {
			try {
				((Consumer<E>) raw).accept(event);
			} catch (Throwable ex) {
				LOG.log(Level.SEVERE, "事件监听器异常 event=" + event.getClass().getName(), ex);
			}
		}
	}
}
