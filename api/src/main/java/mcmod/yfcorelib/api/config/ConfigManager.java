package mcmod.yfcorelib.api.config;

/** 配置管理器：合并默认值、执行迁移、类型安全读写 */
public interface ConfigManager {
	/** 读取键当前值；未加载或类型不符时返回默认值 */
	<T> T get(ConfigKey<T> key);

	/** 写入键值（内存树），等待 save 落盘 */
	<T> void set(ConfigKey<T> key, T value);

	/** 重新从来源加载并合并；返回是否成功 */
	boolean reload();

	/** 把内存树写回来源 */
	void save();

	/** 当前已加载的版本号（迁移后为目标版本） */
	int loadedVersion();
}
