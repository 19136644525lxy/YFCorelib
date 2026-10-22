package mcmod.yfcorelib.api.event;

import java.util.UUID;

/** 玩家引用：api 层不持有 MC 玩家对象，由平台模块解析 */
public interface PlayerRef {
	UUID id();

	String name();

	boolean isOnline();
}
