package mcmod.yfcorelib.api.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * JSON 文件版世界数据仓库：单文件 {@code {key: value}} 映射。
 * <ul>
 *   <li>保存顺序：先备份旧文件为 .bak，再临时文件 + 原子移动；中断后可回退</li>
 *   <li>加载损坏：自动尝试 .bak 回退，仍失败则空表启动（不崩溃）</li>
 *   <li>懒加载：首次 get/save 时读盘</li>
 * </ul>
 * <p>不用 SavedData：离线/独立进程也要可读，纯文件后端可单测；SavedData 适配推迟。</p>
 */
public final class JsonWorldDataStore<T> implements WorldDataStore<T> {
	private static final Logger LOG = LoggerFactory.getLogger(JsonWorldDataStore.class);

	private final java.nio.file.Path file;
	private final DataCodec<T> codec;
	private final Object lock = new Object();

	private Map<String, JsonElement> data;
	private boolean dirty;

	public JsonWorldDataStore(java.nio.file.Path file, DataCodec<T> codec) {
		this.file = file;
		this.codec = codec;
	}

	@Override
	public Optional<T> get(String key) {
		synchronized (lock) {
			loadIfNeeded();
			JsonElement element = data.get(key);
			if (element == null) {
				return Optional.empty();
			}
			try {
				return Optional.of(codec.decode(element));
			} catch (RuntimeException e) {
				LOG.warn("世界数据条目损坏，已跳过: {} ({})", key, e.toString());
				return Optional.empty();
			}
		}
	}

	@Override
	public void set(String key, T value) {
		synchronized (lock) {
			loadIfNeeded();
			data.put(key, codec.encode(value));
			dirty = true;
		}
	}

	@Override
	public void remove(String key) {
		synchronized (lock) {
			loadIfNeeded();
			if (data.remove(key) != null) {
				dirty = true;
			}
		}
	}

	@Override
	public void save() {
		synchronized (lock) {
			loadIfNeeded();
			JsonObject out = new JsonObject();
			// LinkedHashMap 保持插入顺序，序列化稳定可 diff
			data.forEach(out::add);
			if (AtomicFiles.backupThenWrite(file, GsonIo.toJson(out))) {
				dirty = false;
			}
		}
	}

	/** 是否存在未落盘修改 */
	public boolean isDirty() {
		synchronized (lock) {
			return dirty;
		}
	}

	@Override
	public DataCodec<T> codec() {
		return codec;
	}

	private void loadIfNeeded() {
		if (data != null) {
			return;
		}
		data = new LinkedHashMap<>();
		String content = AtomicFiles.readWithFallback(file);
		if (content == null || content.isBlank()) {
			return;
		}
		if (tryFill(content)) {
			return;
		}
		// 主文件内容损坏（中断截断的典型形态）：尝试从 .bak 恢复
		String backup = AtomicFiles.readBackup(file);
		if (backup != null && tryFill(backup)) {
			LOG.warn("世界数据主文件内容损坏，已从 .bak 恢复: {}", file);
			dirty = true;
		} else {
			LOG.warn("世界数据损坏（含备份），已视为空表: {}", file);
		}
	}

	/** 解析并填充数据表；返回是否为合法 JSON 对象 */
	private boolean tryFill(String content) {
		try {
			JsonElement parsed = JsonParser.parseString(content);
			if (!parsed.isJsonObject()) {
				return false;
			}
			Map<String, JsonElement> filled = new LinkedHashMap<>();
			parsed.getAsJsonObject().entrySet().forEach(entry -> filled.put(entry.getKey(), entry.getValue()));
			data = filled;
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** 当前键集合（监控用） */
	public Set<String> keys() {
		synchronized (lock) {
			loadIfNeeded();
			return Set.copyOf(data.keySet());
		}
	}
}
