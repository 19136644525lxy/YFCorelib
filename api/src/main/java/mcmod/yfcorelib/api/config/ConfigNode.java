package mcmod.yfcorelib.api.config;

import java.util.Optional;
import java.util.Set;

/**
 * 配置节点抽象：点分路径读写，供迁移器与合并器操作底层树。
 * <p>实现层用 Gson {@code JsonObject}，接口不泄漏 JSON 类型。</p>
 *
 * <p><strong>为什么提供带类型的读写</strong>：迁移器常常要搬动数字/布尔值。
 * 如果只有字符串读写，把 {@code true} 读成 {@code "true"} 再写回去就变成了字符串，
 * 下次加载会因"类型不符"被默认值替换——一个看起来成功、实际丢配置的迁移。
 * 所以每种受支持的标量类型都必须有对应的读写方法。</p>
 */
public interface ConfigNode {
	/** 指定路径是否存在值（JSON null 不算有值） */
	boolean has(String path);

	/** 读取字符串；路径不存在或类型不是字符串时返回 empty */
	Optional<String> getString(String path);

	void setString(String path, String value);

	/** 读取数值（统一按 double 返回）；路径不存在或类型不是数字时返回 empty */
	Optional<Double> getNumber(String path);

	void setNumber(String path, Number value);

	/** 读取布尔；路径不存在或类型不是布尔时返回 empty */
	Optional<Boolean> getBoolean(String path);

	void setBoolean(String path, boolean value);

	/** 顶层键集合（不含嵌套展开） */
	Set<String> keys();

	/** 进入子节点；不存在时创建中间结构 */
	ConfigNode child(String path);
}
