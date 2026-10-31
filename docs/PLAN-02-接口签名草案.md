# PLAN-02 接口签名草案

> 状态：草案，未实现。用于写代码前冻结契约。
> 约束：`api` 包内不得出现 `net.minecraft.*` / 加载器类型；事件与数据只带 `UUID`、`String`、`JsonElement`、`Path`、原始类型。
> 标注：`[api]` 纯 Java；`[common]` 允许 MC；`[platform]` 加载器相关。

## 1. 门面 `[api]`

```java
public final class YFCoreLib {
    public static final String MOD_ID = "yfcorelib";
    public static Platform platform();
    public static EventBus events();
    public static Scheduler scheduler(Side side);
    public static NetworkChannel network();
    public static TextMeasure text();
}

public enum Side { COMMON, CLIENT, SERVER }

public final class Ids {
    public static String id(String path);                 // "yfcorelib:path"
    public static String id(String namespace, String path);
    public static String translation(String key);          // "yfcorelib.<key>"
    public static String texture(String path);             // "yfcorelib:textures/<path>.png"
    public static String model(String path);
    public static String lang(String locale);              // "assets/yfcorelib/lang/<locale>.json"
}
```

## 2. 平台抽象 `[api]` / `[platform]`

```java
public interface Platform {
    String loaderName();          // "fabric" | "forge" | "neoforge"
    String mcVersion();
    String loaderVersion();
    boolean isClient();
    boolean isDevelopment();
    Path gameDir();
    Path configDir();
    Path modsDir();
    boolean isModLoaded(String modId);
    Optional<ModInfo> modInfo(String modId);
}
public record ModInfo(String modId, String version, String name, Optional<String> description) {}
public interface PlatformProvider { Platform platform(); }
public final class Services {
    public static Platform platform();
    public static <T> Optional<T> load(Class<T> service);
}
```

1. `Services` 通过 `ServiceLoader` 发现 `PlatformProvider`，平台模块在 `META-INF/services` 注册。
2. 业务代码只调 `YFCoreLib.platform()` / `Services.platform()`。
3. `FabricPlatform` / `ForgePlatform` 不向 `common` 泄漏加载器类型。

## 3. 配置 `[api]`

```java
public interface ConfigSpec {
    String name();
    int version();
    List<ConfigKey<?>> keys();
}
public final class ConfigKey<T> {
    public String path();              // "world.temperature.enabled"
    public Class<T> type();
    public T defaultValue();
    public Optional<String> comment();
    public Optional<T> range();
}
public final class ConfigBuilder {
    public <T> ConfigKey<T> define(String path, Class<T> type, T defaultValue);
    public ConfigBuilder comment(String path, String comment);
    public ConfigBuilder version(int version);
    public ConfigSpec build(String name);
}
public interface ConfigManager {
    <T> T get(ConfigKey<T> key);
    <T> void set(ConfigKey<T> key, T value);
    boolean reload();
    void save();
    int loadedVersion();
}
public interface ConfigMigrator {
    int fromVersion();
    int toVersion();
    void migrate(ConfigNode node);
}
public interface ConfigNode {
    boolean has(String path);
    Optional<String> getString(String path);
    void setString(String path, String value);
    Set<String> keys();
    ConfigNode child(String path);
}
public interface ConfigSource {                // JSON/Properties 后端
    Optional<String> read(String name);
    void write(String name, String content);
}
```

行为约定（来自 `PdopnConfig` 已验证逻辑）：以文件实际内容补默认键、绝不覆盖用户已改值、保留未知键、类型不符整体替换、版本号驱动迁移与回写。

## 4. 注册中心 `[api]`

```java
public interface Registry<T> {
    Registration<T> register(String id, T value);
    Optional<T> get(String id);
    T getOrThrow(String id);
    List<T> values();
    List<T> byTag(String tag);
    Set<String> ids();
    void freeze();
    boolean isFrozen();
}
public final class Registration<T> implements Supplier<T> {
    public String id();
    public T get();
    public boolean isPresent();
}
public interface RegistryListener<T> {
    void onRegister(String id, T value);
    void onFreeze();
}
```

要求：线程安全、冲突检测、冻结后拒绝写入、按注册顺序稳定遍历。

## 5. 调度 `[api]`

```java
public interface Scheduler {
    ScheduledTask delay(int ticks, Runnable task);
    ScheduledTask repeat(int periodTicks, Runnable task);
    ScheduledTask repeat(int delayTicks, int periodTicks, Runnable task);
    void cancelAll(Object owner);
    void tick();                      // 平台模块驱动，业务不调用
}
public interface ScheduledTask {
    boolean cancel();
    boolean isCancelled();
}
public interface TimerStore {         // 跨重启计时器
    long remaining(String key);
    void set(String key, long ticks);
    void clear(String key);
    Set<String> keys();
}
public interface Tickable {
    void tick();
    boolean isDone();
}
```

## 6. 事件与生命周期 `[api]`

```java
public interface EventBus {
    <E> Subscription register(Class<E> type, Consumer<E> listener);
    <E> void post(E event);
    interface Subscription extends AutoCloseable { void close(); }
}
public interface PlayerRef {
    UUID id();
    String name();
    boolean isOnline();
}
public record PlayerJoinEvent(PlayerRef player) {}
public record PlayerLeaveEvent(PlayerRef player) {}
public record PlayerRespawnEvent(PlayerRef player, boolean endPortal) {}
public record PlayerDimensionChangeEvent(PlayerRef player, String from, String to) {}
public record ServerStartedEvent() {}
public record ServerStoppingEvent() {}
public record ServerTickEvent(Side side) {}
public interface LifecycleHook {      // 延迟加载队列
    void onPlayerReady(PlayerRef player);
}
```

要求：Mixin 只 `post` 事件，业务只 `register`；订阅必须可关闭；容器并发安全。

## 7. 持久化 `[api]`

```java
public interface DataCodec<T> {
    JsonElement encode(T value);
    T decode(JsonElement json);
}
public interface PlayerDataStore<T> {
    T get(UUID player);
    void set(UUID player, T value);
    void remove(UUID player);
    void save(UUID player);
    void saveAll();
    DataCodec<T> codec();
}
public interface WorldDataStore<T> {
    Optional<T> get(String key);
    void set(String key, T value);
    void remove(String key);
    void save();
    DataCodec<T> codec();
}
public interface SyncChannel<T> {
    void markDirty();
    void syncTo(UUID player);
    void syncAll();
    T snapshot();
    void apply(JsonElement snapshot);
}
```

1. 平台层用 Fabric Attachment / Forge Capability 实现 `PlayerDataStore`。
2. 世界数据统一 `SavedData` 适配；JSON/NBT 双后端由 `DataCodec` 决定。
3. 写入做异常兜底，单条失败不影响整体保存。

## 8. 网络 `[api]`

```java
public interface Packet {}
public record PacketType<T extends Packet>(
        String id,
        Side receiver,
        DataCodec<T> codec,
        PacketHandler<T> handler) {}
public interface PacketHandler<T extends Packet> {
    void handle(T packet, PacketContext context);
}
public interface PacketContext {
    UUID sender();
    Side side();
    void reply(Packet packet);
}
public interface NetworkChannel {
    <T extends Packet> void register(PacketType<T> type);
    void sendToServer(Packet packet);
    void sendToPlayer(UUID player, Packet packet);
    void broadcast(Packet packet);
    void sendToNear(String dimension, double x, double y, double z, double radius, Packet packet);
    boolean isRegistered(String id);
}
```

1. 包 id 必带命名空间，注册冲突立即抛错。
2. 序列化走 `DataCodec`，业务字段不直接依赖 NBT 类。
3. 发送端限流；Netty 线程不得直接改世界状态（切主线程）。

## 9. 同步引擎 `[api]`

```java
public interface SyncEngine<S> {
    S snapshot();
    void publish(S state);
    Subscription subscribe(Consumer<S> listener);
    void syncOnLogin(boolean enabled);
    void syncEveryTicks(int ticks);
    void syncOnChange(boolean enabled);
    interface Subscription extends AutoCloseable { void close(); }
}
```

来源：`Calendar` 的 `AutoSyncScheduler` + `CalendarClientCache` 模式。

## 10. Mixin 工具 `[api]`

```java
public final class MixinCallbacks {
    public static <E> AutoCloseable register(Class<E> type, Consumer<E> callback);
    public static <E> void fire(E event);
}
public interface AccessHelper {
    FieldAccess field(Class<?> owner, String name);
    MethodAccess method(Class<?> owner, String name, Class<?>... parameterTypes);
}
public interface FieldAccess {
    Object get(Object target);
    void set(Object target, Object value);
    Class<?> type();
}
public interface MethodAccess {
    Object invoke(Object target, Object... args);
    Class<?> returnType();
}
public interface MixinConfigValidator {
    ValidationResult validate(String configResource);
}
```

1. 访问失败必须抛异常/记录 error，禁止静默返回默认值。
2. 优先 `@Shadow` / AccessTransformer，反射只走 `AccessHelper`。
3. 校验器检查：json 存在、package 与源码一致、注册类存在、client 配置不混入主配置。

## 11. 文本 `[api]`

```java
public interface TextMeasure {
    int width(String text);
    int height(String text);
    List<TextLine> wrap(String text, int maxWidth);
    String truncate(String text, int maxWidth, String ellipsis);
    void clearCache();
}
public record TextLine(String text, int width, int startIndex, int endIndex) {}
```

必须使用真实字体度量；支持 CJK 全角；带缓存与失效入口。

## 12. 数学与随机 `[api]`

```java
public enum Ease { LINEAR, IN_QUAD, OUT_QUAD, IN_OUT_QUAD, IN_CUBIC, OUT_CUBIC, IN_OUT_CUBIC, OUT_BACK, IN_OUT_SINE }
public final class Easing {
    public static float apply(Ease ease, float t);
}
public final class Color {
    public static int argb(int a, int r, int g, int b);
    public static int alpha(int argb);
    public static int red(int argb);
    public static int green(int argb);
    public static int blue(int argb);
    public static int lerp(int from, int to, float t);
    public static int withAlpha(int argb, int alpha);
    public static int fromHex(String hex);
}
public interface Rng {
    int nextInt(int bound);
    float nextFloat();
    double nextDouble();
    long nextLong();
}
public final class WeightedRandom {
    public static <T> T pick(Rng rng, List<Entry<T>> entries);
    public static <T> Optional<T> pickOptional(Rng rng, List<Entry<T>> entries);
    public record Entry<T>(T value, double weight) {}
}
public final class Noise {
    public static double perlin(double x, double y, double z, long seed);
}
```

## 13. 反射 `[api]`

```java
public final class ReflectionCache {
    public static <T> T getField(Object target, String name);
    public static void setField(Object target, String name, Object value);
    public static Object invoke(Object target, String name, Object... args);
    public static Object instantiate(Class<?> type, Class<?>[] parameterTypes, Object... args);
    public static void clearCache();
}
```

失败必须抛出 `ReflectionException` 并记录 owner/name。

## 14. 跨模组互操作 `[api]`

```java
public interface SoftDependency {
    String modId();
    boolean isPresent();
    <T> Optional<T> get(Class<T> type);
}
public interface CapabilityRegistry {
    <T> void register(CapabilityKey<T> key, T implementation);
    <T> Optional<T> find(CapabilityKey<T> key);
    <T> List<T> findAll(CapabilityKey<T> key);
}
public record CapabilityKey<T>(String id, Class<T> type) {}
```

对方缺失时返回 `Optional.empty()` 并走降级路径；禁止到处散落 `Class.forName`。

## 15. 评审待确认

1. 事件是否允许携带 `PlayerRef` 而非 MC `Player`（建议允许，平台层解析）。
2. `ConfigNode` 是否直接用 Gson `JsonObject`（建议不，实现层用 Gson）。
3. `Rng` 是否直接用 `java.util.Random`（建议保留接口）。
4. `Registry<T>` 是否需要标签/分类两套索引（建议需要）。
5. 网络包是否默认切主线程（建议默认切，另提供 `handleAsync` 逃逸）。
