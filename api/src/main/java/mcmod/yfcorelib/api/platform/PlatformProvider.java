package mcmod.yfcorelib.api.platform;

/** 平台实现的服务发现接口，由 fabric/forge 模块通过 META-INF/services 注册。 */
public interface PlatformProvider {
	Platform platform();
}
