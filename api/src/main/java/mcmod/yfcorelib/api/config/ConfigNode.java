package mcmod.yfcorelib.api.config;

import java.util.Optional;
import java.util.Set;

/**
 * 配置节点抽象：点分路径读写，供迁移器与合并器操作底层树。
 * <p>实现层用 Gson {@code JsonObject}，接口不泄漏 JSON 类型。</p>
 */
public interface ConfigNode {
	/** 指定路径是否存在值 */
	boolean has(String path);

	Optional<String> getString(String path);

	void setString(String path, String value);

	/** 顶层键集合（不含嵌套展开） */
	Set<String> keys();

	/** 进入子节点；不存在时创建中间结构 */
	ConfigNode child(String path);
}
