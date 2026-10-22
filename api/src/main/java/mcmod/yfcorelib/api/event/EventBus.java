package mcmod.yfcorelib.api.event;

import java.util.function.Consumer;

/**
 * 轻量事件总线：按事件精确类型分发，监听器按注册顺序同步执行。
 * <p>线程安全；监听器异常被捕获记录，不阻断后续监听器。</p>
 */
public interface EventBus {
	<E> Subscription register(Class<E> type, Consumer<E> listener);

	<E> void post(E event);

	/** 订阅句柄：close 后不再收到事件，可重复 close */
	interface Subscription extends AutoCloseable {
		@Override
		void close();
	}
}
