# YFCoreLib 接入示例

一份"从零接上一个模组"的完整走查。目标是**每一步都能直接抄**，并且每段代码都对应真实 API（不是示意）。

> 约定：下面用 `mymod` 当业务模组 id，`yfcorelib` 是库本身。库的入口类已经在库自己的
> `fabric.mod.json` / `mods.toml` 里声明过，**业务模组不需要写任何初始化代码**——
> 直接调用 `YFCoreLib` 的静态方法即可。

---

## 0. 前置：让构建与运行时认识这个库

### 构建脚本

库目前发布在 GitHub Packages（`mcmod.yfcorelib:yfcorelib-api` / `-common`），平台 jar 以 Release 附件形式提供。
本地联调最简单的方式是直接引用构建产物：

```groovy
// 平台模组工程（fabric / forge）
dependencies {
    modImplementation files("../YFCoreLib/fabric/build/libs/yfcorelib-fabric-0.1.0.jar")
    // Forge 换 ../YFCoreLib/forge/build/libs/yfcorelib-forge-0.1.0.jar
}
```

如果走 GitHub Packages，记得排除库自己构建期的脚手架依赖（它们不该传染给下游）：

```groovy
implementation("mcmod.yfcorelib:yfcorelib-common:0.1.0") {
    // common 的 POM 里已裁掉 fabric-loader / architectury，这里再加保险
    exclude group: "net.fabricmc"
    exclude group: "dev.architectury"
}
```

### 元数据

**Fabric**（`fabric.mod.json`）：

```json
"depends": { "yfcorelib": ">=0.1.0" }
```

**Forge**（`mods.toml`）：

```toml
[[dependencies.mymod]]
modId="yfcorelib"
mandatory=true
versionRange="[0.1.0,)"
ordering="NONE"
side="BOTH"
```

不声明依赖的后果：加载顺序不保证，`YFCoreLib.platform()` 可能在库初始化前被调用（见 §10 第 1 条）。

---

## 1. 配置：把键声明成常量

```java
public final class MyConfig {
    public static final ConfigKey<Integer> MAX_HEAT =
            ConfigKey.of("world.temperature.max", Integer.class, 40, "体温上限");
    public static final ConfigKey<Boolean> ENABLED =
            ConfigKey.of("world.temperature.enabled", Boolean.class, true);

    public static final ConfigSpec SPEC = ConfigBuilder.create("mymod")
            .define(MAX_HEAT)      // 装入已定义的键（含注释）
            .define(ENABLED)
            .version(2)
            .build();

    private static ConfigManager manager;

    public static ConfigManager get() {
        if (manager == null) {
            manager = YFCoreLib.config(SPEC, new HeatMigrator());
        }
        return manager;
    }

    private MyConfig() {
    }
}
```

读取：

```java
int max = MyConfig.get().get(MyConfig.MAX_HEAT);   // 40 或用户改过的值
MyConfig.get().set(MyConfig.MAX_HEAT, 50);
MyConfig.get().save();                             // 写盘（内存写入不自动落盘）
```

要点：

- **键用常量，不要按字符串反查**。`ConfigKey.of` 在声明期就会校验类型，写错类型当场抛异常。
- 库的语义是"**绝不覆盖用户已改的值**"：文件里有的键用文件值，缺的键补默认值，未知键原样保留。
- `reload()` 失败后**禁止** `save()`（会抛 `IllegalStateException`）——失败时内存树不代表文件真实内容，
  写回会吃掉用户配置。
- 支持的标量类型只有 `String / Boolean / Integer / Long / Double`。

版本迁移：

```java
public final class HeatMigrator implements ConfigMigrator {
    @Override
    public int fromVersion() { return 1; }

    @Override
    public int toVersion() { return 2; }

    @Override
    public void migrate(ConfigNode node) {
        // 1 -> 2：旧键 world.temperature 改名为 world.temperature.max，并把量程翻倍
        // 注意搬数字必须用 getNumber/setNumber：走 setString 会写成 JSON 字符串，
        // 下次加载时因"类型不符"被默认值替换，形成"迁移成功但配置被重置"的假象
        node.getNumber("world.temperature").ifPresent(old ->
                node.setNumber("world.temperature.max", old * 2));
    }
}
```

---

## 2. 内容注册：一个 DSL 管两端

```java
public final class MyContent {
    private static ContentNamespace ns;

    public static void register() {           // Forge：必须在模组构造期调用
        ns = YFRegistries.namespace("mymod");

        ns.items().add("ruby", () -> new Item(new Item.Properties()));

        BlockEntry<Block> ore = ns.block("ruby_ore", Block::new,
                BlockBehaviour.Properties.of().strength(3.0F));
        // 方块会自动生成同名 BlockItem，不需要自己写

        ns.sound("ruby_chime");
    }

    public static ContentNamespace ns() { return ns; }
}
```

要点：

- **Forge 下必须在模组构造期间首次调用**（延迟注册表要挂到模组事件总线）。Fabric 没有这个限制，
  但两端统一成"构造期注册"最省事。
- **不要在构造期取 `RegistrationRef.get()`**：Forge 此时还没真注册完，会抛
  `IllegalStateException("注册条目尚未完成注册，禁止在模组构造期取值")`。要取实例请等
  `ServerStartedEvent` 之后。
- 方块 + 物品的顺序是 BLOCK → ITEM，`ns.block(...)` 已经处理好；如果手写 BlockItem，务必保证这个顺序，
  否则会得到 `IllegalStateException("生成 BlockItem 时方块 xxx 尚未注册")`。

---

## 3. 事件：自带事件 + 自定义事件

```java
// 订阅（返回句柄，记得在合适的时机退订）
EventBus.Subscription sub = YFCoreLib.events().register(
        PlayerDeathEvent.class,
        event -> System.out.println(event.player().name() + " 死了"));

// 退订（幂等，可重复调用）
sub.close();
```

自带事件里，`PlayerDeathEvent` 是本库**唯一**通过 Mixin 采集的：

```java
YFCoreLib.events().register(PlayerDeathEvent.class, event -> {
    UUID id = event.player().id();
    MyStorage.flushPlayer(id);          // 死亡瞬间落盘
});
```

自定义事件：

```java
public record HeatCriticalEvent(UUID player, double heat) { }

YFCoreLib.events().register(HeatCriticalEvent.class, event -> warn(event.player()));
YFCoreLib.events().post(new HeatCriticalEvent(id, 0.95));
```

**分发语义（踩坑重灾区）**：

| 语义 | 说明 |
| --- | --- |
| 精确类型 | `post(new SubEvent())` **不会**触发 `ParentEvent` 的监听器 |
| 同步 | 在 `post` 的调用线程上按注册顺序执行 |
| 异常隔离 | 单个监听器抛异常只记 error，不影响后续监听器 |
| 无优先级 | 需要顺序就在一个监听器里自己编排 |

---

## 4. 网络：包定义用 JSON 编解码，收包已切主线程

```java
// ① 包本体
public record HeatSyncPacket(UUID player, int heat) implements Packet {
    @Override
    public String typeId() {
        return "mymod:heat_sync";
    }
}

// ② 编解码（纯函数，不要在里面碰世界/玩家）
public final class HeatSyncCodec implements DataCodec<HeatSyncPacket> {
    @Override
    public JsonElement encode(HeatSyncPacket value) {
        JsonObject json = new JsonObject();
        json.addProperty("player", value.player().toString());
        json.addProperty("heat", value.heat());
        return json;
    }

    @Override
    public HeatSyncPacket decode(JsonElement json) {
        JsonObject o = json.getAsJsonObject();
        return new HeatSyncPacket(
                UUID.fromString(o.get("player").getAsString()),
                o.get("heat").getAsInt());
    }
}

// ③ 注册（初始化阶段一次）
NetworkChannel channel = YFCoreLib.network("mymod:main");
PacketType<HeatSyncPacket> HEAT_SYNC = new PacketType<>(
        "mymod:heat_sync", Side.CLIENT, HeatSyncPacket.class,
        new HeatSyncCodec(),
        (packet, context) -> HeatHud.set(packet.heat()));
channel.register(HEAT_SYNC);

// ④ 发送
channel.sendToPlayer(uuid, new HeatSyncPacket(uuid, 55));     // S2C
channel.sendToServer(new HeatSyncPacket(uuid, 55));           // C2S
channel.broadcast(new HeatSyncPacket(uuid, 55));              // 全服
channel.sendToNear("minecraft:overworld", x, y, z, 32.0, packet);
```

要点：

- **处理器总在接收侧主线程跑**，可以放心操作世界状态。
- **编解码器必须是纯函数**：Forge 侧解码发生在 Netty 线程，不是主线程。
- 包体走 JSON 传输，单个包有 **1,000,000 字符**上限；超了在发送侧抛异常。
- 分包类型 id 形如 `namespace:path`，必须唯一。
- `receiver=COMMON` 表示双向。

---

## 5. 调度与计时器

```java
// 延迟 1 刻
YFCoreLib.scheduler(Side.SERVER).delay(1, () -> refresh());

// 每 20 刻（1 秒）执行
ScheduledTask task = YFCoreLib.scheduler(Side.SERVER).repeat(20, () -> tickHeat());
task.cancel();

// 命名倒计时（单位：刻）
YFCoreLib.timers().set("mymod:cooldown:" + uuid, 200);
long left = YFCoreLib.timers().remaining("mymod:cooldown:" + uuid);
```

要点：

- `scheduler(Side.COMMON)` **直接抛错**——没有任何平台为 COMMON 驱动刻，受理任务等于让它永不执行。
- `repeat(period, task)` 的**第一次执行发生在 `period` 刻之后**（不是立即）。
- 计时器文件的落盘由库的 `TimerPersistence` 自动处理（每 6000 刻一次），文件在
  `<存档目录>/data/yfcorelib/timers.json`，**每个存档各一份**。

---

## 6. 持久化

### 玩家数据

```java
private static final JsonPlayerDataStore<HeatData> STORE = new JsonPlayerDataStore<>(
        YFCoreLib.platform().worldDir()
                .orElseThrow(() -> new IllegalStateException("配置数据只能在服务端读写"))
                .resolve("data").resolve("mymod").resolve("players"),
        new HeatCodec());

// 加入
YFCoreLib.events().register(PlayerJoinEvent.class, e ->
        YFCoreLib.scheduler(Side.SERVER).delay(1, () ->
                YFCoreLib.lifecycle().playerReady(e.player())));

YFCoreLib.lifecycle().addHook(player -> STORE.get(player.id()));

// 离开：必须 unload，否则内存里会永久保留离线玩家的数据
YFCoreLib.events().register(PlayerLeaveEvent.class, e -> STORE.unload(e.player().id()));
```

要点：

- **`unload` 是必须的**，不是优化。忘了它 = 玩家数据在内存里无界增长。
- `unload` 会先保存再清缓存；如果保存失败，缓存会被保留（不丢数据）并记 error。

### 世界数据

```java
JsonWorldDataStore<MyWorldData> world = new JsonWorldDataStore<>(
        worldDir.resolve("data").resolve("mymod").resolve("world.json"),
        new MyWorldCodec());
```

---

## 7. 文本度量（HUD / 字幕）

```java
TextMeasure measure = new FontTextMeasure(Minecraft.getInstance().font);
TextLayout layout = TextLayout.of(measure, subtitle, 240);

int boxWidth = layout.width();
int boxHeight = layout.height();
for (String line : layout.lines()) {
    guiGraphics.drawString(font, line, x, y, 0xFFFFFF);
    y += measure.lineHeight();
}
```

要点：

- `FontTextMeasure` **只能在客户端构造**（引用了 `Font`）。
- 每行是**拍平后的纯文本**，样式（颜色/粗体）会丢；需要样式就直接用 `Font#split`。
- `wrap` 只按像素宽度折行，**不处理 `\n`**；要保留显式换行就先按 `\n` 切分再分别折行。
- 想单测布局逻辑？实现一个假 `TextMeasure` 即可，不需要进游戏。

---

## 8. 工具箱（纯逻辑，全部可单测）

```java
// 缓动
double v = Easing.EASE_OUT_CUBIC.apply(0.5);

// 颜色
Color c = Color.of(255, 128, 0).lerp(Color.WHITE, 0.25);
int argb = c.toArgb();

// 随机（同种子可复现）
Rng rng = Rng.of(12345L);
Rng perBlock = Rng.at(worldSeed, pos.getX(), pos.getZ());   // 同坐标恒定

// 权重表（3:1 抽取）
WeightedTable<String> table = WeightedTable.<String>create()
        .add("common", 3)
        .add("rare", 1)
        .build();
String picked = table.pickOr(rng, "common");

// 噪声
Noise noise = new Noise(seed);
double n = noise.fbm2(x * 0.05, z * 0.05, 4);

// 反射（拼错名字会告诉你候选名，不会静默返回 null）
Object speed = ReflectionCache.read(entity, "walkingSpeed");

// 软依赖
if (SoftDependency.isLoaded("curios")) { ... }

// 能力注册（"装了这个扩展才有"的实现）
CapabilityKey<HeatProvider> HEAT = CapabilityKey.of("mymod:heat", HeatProvider.class);
CapabilityRegistry caps = new SimpleCapabilityRegistry();
caps.register(HEAT, impl);
Optional<HeatProvider> provider = caps.get(HEAT);
```

---

## 9. 写自己的 Mixin 时

```java
@Mixin(SomeVanillaClass.class)
public abstract class MyMixin {
    @Inject(method = "someMethod", at = @At("HEAD"))
    private void mymod$onSomeMethod(CallbackInfo ci) {
        MixinCallbacks.fire(new SomeMomentEvent(this));
    }
}
```

规则只有一条：**Mixin 里只做"发现时机 + 组事件"，业务一律写成监听器**。
理由：Mixin 跑在别人的方法体里，业务代码抛异常会直接破坏原版逻辑，而且没法单测。

配置要声明到**加载器认得的地方**，这是最容易漏、且完全不报错的一步：

| 加载器 | 声明位置 |
| --- | --- |
| Fabric | `fabric.mod.json` 的 `"mixins": ["mymod.mixins.json"]` |
| Forge | `build.gradle` 的 `loom { forge { mixinConfig "mymod.mixins.json" } }` —— **不是** `mods.toml` |

`mods.toml` 里写 `[[mixins]]` 在 Forge 1.20.1 上不会生效也不会报错，配置会安静地躺在 jar 里从不被加载。

顺便用自带的校验器验证自己的配置：

```java
for (MixinConfigValidator.Problem p : MixinConfigValidator.validateLoadedConfig("mymod.mixins.json")) {
    LOG.error("Mixin 配置问题: {}", p);
}
```

但校验器只能证明"配置写对了"，证明不了"注入生效了"。想确认后者，去看目标类上有没有你的注入方法（Mixin 会把处理器方法合并进目标类）：

```java
boolean injected = Arrays.stream(SomeVanillaClass.class.getDeclaredMethods())
        .anyMatch(m -> m.getName().contains("mymod$onSomeMethod"));
if (!injected) {
    LOG.error("Mixin 没有生效：检查 mixin 配置是否被加载、refmap 是否可用");
}
```

---

## 10. 接入时最容易踩的坑

1. **在静态初始化器里调 `YFCoreLib.platform()`**
   此时库可能还没初始化（`ServiceLoader` 找不到实现）。所有库调用都放到模组构造器或更晚。
2. **`scheduler(Side.COMMON)`**
   直接抛 `IllegalArgumentException`。用 `CLIENT` 或 `SERVER`。
3. **监听 `Object.class` 想收所有事件**
   收不到。事件总线按精确类型分发。
4. **配置键按字符串反查**
   请用 `ConfigKey` 常量 + `define(key)`，否则类型安全就白做了。
5. **Forge 构造期取 `RegistrationRef.get()`**
   抛 `IllegalStateException`。引用可以先持有，取值放到 `ServerStartedEvent`。
6. **忘了 `unload` 玩家数据**
   内存无界增长。绑到 `PlayerLeaveEvent`。
7. **包编解码器里做业务**
   Forge 侧在 Netty 线程解码，不是主线程。codec 必须是纯函数。
8. **`repeat(period, task)` 以为会立即执行一次**
   不会，首次在 `period` 刻之后。
9. **Mixin 配置与实际类不一致**
   启动时完全不报错，功能静默失效。用 `MixinConfigValidator` 在启动时自检。
10. **库的 `selftest` 只在 dev 有第 7 项**
   发布环境 `注册 DSL` 一项会被跳过（`DemoContent` 非 dev 不注册），不是 bug。

---

## 11. 排查顺序

遇到"功能没反应"时按这个顺序查（比盲猜快）：

1. `/yfcorelib selftest` —— 六项自检先过一遍（dev 环境七项）
2. `/yfcorelib info` —— 确认 loader / MC 版本 / dev 标志 / 配置是否真的被创建
3. 事件没触发 → 检查事件类型是否精确匹配、监听器是否被 `close()` 了
4. 网络没收到 → 共享包类型 id 是否两端一致、是否在初始化阶段 `register`、包体是否超上限
5. Mixin 没生效 → `MixinConfigValidator` 的返回值 + 构建产物里是否有 refmap
