package mcmod.yfcorelib.api.data;

import java.util.Set;
import java.util.UUID;

/**
 * 玩家会话数据：仅存活于本次在线期间，零磁盘 IO。
 * <p>与 {@link PlayerDataStore} 的分工：需要跨重启持久化用前者；临时战斗/冷却/UI 状态用本接口。</p>
 * <p>玩家离线自动清理，避免 UUID 缓存无限增长。</p>
 */
public interface PlayerSessionStore<T> {
	/** 读取会话数据；不存在返回 null */
	T get(UUID player);

	/** 读取，不存在则用工厂创建并写入 */
	T getOrCreate(UUID player);

	void set(UUID player, T value);

	boolean has(UUID player);

	void remove(UUID player);

	/** 当前在线玩家 id 快照 */
	Set<UUID> online();

	int size();

	/** 清空全部会话数据（停服兜底） */
	void clear();
}
