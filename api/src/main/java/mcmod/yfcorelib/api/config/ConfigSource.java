package mcmod.yfcorelib.api.config;

import java.util.Optional;

/** 配置来源抽象：按名称读写文本内容，隔离文件系统/内存实现 */
public interface ConfigSource {
	/** 读取指定名称的内容；不存在返回 empty */
	Optional<String> read(String name);

	/** 原子写入指定名称的内容 */
	void write(String name, String content);
}
