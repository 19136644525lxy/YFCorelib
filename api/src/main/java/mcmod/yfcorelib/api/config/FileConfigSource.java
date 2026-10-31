package mcmod.yfcorelib.api.config;

import mcmod.yfcorelib.api.data.AtomicFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;

/** 文件系统配置来源：目录 + {@code <name>.json} 文件 */
public final class FileConfigSource implements ConfigSource {
	private final Path dir;

	public FileConfigSource(Path dir) {
		this.dir = dir;
	}

	@Override
	public Optional<String> read(String name) {
		Path file = file(name);
		try {
			return Optional.ofNullable(Files.readString(file, StandardCharsets.UTF_8));
		} catch (NoSuchFileException e) {
			// 只有"文件确实不存在"才等价于没有配置
			return Optional.empty();
		} catch (IOException e) {
			// 读失败必须与"不存在"区分开：若当成不存在，上层会用默认值覆盖用户文件
			throw new IllegalStateException("配置读取失败: " + file, e);
		}
	}

	@Override
	public void write(String name, String content) {
		AtomicFiles.writeAtomic(file(name), content);
	}

	private Path file(String name) {
		return dir.resolve(name + ".json");
	}
}
