package mcmod.yfcorelib.api.config;

import java.util.List;

/** 配置契约：文件名、目标版本与全部键定义（不可变） */
public interface ConfigSpec {
	/** 配置文件名（不含扩展名） */
	String name();

	/** 目标版本号，低于该值时触发迁移 */
	int version();

	/** 全部键定义，顺序稳定 */
	List<ConfigKey<?>> keys();
}
