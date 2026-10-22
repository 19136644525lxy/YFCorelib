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

	/** 按类型发现可选服务 */
	public static <T> Optional<T> load(Class<T> service) {
		return ServiceLoader.load(service).findFirst();
	}
}
