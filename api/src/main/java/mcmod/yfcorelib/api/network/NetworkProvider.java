package mcmod.yfcorelib.api.network;

/** 网络通道的服务发现接口，由 fabric/forge 模块通过 META-INF/services 注册 */
public interface NetworkProvider {
	/** 按名获取通道；名称必须为 "命名空间:路径" 形式，同名重复调用返回同一实例 */
	NetworkChannel channel(String name);
}
