package mcmod.yfcorelib.api.data;

import java.util.Optional;

/** 世界数据仓库：键值型共享数据，原子写入 + 损坏 .bak 回退 */
public interface WorldDataStore<T> {
	/** 读取键值；不存在返回 empty，损坏条目跳过并记录 */
	Optional<T> get(String key);

	/** 写入键值（内存），等待 save 落盘 */
	void set(String key, T value);

	/** 删除键值 */
	void remove(String key);

	/** 全量落盘（原子写 + .bak 备份） */
	void save();

	DataCodec<T> codec();
}
