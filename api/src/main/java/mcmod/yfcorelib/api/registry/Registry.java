package mcmod.yfcorelib.api.registry;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 通用注册中心：与 MC 原生注册表解耦，用于模组内自定义内容索引。
 * <p>线程安全；重复 id 立即抛错；冻结后拒绝写入；遍历按注册顺序。</p>
 */
public interface Registry<T> {
	/** 注册新条目，id 重复或已冻结时抛 IllegalStateException */
	Registration<T> register(String id, T value);

	/** 为已注册条目打标签（同一 id 可多标签），冻结或 id 不存在时抛错 */
	void tag(String id, String tag);

	Optional<T> get(String id);

	/** 不存在时抛 NoSuchElementException */
	T getOrThrow(String id);

	/** 按注册顺序返回全部值 */
	List<T> values();

	/** 返回带指定标签的全部值，按注册顺序 */
	List<T> byTag(String tag);

	/** 按注册顺序返回全部 id */
	Set<String> ids();

	void freeze();

	boolean isFrozen();
}
