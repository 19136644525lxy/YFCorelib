package mcmod.yfcorelib.api.platform;

import java.util.Optional;
import java.util.ServiceLoader;

/** ServiceLoader 门面：统一发现平台实现，业务代码禁止直接接触加载器 API。 */
public final class Services {
	private static volatile Platform platform;

	private Services() {
	}

	/** 获取当前平台实现，首次调用时通过 ServiceLoader 发现并缓存。 */
	public static Platform platform() {
		Platform p = platform;
		if (p == null) {
			synchronized (Services.class) {
				if (platform == null) {
					platform = load(PlatformProvider.class)
							.orElseThrow(() -> new IllegalStateException(
									"未找到 PlatformProvider 实现：请确认平台模块已注册 META-INF/services"))
							.platform();
				}
				p = platform;
			}
		}
		return p;
	}

	/**
	 * 按类型发现可选服务。
	 * <p>显式使用服务接口自身的类加载器，而非线程上下文类加载器（TCCL）：
	 * 服务实现与接口同处模组类加载器，TCCL 在部分线程（如 Netty 工作线程）下看不到模组类。</p>
	 */
	public static <T> Optional<T> load(Class<T> service) {
		return ServiceLoader.load(service, service.getClassLoader()).findFirst();
	}

	/**
	 * 丢弃已缓存的平台实例，下次 {@link #platform()} 重新发现。
	 * <p>供单测与热重载使用；生产环境调用只会多一次 ServiceLoader 扫描。</p>
	 */
	public static void reset() {
		synchronized (Services.class) {
			platform = null;
		}
	}
}
