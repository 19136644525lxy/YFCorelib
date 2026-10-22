package mcmod.yfcorelib.api.config;

/** 版本迁移器：把配置从 fromVersion 升级到 toVersion，只执行一次后回写新版本号 */
public interface ConfigMigrator {
	int fromVersion();

	int toVersion();

	/** 就地修改节点；实现不得假设目标版本键存在 */
	void migrate(ConfigNode node);
}
