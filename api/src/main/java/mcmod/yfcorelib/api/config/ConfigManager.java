package mcmod.yfcorelib.api.config;

/** 配置管理器：合并默认值、执行迁移、类型安全读写 */
public interface ConfigManager {
	/** 读取键当前值；类型不符时回退默认值，尚未成功加载时抛 IllegalStateException */
	<T> T get(ConfigKey<T> key);

	/** 写入键值（内存树），等待 save 落盘 */
	<T> void set(ConfigKey<T> key, T value);

	/** 重新从来源加载并合并；返回是否成功。失败后内存退回默认值且禁止 save */
	boolean reload();

	/**
	 * 把内存树写回来源。
	 *
	 * @throws IllegalStateException 上次 {@link #reload()} 失败：此时内存树不代表文件真实内容，
	 *                               写回会覆盖用户配置，故拒绝执行
	 */
	void save();

	/** 当前已加载的版本号（迁移后为目标版本） */
	int loadedVersion();
}
