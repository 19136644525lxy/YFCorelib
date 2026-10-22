package mcmod.yfcorelib.api.config;

import mcmod.yfcorelib.api.data.AtomicFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
		try {
			return Optional.ofNullable(Files.readString(file(name), StandardCharsets.UTF_8));
		} catch (IOException e) {
			return Optional.empty();
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
