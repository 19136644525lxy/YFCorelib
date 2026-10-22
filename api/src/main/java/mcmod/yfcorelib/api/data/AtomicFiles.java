package mcmod.yfcorelib.api.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 文件原子写与损坏回退工具。
 * <p>写入顺序：先备份旧文件为 {@code .bak}，再写临时文件后原子移动，
 * 任意时刻中断都能从主文件或 .bak 恢复到上一个成功版本。</p>
 */
public final class AtomicFiles {
	private static final Logger LOG = LoggerFactory.getLogger(AtomicFiles.class);
	private static final String BACKUP_SUFFIX = ".bak";
	private static final String TMP_SUFFIX = ".tmp";

	private AtomicFiles() {
	}

	/** 原子写入；失败返回 false 不抛异常（调用方决定是否兜底） */
	public static boolean writeAtomic(Path target, String content) {
		Path tmp = target.resolveSibling(target.getFileName() + TMP_SUFFIX);
		try {
			ensureParent(target);
			Files.writeString(tmp, content, StandardCharsets.UTF_8);
			move(tmp, target);
			return true;
		} catch (IOException e) {
			LOG.error("原子写入失败: {}", target, e);
			cleanupQuietly(tmp);
			return false;
		}
	}

	/** 备份旧文件后原子写入；无旧文件时直接写 */
	public static boolean backupThenWrite(Path target, String content) {
		Path backup = target.resolveSibling(target.getFileName() + BACKUP_SUFFIX);
		try {
			if (Files.exists(target)) {
				Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			LOG.warn("备份失败，跳过备份继续写入: {}", target, e);
		}
		return writeAtomic(target, content);
	}

	/** 读取内容；主文件失败时回退 .bak；两者都不可用返回 null */
	public static String readWithFallback(Path target) {
		String main = readQuietly(target);
		if (main != null) {
			return main;
		}
		String backup = readQuietly(target.resolveSibling(target.getFileName() + BACKUP_SUFFIX));
		if (backup != null) {
			LOG.warn("主文件不可用，已从备份恢复: {}", target);
			return backup;
		}
		return null;
	}

	private static String readQuietly(Path path) {
		try {
			return Files.readString(path, StandardCharsets.UTF_8);
		} catch (IOException e) {
			return null;
		}
	}

	/** 读取备份文件内容；不可用返回 null */
	public static String readBackup(Path target) {
		return readQuietly(target.resolveSibling(target.getFileName() + BACKUP_SUFFIX));
	}

	private static void move(Path from, Path to) throws IOException {
		try {
			Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException atomicFailed) {
			// 部分文件系统不支持原子移动，回退普通移动
			Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void ensureParent(Path target) throws IOException {
		Path parent = target.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
	}

	private static void cleanupQuietly(Path tmp) {
		try {
			Files.deleteIfExists(tmp);
		} catch (IOException ignored) {
			// 清理失败无影响
		}
	}
}
