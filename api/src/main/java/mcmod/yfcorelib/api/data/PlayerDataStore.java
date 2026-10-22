package mcmod.yfcorelib.api.data;

import java.util.UUID;

/**
 * 玩家数据仓库：内存缓存 + 每玩家一份 JSON 文件，离线/重启/维度切换后仍可读。
 * <p>未读取时返回 {@code null}；玩家离线时调用 {@link #unload(UUID)} 保存并释放缓存防泄漏。</p>
 */
public interface PlayerDataStore<T> {
	/** 读取玩家数据；无数据或损坏时返回 null（损坏条目跳过并记录） */
	T get(UUID player);

	/** 写入玩家数据（内存），等待 save 落盘 */
	void set(UUID player, T value);

	/** 读取或返回默认值（预留：降低业务判空成本） */
	default T getOrDefault(UUID player, T fallback) {
		T value = get(player);
		return value == null ? fallback : value;
	}

	/** 删除玩家数据（内存与磁盘） */
	void remove(UUID player);

	/** 把指定玩家数据写盘 */
	void save(UUID player);

	/** 保存全部脏数据；单条失败不影响整体 */
	void saveAll();

	/** 玩家离线时调用：保存后清缓存（预留：防内存泄漏） */
	void unload(UUID player);

	DataCodec<T> codec();
}
