package mcmod.yfcorelib.api.sync;

import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * 服务端-客户端快照同步引擎：服务器侧 publish，客户端侧 subscribe。
 * <p>三种发送时机独立开关：登录时（syncOnLogin）、内容变化时（syncOnChange）、周期重发（syncEveryTicks）。</p>
 */
public interface SyncEngine<S> {
	/** 服务器侧：为玩家发布最新快照（内容比较后按开关决定是否立即发送） */
	void publish(UUID playerId, S snapshot);

	/** 服务器侧：读取玩家当前快照，未发布过返回 null */
	S snapshot(UUID playerId);

	/** 客户端侧：订阅收到的快照；关闭返回值取消订阅 */
	AutoCloseable subscribe(BiConsumer<UUID, S> listener);

	/** 客户端侧：平台桥收到快照后调用，分发给监听器 */
	void receive(UUID playerId, S snapshot);

	/** 服务器侧：玩家加入，按 syncOnLogin 开关重发快照（供加入事件驱动） */
	void onJoin(UUID playerId);

	/** 服务器侧：玩家离开，清理该玩家缓存防泄漏 */
	void onLeave(UUID playerId);

	/** 服务器侧：每刻驱动，按 syncEveryTicks 周期重发全部快照 */
	void tick();

	SyncEngine<S> syncOnLogin(boolean value);

	SyncEngine<S> syncOnChange(boolean value);

	SyncEngine<S> syncEveryTicks(int value);

	boolean syncOnLogin();

	boolean syncOnChange();

	int syncEveryTicks();
}
