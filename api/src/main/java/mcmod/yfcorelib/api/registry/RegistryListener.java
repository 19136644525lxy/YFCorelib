package mcmod.yfcorelib.api.registry;

/** 注册表监听：按需覆盖；回调异常会被捕获记录，不中断注册流程 */
public interface RegistryListener<T> {
	default void onRegister(String id, T value) {
	}

	default void onFreeze() {
	}
}
