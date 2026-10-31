package mcmod.yfcorelib.api.data;

import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JSON 文件版玩家数据仓库：每玩家一个 {@code <uuid>.json} 文件。
 * <ul>
 *   <li>内存缓存 {@link ConcurrentHashMap}，读取命中不打磁盘</li>
 *   <li>读取失败/损坏：跳过该条目并记录 warn，不崩溃</li>
 *   <li>{@code unload} 在玩家离线时保存并清缓存，防内存泄漏</li>
 * </ul>
 * <p>选择文件而非 Attachment/Capability：验收要求离线可读，文件天然满足。</p>
 */
public final class JsonPlayerDataStore<T> implements PlayerDataStore<T> {
	private static final Logger LOG = LoggerFactory.getLogger(JsonPlayerDataStore.class);

	private final Path dir;
	private final DataCodec<T> codec;
	private final ConcurrentHashMap<UUID, T> cache = new ConcurrentHashMap<>();
	private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();

	public JsonPlayerDataStore(Path dir, DataCodec<T> codec) {
		this.dir = dir;
		this.codec = codec;
	}

	@Override
	public T get(UUID player) {
		T cached = cache.get(player);
		if (cached != null) {
			return cached;
		}
		// 不在 computeIfAbsent 里做磁盘 IO：CHM 的桶锁会被整段 IO 占住，
		// 同桶其他玩家（甚至同一玩家的其他线程）全部阻塞
		T loaded = readFromDisk(player);
		if (loaded == null) {
			return null;
		}
		T prev = cache.putIfAbsent(player, loaded);
		return prev != null ? prev : loaded;
	}

	@Override
	public void set(UUID player, T value) {
		cache.put(player, value);
		dirty.add(player);
	}

	@Override
	public void remove(UUID player) {
		cache.remove(player);
		dirty.remove(player);
		try {
			Files.deleteIfExists(file(player));
		} catch (IOException e) {
			LOG.warn("玩家数据文件删除失败: {}", player, e);
		}
	}

	@Override
	public void save(UUID player) {
		T value = cache.get(player);
		if (value == null) {
			return;
		}
		if (AtomicFiles.writeAtomic(file(player), GsonIo.toJson(codec.encode(value)))) {
			// 写盘期间可能被 set 成新值：此时不能清 dirty，否则新值永远不会落盘
			if (cache.get(player) == value) {
				dirty.remove(player);
			}
		}
	}

	@Override
	public void saveAll() {
		for (UUID player : Set.copyOf(dirty)) {
			try {
				save(player);
			} catch (RuntimeException e) {
				// 单条失败不影响整体保存
				LOG.error("玩家数据保存失败: {}", player, e);
			}
		}
	}

	@Override
	public void unload(UUID player) {
		if (dirty.contains(player)) {
			try {
				save(player);
			} catch (RuntimeException e) {
				LOG.error("玩家数据保存失败，保留缓存以免丢数据: {}", player, e);
				return;
			}
			if (dirty.contains(player)) {
				// writeAtomic 返回 false 时 save 不会清 dirty：同样不能丢内存
				LOG.error("玩家数据落盘失败，保留缓存以免丢数据: {}", player);
				return;
			}
		}
		cache.remove(player);
		dirty.remove(player);
	}

	@Override
	public DataCodec<T> codec() {
		return codec;
	}

	/** 缓存当前命中数量（监控用） */
	public int cachedCount() {
		return cache.size();
	}

	private T readFromDisk(UUID player) {
		Path file = file(player);
		if (!Files.exists(file)) {
			return null;
		}
		try {
			String content = Files.readString(file, StandardCharsets.UTF_8);
			if (content.isBlank()) {
				return null;
			}
			return codec.decode(JsonParser.parseString(content));
		} catch (IOException | RuntimeException e) {
			// 损坏条目跳过并记录，不中断整体
			LOG.warn("玩家数据损坏，已跳过: {} ({})", file, e.toString());
			return null;
		}
	}

	private Path file(UUID player) {
		return dir.resolve(player + ".json");
	}
}
