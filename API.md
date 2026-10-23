# YFCoreLib API 使用文档

> 适用版本：YFCoreLib 0.1.0 · Minecraft 1.20.1 · Fabric / Forge
> 本文档中的类名均可直接作为 import 目标，包名以 `mcmod.yfcorelib` 开头。

---

## 目录

1. [库的定位与架构](#1-库的定位与架构)
2. [接入方式](#2-接入方式)
3. [初始化时序](#3-初始化时序)
4. [统一门面 YFCoreLib](#4-统一门面-yfcorelib)
5. [平台抽象 Platform](#5-平台抽象-platform)
6. [事件总线 EventBus](#6-事件总线-eventbus)
7. [刻级调度器 Scheduler](#7-刻级调度器-scheduler)
8. [计时器 TimerStore](#8-计时器-timerstore)
9. [延迟加载队列 LifecycleQueue](#9-延迟加载队列-lifecyclequeue)
10. [配置系统 Config](#10-配置系统-config)
11. [数据持久化 Data](#11-数据持久化-data)
12. [玩家会话 PlayerSessionStore](#12-玩家会话-playersessionstore)
13. [网络通信 Network](#13-网络通信-network)
14. [快照同步 SyncEngine](#14-快照同步-syncengine)
15. [内容注册 DSL](#15-内容注册-dsl)
16. [属性修改快照](#16-属性修改快照)
17. [通用注册中心 Registry](#17-通用注册中心-registry)
18. [工具类](#18-工具类)
19. [线程模型与安全约束](#19-线程模型与安全约束)
20. [诊断命令与自检](#20-诊断命令与自检)
21. [常见问题](#21-常见问题)

---

## 1. 库的定位与架构

YFCoreLib 不是"功能模组"，而是一组**跨加载器的底层能力**：事件总线、刻级调度、配置内核、数据持久化、网络通道、快照同步、内容注册 DSL、属性快照。它把 Fabric 与 Forge 的差异收敛在平台桥层，业务代码只依赖纯 Java 契约，从而做到**一份业务代码双端编译、双端行为一致**。

### 1.1 四个模块

| 模块 | 内容 | 依赖边界 |
| --- | --- | --- |
| `api` | 契约接口 + 纯 Java 实现（事件总线、调度器、计时器、配置内核、JSON 仓库、网络契约、同步引擎、路径校验、通用注册中心） | **禁止** import `net.minecraft.*`、`net.fabricmc.*`、`net.minecraftforge.*` |
| `common` | 允许 Minecraft 类型的共享实现（内容注册 DSL、属性快照、计时器持久化、调试命令、自检） | 允许 MC，**禁止**加载器与 Fabric API |
| `fabric` | Fabric 平台实现（`Platform`、网络桥、注册后端、生命周期桥、客户端入口） | 允许 Fabric API |
| `forge` | Forge 平台实现（同上，使用延迟注册与 `SimpleChannel`） | 允许 Forge API |

api 模块的边界不是"建议"而是**构建期强制**：根 `build.gradle` 中的 `checkImportBoundary` 任务会扫描源码 import，一旦越界，`gradlew build` 直接失败。这样做的原因是——加载器 API 与 common 处于同一 classpath，越界 import 在编译期根本不会报错，只有源码扫描能在早期阻断。

### 1.2 分层依赖关系

```
业务模组（附属）
   ├── 依赖 api     ← 契约与纯逻辑，可安全 compileOnly
   └── 依赖 common  ← 需要注册 DSL / 属性快照时才依赖
          ↓
   YFCoreLib 的 fabric / forge 最终 jar（已内嵌 api + common）
```

平台实现通过 **ServiceLoader** 被发现：`api` 定义 `PlatformProvider`、`NetworkProvider`，`common` 定义 `RegistrarFactory`，两个平台模块各自在 `META-INF/services` 写一行实现类。业务代码永远不 new 平台实现。

### 1.3 为什么用 ServiceLoader 而不是反射/类名判断

- 反射按类名探测依赖字符串拼接，重命名编译期无法发现错误；ServiceLoader 是类型安全的。
- 双端只需"注册一个实现文件"，业务侧零改动；新增 NeoForge 只需新增模块，api / common 不动（开闭原则）。
- 解析失败会抛带指引的 `IllegalStateException`，而不是晦涩的 `ClassNotFoundException`。

---

## 2. 接入方式

### 2.1 远程坐标

`api` 与 `common` 发布在 GitHub Packages：

| 坐标 | 内容 |
| --- | --- |
| `mcmod.yfcorelib:yfcorelib-api:0.1.0` | 纯 Java 契约层 |
| `mcmod.yfcorelib:yfcorelib-common:0.1.0` | 双端共享实现 |

GitHub Packages **即使公开包也要求鉴权**。业务模组仓库需配置仓库地址与读权限凭据：

```groovy
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/19136644525lxy/YFCorelib")
        credentials {
            username = project.findProperty("gpr.user") ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.key") ?: System.getenv("GITHUB_TOKEN")  // 需 read:packages
        }
    }
}

dependencies {
    compileOnly "mcmod.yfcorelib:yfcorelib-api:0.1.0"
    compileOnly "mcmod.yfcorelib:yfcorelib-common:0.1.0"
}
```

依赖 `common` 时注意：其 POM 会带出 `dev.architectury:architectury` 与 `net.fabricmc:fabric-loader`（由构建配置自动推导）。Forge 侧若不需要 Fabric 依赖，可加 `exclude group: "net.fabricmc"`。

### 2.2 本地 jar 接入（离线或未发布时）

先构建产物：

```powershell
.\gradlew.bat build
```

产出位置：

| 文件 | 用途 |
| --- | --- |
| `api/build/libs/yfcorelib-api-0.1.0.jar` | 编译期契约 |
| `common/build/libs/yfcorelib-common-0.1.0.jar` | 编译期共享实现（需要注册 DSL 时） |
| `fabric/build/libs/yfcorelib-fabric-1.20.1-0.1.0.jar` | 运行时（玩家安装） |
| `forge/build/libs/yfcorelib-forge-1.20.1-0.1.0.jar` | 运行时（玩家安装） |

在业务模组中：

```groovy
repositories {
    flatDir { dirs "../YFCoreLib/api/build/libs", "../YFCoreLib/common/build/libs" }
}

dependencies {
    // 只在编译期可见：运行时这些类已内嵌在 YFCoreLib 的平台 jar 中
    compileOnly "mcmod.yfcorelib:yfcorelib-api:0.1.0"
    compileOnly "mcmod.yfcorelib:yfcorelib-common:0.1.0"
}
```

**为什么必须是 compileOnly**：YFCoreLib 的 `shadowJar` 已经把 api 与 common 的类打进平台 jar。若业务模组再用 `implementation` 打包一份，classpath 上会出现两份同名类，轻则 `LinkageError`、重则 ServiceLoader 拿到错误的实现。运行时的类归属只有一处——YFCoreLib 的平台 jar。

### 2.3 Architectury 业务模组接入

若业务模组本身也是 Architectury 多模块工程，直接在 `common` 模块加：

```groovy
dependencies {
    modCompileOnly project(":api")        // 若 YFCoreLib 与业务模组同属一个 multi-project
    // 或跨仓库时使用本地 jar
    compileOnly files("libs/yfcorelib-api-0.1.0.jar")
    compileOnly files("libs/yfcorelib-common-0.1.0.jar")
}
```

同时确保业务模组的 `fabric`/`forge` 模块**不要**把 api 打进自己的 `shadowCommon`，否则与 YFCoreLib 冲突。

### 2.4 声明依赖关系（mod 元数据）

为了在加载顺序上保证 YFCoreLib 先加载，建议显式声明依赖。

Fabric（`fabric.mod.json`）：

```json
{
  "depends": {
    "yfcorelib": ">=0.1.0"
  }
}
```

Forge（`mods.toml`）：

```toml
[[dependencies.mymod]]
    modId="yfcorelib"
    mandatory=true
    versionRange="[0.1.0,)"
    ordering="AFTER"
    side="BOTH"
```

---

## 3. 初始化时序

时序错误是双端开发最常见的问题来源。YFCoreLib 内部已经把关键时序差异封装好，但业务侧仍需在正确阶段调用。

| 阶段 | Fabric | Forge | 业务可做的事 |
| --- | --- | --- | --- |
| 模组构造 | `onInitialize()` | 模组类构造函数 | **Forge 必须在此调用** `YFRegistries.namespace(...)`、`DemoContent.register()` 等延迟注册入口 |
| 内容注册 | `onInitialize()` 内注册即生效 | `DeferredRegister` 延迟到注册事件 | 声明注册条目本身任何时刻都安全，但**不要取值** |
| 服务端启动完成 | `ServerStartedEvent` | `ServerStartedEvent` | 读取世界数据、恢复计时器、初始化依赖世界的状态 |
| 玩家加入 | `PlayerJoinEvent` → 延迟 1 刻 → `LifecycleHook` | 同左 | 给玩家发数据包、挂载属性 |
| 服务端停止 | `ServerStoppingEvent` | `ServerStoppingEvent` | 落盘、释放缓存 |
| 客户端刻 | `onInitializeClient()` 后每刻 | 客户端 tick 事件 | 驱动客户端调度器（库已代劳） |

**为什么 Forge 要求"构造期"**：Forge 的 `DeferredRegister` 必须在模组构造期间绑定到模组事件总线（`FMLJavaModLoadingContext.get().getModEventBus()`）。晚于构造期创建会导致注册事件已经过去，条目永远不进入注册表。

**为什么玩家就绪要延迟 1 刻**：`PlayerJoinEvent` 触发时客户端尚未完成初始数据同步，此时下发 GUI 或能力数据可能被覆盖。库在加入事件后用 `scheduler(Side.SERVER).delay(1, ...)` 延后一刻再回调 `LifecycleHook`，是经过验证的最小安全间隔。

---

## 4. 统一门面 YFCoreLib

`mcmod.yfcorelib.api.YFCoreLib` 是全局单例入口，所有业务代码从这里拿能力，不要自己 new 实现。

```java
import mcmod.yfcorelib.api.YFCoreLib;
import mcmod.yfcorelib.api.Side;
```

### 4.1 方法总表

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `MOD_ID` | `String` | 常量 `"yfcorelib"` |
| `platform()` | `Platform` | 当前加载器环境（ServiceLoader 缓存） |
| `events()` | `EventBus` | 全局事件总线 |
| `scheduler(Side)` | `Scheduler` | 指定侧的刻调度器 |
| `timers()` | `TimerStore` | 服务端刻级命名计时器 |
| `lifecycle()` | `LifecycleQueue` | 玩家就绪钩子队列 |
| `config(ConfigSpec, ConfigMigrator...)` | `ConfigManager` | 按 spec 名缓存单例，首次创建时自动 `reload()` |
| `configNames()` | `Set<String>` | 已创建配置名（监控） |
| `network(String name)` | `NetworkChannel` | 按名缓存通道单例 |

### 4.2 使用示例

```java
public final class MyMod {
    public static void init() {
        // 事件
        YFCoreLib.events().register(ServerStartedEvent.class, e -> loadWorldState());

        // 调度：延迟 20 刻（1 秒）执行
        YFCoreLib.scheduler(Side.SERVER).delay(20, MyMod::grantStarterKit);

        // 计时器：30 秒冷却
        YFCoreLib.timers().set("mymod:dash_cooldown:" + playerId, 600);
    }
}
```

### 4.3 设计要点

- `scheduler(Side.COMMON)` 实例**存在但不会被驱动**，它是为"无侧别语义的纯逻辑"预留的占位，不要指望它自动跑。需要执行请用 `SERVER` 或 `CLIENT`。
- `config(spec)` 是"按名去重"的：同名 spec 第二次调用返回第一次的实例，后续传入的 migrator 会被忽略。因此配置定义应当是**静态常量**，在模组初始化期一次性创建。
- `network(name)` 同理按名缓存。名字必须形如 `命名空间:路径`，格式非法的名字会在创建时立刻抛错。

---

## 5. 平台抽象 Platform

`mcmod.yfcorelib.api.platform.Platform` 屏蔽加载器差异。**业务代码只允许依赖它，禁止直接调用 FabricLoader 或 Forge API**——这是保持双端一份代码的前提。

### 5.1 方法说明

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `loaderName()` | `String` | `fabric` / `forge` / `neoforge` |
| `mcVersion()` | `String` | Minecraft 版本，如 `1.20.1` |
| `loaderVersion()` | `String` | 加载器版本，取不到返回 `unknown` |
| `isClient()` | `boolean` | 是否客户端环境 |
| `isDevelopment()` | `boolean` | 是否开发环境（`runClient` / `runServer`） |
| `gameDir()` | `Path` | 游戏根目录 |
| `configDir()` | `Path` | 配置目录 |
| `modsDir()` | `Path` | mods 目录 |
| `isModLoaded(String modId)` | `boolean` | 软依赖探测 |
| `modInfo(String modId)` | `Optional<ModInfo>` | 版本、名称、描述 |

`ModInfo` 为 record：`(String modId, String version, String name, Optional<String> description)`。

### 5.2 软依赖示例

```java
Platform platform = YFCoreLib.platform();

if (platform.isModLoaded("jei")) {
    platform.modInfo("jei").ifPresent(info ->
            LOG.info("检测到 {} {}", info.name(), info.version()));
    // 此处可加载 JEI 兼容类；注意：类引用必须在分支内，避免 NoClassDefFoundError
}
```

### 5.3 注意事项

- `modInfo` 的 `description` 可能为空，使用前判空（已用 `Optional` 表达）。
- `isModLoaded` 传入不存在的 id 返回 `false`，不抛异常。
- 不要在静态初始化器中调用 `YFCoreLib.platform()`，此时平台模块可能尚未完成 ServiceLoader 注册。放在模组入口方法里调用。

---

## 6. 事件总线 EventBus

`mcmod.yfcorelib.api.event.EventBus` 是同步、精确类型分发的轻量总线。

### 6.1 接口

```java
public interface EventBus {
    <E> Subscription register(Class<E> type, Consumer<E> listener);
    <E> void post(E event);

    interface Subscription extends AutoCloseable {
        void close();
    }
}
```

### 6.2 内置事件清单

| 事件 | 字段 | 触发时机 |
| --- | --- | --- |
| `ServerStartedEvent` | 无 | 服务端启动完成 |
| `ServerStoppingEvent` | 无 | 服务端开始停止（存档保存前） |
| `ServerTickEvent` | `Side side` | 每刻；服务端 `Side.SERVER`，客户端 `Side.CLIENT` |
| `PlayerJoinEvent` | `PlayerRef player` | 玩家进入服务端 |
| `PlayerLeaveEvent` | `PlayerRef player` | 玩家断开 |
| `PlayerRespawnEvent` | `PlayerRef player, boolean endPortal` | 玩家重生；`endPortal` 表示经末地折跃门返回 |
| `PlayerDimensionChangeEvent` | `PlayerRef player, String from, String to` | 跨维度；维度 id 形如 `minecraft:overworld` |

`PlayerRef` 是 api 层的玩家引用，只暴露 `id()`、`name()`、`isOnline()`，不持有 MC 实体。需要实体时由业务在自己的平台分支里用 `UUID` 反查。

### 6.3 订阅与退订

```java
EventBus.Subscription sub = YFCoreLib.events().register(
        PlayerJoinEvent.class, event -> welcome(event.player()));

// 不再需要时退订，避免长生命周期对象持有已失效监听器
sub.close();
```

`Subscription.close()` 是幂等的，可重复调用。

### 6.4 分发语义（重要）

1. **精确类型匹配**：`register(FooEvent.class, ...)` 只接收 `FooEvent` 实例。若 `BarEvent extends FooEvent`，`post(new BarEvent())` **不会**触发 `FooEvent` 的监听器。这是刻意的——避免子类事件误触发父类逻辑。
2. **同步执行**：`post` 在调用线程上按注册顺序依次执行监听器。总线内部只保证"注册表线程安全"，不保证"监听器异步"。
3. **异常隔离**：单个监听器抛 `Throwable` 会被捕获并记录 SEVERE，后续监听器照常执行。这保证一个业务 bug 不会中断整条链路。
4. **无优先级**：按注册顺序执行。若需要顺序控制，由业务在单个监听器内部编排。

### 6.5 自定义事件

```java
public record MyEvent(String reason, int amount) {}

YFCoreLib.events().register(MyEvent.class, e -> handle(e.reason(), e.amount()));
YFCoreLib.events().post(new MyEvent("tick", 3));
```

推荐用 `record` 定义事件：不可变、自带 `equals`、可作为快照键。

---

## 7. 刻级调度器 Scheduler

`mcmod.yfcorelib.api.scheduler.Scheduler` 把"多久之后做什么"从各加载器的 tick 事件里解耦出来。业务只提交任务，tick 由平台驱动。

### 7.1 接口

| 方法 | 说明 |
| --- | --- |
| `delay(int delayTicks, Runnable task)` | 延迟执行一次，`0` = 下一 tick |
| `delay(Object owner, int delayTicks, Runnable task)` | 带归属者的延迟任务 |
| `repeat(int periodTicks, Runnable task)` | 每 `periodTicks` 重复，首次在 `periodTicks` 之后 |
| `repeat(int delayTicks, int periodTicks, Runnable task)` | 自定义首次延迟 |
| `repeat(Object owner, int delayTicks, int periodTicks, Runnable task)` | 带归属者的重复任务 |
| `attach(Tickable tickable)` | 挂持续型对象，每 tick 调 `tick()`，`isDone()` 为真自动移除 |
| `cancelAll(Object owner)` | 按 owner **身份（==）** 取消其名下全部未完成任务 |
| `tick()` | 平台驱动，**业务禁止调用** |

返回的 `ScheduledTask` 提供 `cancel()` 与 `isCancelled()`。

### 7.2 基本用法

```java
Scheduler server = YFCoreLib.scheduler(Side.SERVER);

// 5 秒后执行一次
server.delay(100, () -> LOG.info("5 秒到了"));

// 每 2 秒执行，首次在 1 秒后
ScheduledTask task = server.repeat(20, 40, this::tickUpdate);
task.cancel();   // 取消
```

### 7.3 归属者模式（推荐用于可取消的模块）

```java
public final class DashModule {
    private final Object owner = new Object();   // 用模块实例本身亦可

    public void start() {
        YFCoreLib.scheduler(Side.SERVER).repeat(owner, 0, 20, this::tick);
    }

    public void stop() {
        YFCoreLib.scheduler(Side.SERVER).cancelAll(owner);  // 一次性取消全部
    }
}
```

**为什么用 `==` 而不是 `equals` 比较 owner**：业务对象的 `equals` 常被重写为值比较（record 尤其如此），若两个内容相同的对象被误判为同一归属者，会错误取消别人的任务。用引用身份比较是唯一无歧义的方式。

### 7.4 语义细节

- 时间单位是**游戏刻**，20 刻 ≈ 1 秒（服务端）。
- `delayTicks` 为负抛 `IllegalArgumentException`；`periodTicks <= 0` 同样抛错。
- 同一刻到期的多个任务按**提交顺序**执行（内部单调递增序号排序）。
- 单个任务抛异常被捕获记录，**不影响**本轮其他任务，也不会取消该重复任务。
- 重复任务在 `tick()` 中只会推进下次到期刻，不会重复入列——这修正了早期"每期执行两次"的缺陷。
- `cancel()` 对已执行完的任务返回 `false`，对未执行任务返回 `true`，可重复调用。
- 取消后的任务在下一轮 `tick()` 时被清理出列表，不会堆积。
- `Tickable` 每 tick 都调用，`isDone()` 检查发生在调用**之前**：返回 true 后当刻不再 `tick()`。
- `attach` 没有对应的分离方法，靠 `isDone()` 或创建新的调度器实例来终止。

### 7.5 监控

`TickScheduler` 额外提供 `currentTick()` 与 `pendingCount()`，可用于调试页或自检。业务若只依赖 `Scheduler` 接口，则拿不到这两个方法——需要时强转，或直接在调试命令里用。

---

## 8. 计时器 TimerStore

`TimerStore` 是"命名倒计时"的存储层，与调度器的区别：调度器管**动作**，计时器管**剩余时间**。典型用途是技能冷却、Buff 持续时间——这些需要随时查询剩余值，且要跨重启恢复。

### 8.1 接口

| 方法 | 说明 |
| --- | --- |
| `remaining(String key)` | 剩余刻数；不存在或已过期返回 `0` |
| `set(String key, long ticks)` | 设置/重置倒计时；负值按 0 处理 |
| `clear(String key)` | 立即移除 |
| `keys()` | 未过期的全部键 |
| `sweep()` | 清理过期条目，防内存泄漏 |

### 8.2 使用

```java
TimerStore timers = YFCoreLib.timers();
String key = "mymod:cooldown:" + player.getUUID();

if (timers.remaining(key) > 0) {
    player.sendSystemMessage(Component.literal("还需 " + timers.remaining(key) / 20 + " 秒"));
    return;
}

timers.set(key, 600);   // 30 秒
```

### 8.3 持久化机制

`common` 模块的 `TimerPersistence` 已把持久化挂到事件总线上：

- 文件位置：`<gameDir>/data/yfcorelib/timers.json`
- 写入方式：`AtomicFiles.writeAtomic`（临时文件 + 原子移动）
- 恢复时机：`ServerStartedEvent`
- 保存时机：`ServerStoppingEvent` + 每 6000 刻自动保存一次
- 损坏条目：单条解析失败跳过并 warn，不影响其他条目

平台模块在入口各调用一次 `TimerPersistence.register()`，方法**幂等**，双端重复调用安全。业务无需任何额外操作。

### 8.4 内存安全

`SimpleTimerStore` 在 `remaining()` 读取时会**惰性清理**已过期条目；平台每 200 刻调用一次 `sweep()` 兜底。即便如此，业务仍应保证 key 数量有上界——例如按玩家 UUID 生成 key 时，在玩家离线时主动 `clear`。

---

## 9. 延迟加载队列 LifecycleQueue

`LifecycleQueue` 解决一个具体问题：**业务初始化晚于玩家登录，导致在线玩家拿不到初始化数据**。

### 9.1 接口

| 方法 | 说明 |
| --- | --- |
| `addHook(LifecycleHook hook)` | 注册就绪钩子；**对当前已就绪的在线玩家立即补发** |
| `playerReady(PlayerRef player)` | 标记玩家就绪并触发全部钩子（平台调用） |
| `playerGone(UUID id)` | 玩家离线，清除在线标记（平台调用） |

```java
public interface LifecycleHook {
    void onPlayerReady(PlayerRef player);
}
```

### 9.2 使用

```java
YFCoreLib.lifecycle().addHook(player -> {
    // 此时玩家已完成初始同步，可以安全下发数据
    syncEngine.onJoin(player.id());
    playerDataStore.get(player.id());
});
```

### 9.3 语义

- 钩子按注册顺序执行，单个钩子异常被捕获记录，不影响其他钩子。
- `addHook` 会遍历内部在线集合补发——因此"先登录后注册钩子"和"先注册钩子后登录"结果一致，业务无需关心初始化顺序。
- `playerReady` 会先把玩家写入在线集合再分发，保证钩子内部查询 `online()` 一致。
- 平台侧在玩家加入后延迟 1 刻调用 `playerReady`（原因见[第 3 节](#3-初始化时序)），离线时调用 `playerGone`。

---

## 10. 配置系统 Config

配置内核是本库最"重"的部分，行为经过四个约束验证：**补默认键不覆盖用户值、未知键保留、类型不符整体替换、版本迁移只执行一次**。

### 10.1 组成

| 类型 | 职责 |
| --- | --- |
| `ConfigSpec` | 不可变的配置定义：名称、目标版本、键列表 |
| `ConfigKey<T>` | 单个键：点分路径、类型、默认值、可选注释 |
| `ConfigBuilder` | 链式 DSL，产出 `ConfigSpec` |
| `ConfigManager` | 读写、重载、保存、版本查询 |
| `ConfigMigrator` | 版本迁移器 |
| `ConfigSource` | 读写抽象（文件 / 内存） |
| `FileConfigSource` | 文件实现：`<configDir>/<name>.json` |
| `SimpleConfigManager` | 合并内核默认实现 |

### 10.2 支持的值类型

仅支持五种标量：`String`、`Boolean`、`Integer`、`Long`、`Double`。其它类型在 `ConfigKey` 构造时直接抛 `IllegalArgumentException`。

**为什么限制类型**：配置后端是 JSON 标量，限死类型才能保证"读出来的类型"与"声明类型"一一对应，避免运行时出现 `Integer` 被当成 `Long` 的隐性错误。复杂结构请拆成多个标量键，或用 `JsonElement` 自行管理（推荐前者）。

### 10.3 定义配置

```java
public final class MyConfig {
    public static final ConfigSpec SPEC = ConfigBuilder.create("mymod")
            .version(2)
            .define("enabled", Boolean.class, true)
            .comment("enabled", "总开关")
            .define("radius", Double.class, 8.0)
            .comment("radius", "作用半径（格）")
            .define("mode", String.class, "normal")
            .build();

    public static final ConfigKey<Boolean> ENABLED = key("enabled");
    public static final ConfigKey<Double> RADIUS = key("radius");
    public static final ConfigKey<String> MODE = key("mode");

    @SuppressWarnings("unchecked")
    private static <T> ConfigKey<T> key(String path) {
        return (ConfigKey<T>) SPEC.keys().stream()
                .filter(k -> k.path().equals(path))
                .findFirst()
                .orElseThrow();
    }
}
```

`ConfigSpec.keys()` 按定义顺序返回，索引稳定，也可以直接按下标取。

### 10.4 获取管理器并读写

```java
ConfigManager cfg = YFCoreLib.config(MyConfig.SPEC);

boolean enabled = cfg.get(MyConfig.ENABLED);   // 类型安全
cfg.set(MyConfig.RADIUS, 12.0);                // 只改内存
cfg.save();                                    // 落盘
cfg.reload();                                  // 重新读盘并合并
```

可用方法：

| 方法 | 说明 |
| --- | --- |
| `get(ConfigKey<T>)` | 读取当前值；未加载或类型不符返回默认值 |
| `set(ConfigKey<T>, T)` | 写入内存树，**不落盘** |
| `reload()` | 从来源重读、补默认、执行迁移；返回是否成功 |
| `save()` | 内存树写回来源（原子写） |
| `loadedVersion()` | 当前已加载版本（迁移后为目标版本） |

`YFCoreLib.config(spec)` 首次调用会自动 `reload()`，所以业务拿到即可读。

### 10.5 文件形态

路径：`<configDir>/mymod.json`

```json
{
  "__version": 2,
  "enabled": false,
  "radius": 12.0,
  "mode": "normal",
  "my_own_extra_key": 42
}
```

- `__version` 是**保留键**，业务不得使用该路径，否则会被版本逻辑覆盖。
- 缩进美化、不转义 HTML（中文可直接阅读）。
- 未知键 `my_own_extra_key` 会在所有操作中原样保留。

### 10.6 合并规则（严格按此实现）

| 场景 | 行为 |
| --- | --- |
| 文件缺该键 | 补默认值，标记需要写回 |
| 文件已有该键且类型正确 | **完全不动**，用户值优先 |
| 文件该键类型不符 | 整体替换为默认值，记录 warn，不崩溃 |
| 文件存在未知键 | 原样保留，永不删除 |
| 文件不存在 | 以默认值全集创建 |
| 文件顶层不是 JSON 对象 | 视为空配置，记录 warn |
| 文件语法错误 | 视为空配置，记录 warn |

"类型不符"的判定：`String` 要求 JSON 字符串，`Boolean` 要求布尔，数值类要求 JSON number（`Integer/Long/Double` 之间不互斥）。

### 10.7 版本迁移

```java
public final class V1ToV2 implements ConfigMigrator {
    @Override public int fromVersion() { return 1; }
    @Override public int toVersion()   { return 2; }

    @Override
    public void migrate(ConfigNode node) {
        String old = node.getString("mode").orElse("normal");
        node.setString("mode", old.equals("legacy") ? "normal" : old);
    }
}

ConfigManager cfg = YFCoreLib.config(MyConfig.SPEC, new V1ToV2());
```

迁移链行为：

- 从文件 `__version` 开始，逐级查找 `fromVersion` 匹配的迁移器，直到达到 `spec.version()`。
- 每执行一级立刻回写新版本号，因此**中断后不会重复执行**已完成的迁移。
- 缺少某一级迁移器时，记录 warn 并**中止**（不猜测跳级）。
- 迁移器抛异常时，记录 error 并中止，保留已完成的版本推进。
- 文件版本高于目标版本时记录 warn（典型场景：用户降级了模组），不做回退。
- 旧文件缺少 `__version` 按版本 `0` 处理，可写 `fromVersion=0` 的迁移器兼容更早期格式。

**迁移器编写约束**：`migrate` 接收的是底层节点，**不得假设目标版本的新键已经存在**——补默认键发生在迁移之前，但迁移器可能删除了键。所有读取都必须走 `orElse` 兜底。

### 10.8 自定义 ConfigSource

`ConfigSource` 只有两个方法：

```java
public interface ConfigSource {
    Optional<String> read(String name);
    void write(String name, String content);
}
```

单测中用内存实现可以完全避免磁盘 IO：

```java
final class MemorySource implements ConfigSource {
    private final Map<String, String> store = new HashMap<>();
    public Optional<String> read(String name) { return Optional.ofNullable(store.get(name)); }
    public void write(String name, String content) { store.put(name, content); }
}
```

### 10.9 线程安全

`SimpleConfigManager` 所有公开方法都在同一把锁内执行，`reload/save/set/get` 可以来自不同线程。但**业务语义上仍建议只在主线程改配置**——否则"改完立刻读"的时序依赖无法保证。

---

## 11. 数据持久化 Data

`api.data` 包提供"业务对象 ↔ JSON ↔ 原子文件"的完整链路，不依赖 Minecraft，可在 JUnit 中直接验证。

### 11.1 DataCodec

```java
public interface DataCodec<T> {
    JsonElement encode(T value);
    T decode(JsonElement json);
}
```

`decode` 失败应抛 `RuntimeException`，由存储层捕获后跳过该条目（而不是静默返回 null）。

内置编解码器 `DataCodecs`：

| 常量 | 类型 |
| --- | --- |
| `DataCodecs.STRING` | `String` |
| `DataCodecs.INTEGER` | `Integer` |
| `DataCodecs.LONG` | `Long` |
| `DataCodecs.DOUBLE` | `Double` |
| `DataCodecs.BOOLEAN` | `Boolean` |
| `DataCodecs.RAW` | `JsonElement`（深拷贝透传） |

自定义对象示例：

```java
public record PlayerStats(int kills, int deaths) {}

public static final DataCodec<PlayerStats> STATS_CODEC = new DataCodec<>() {
    @Override
    public JsonElement encode(PlayerStats v) {
        JsonObject json = new JsonObject();
        json.addProperty("kills", v.kills());
        json.addProperty("deaths", v.deaths());
        return json;
    }

    @Override
    public PlayerStats decode(JsonElement json) {
        JsonObject obj = json.getAsJsonObject();
        return new PlayerStats(obj.get("kills").getAsInt(), obj.get("deaths").getAsInt());
    }
};
```

强烈建议按字段手写而非 Gson 反射序列化：字段重命名时手写 codec 会在编译期暴露问题，反射序列化则会在运行时悄悄产生 `null`。

### 11.2 PlayerDataStore

```java
PlayerDataStore<PlayerStats> store = new JsonPlayerDataStore<>(dataDir, STATS_CODEC);
```

| 方法 | 说明 |
| --- | --- |
| `get(UUID)` | 读缓存，未命中读盘；无数据/损坏返回 `null` |
| `set(UUID, T)` | 写内存并标记脏 |
| `getOrDefault(UUID, T)` | 空值兜底 |
| `remove(UUID)` | 删内存缓存**并删磁盘文件** |
| `save(UUID)` | 单条落盘 |
| `saveAll()` | 保存全部脏数据；单条失败不影响其他 |
| `unload(UUID)` | 有脏数据先保存，然后清缓存 |

**目录建议**：`<gameDir>/data/<yourmodid>/players/`。每个玩家一个 `<uuid>.json`。

**必须调用 `unload`**：`get` 会把数据读进 `ConcurrentHashMap` 缓存，若玩家离线后不 `unload`，缓存随在线人数历史无限增长。标准做法是在 `PlayerLeaveEvent` 里调用。

```java
YFCoreLib.events().register(PlayerLeaveEvent.class,
        e -> statsStore.unload(e.player().id()));
```

**为什么不用 Attachment / Capability**：两者都只能挂在玩家实体上，玩家离线后实体不存在，无法满足"离线可读"。JSON 文件天然支持离线读写，也让数据可以在服务器迁移时整体带走。

### 11.3 WorldDataStore

```java
WorldDataStore<JsonElement> worldData =
        new JsonWorldDataStore<>(worldDir.resolve("flags.json"), DataCodecs.RAW);
```

| 方法 | 说明 |
| --- | --- |
| `get(String key)` | `Optional<T>`；键不存在或条目损坏返回 empty |
| `set(String key, T)` | 写内存并标记脏 |
| `remove(String key)` | 删键 |
| `save()` | 全量原子写，写前备份 `.bak` |

单文件形态：

```json
{
  "first_launch": true,
  "season": 3
}
```

额外方法：`isDirty()`、`keys()`、`codec()`。

**损坏恢复策略**：

1. 读取主文件；若内容不是合法 JSON 对象，尝试 `.bak`。
2. `.bak` 可用则载入并标记脏（下次 `save()` 会修正主文件）。
3. 两者都不可用时以空表启动并记录 warn——绝不因数据损坏导致启动失败。

**为什么不用 `SavedData`**：`SavedData` 绑定世界存档且依赖 MC 生命周期，无法在纯 Java 单测中验证，也不方便独立进程读取。当前实现优先保证可测试性与离线可读，`SavedData` 适配留作后续可选后端。

### 11.4 AtomicFiles

| 方法 | 说明 |
| --- | --- |
| `writeAtomic(Path, String)` | 临时文件写入后原子移动；失败返回 `false` |
| `backupThenWrite(Path, String)` | 先复制旧文件为 `.bak`，再原子写 |
| `readWithFallback(Path)` | 主文件失败回退 `.bak`，都不可用返回 `null` |
| `readBackup(Path)` | 只读 `.bak` |

写入流程：

```
写 <file>.tmp  →  Files.move(tmp, file, ATOMIC_MOVE)
             ↘ 不支持原子移动时回退普通 move
```

`backupThenWrite` 的完整流程是"旧文件 → `.bak` → 写 `.tmp` → 原子移动"。任意时刻断电/崩溃，主文件要么是旧的完整版本，要么是新的完整版本，不会出现半截文件。

### 11.5 组合示例：完整玩家数据模块

```java
public final class PlayerStatsModule {
    private static PlayerDataStore<PlayerStats> store;

    public static void init(Path dataDir) {
        store = new JsonPlayerDataStore<>(dataDir.resolve("players"), STATS_CODEC);

        YFCoreLib.events().register(PlayerLeaveEvent.class, e -> store.unload(e.player().id()));
        YFCoreLib.events().register(ServerStoppingEvent.class, e -> {
            store.saveAll();
            // 停服后统一释放缓存
        });
    }

    public static PlayerStats statsOf(UUID id) {
        return store.getOrDefault(id, new PlayerStats(0, 0));
    }

    public static void addKill(UUID id) {
        PlayerStats old = statsOf(id);
        store.set(id, new PlayerStats(old.kills() + 1, old.deaths()));
    }
}
```

---

## 12. 玩家会话 PlayerSessionStore

有些数据**不该写盘**：本次战斗的目标、当前 UI 页签、临时 Buff 计时。这类数据只存活于在线期间，用 `PlayerSessionStore`。

### 12.1 接口

| 方法 | 说明 |
| --- | --- |
| `get(UUID)` | 不存在返回 `null` |
| `getOrCreate(UUID)` | 不存在则用工厂创建并写入 |
| `set(UUID, T)` | 覆盖 |
| `has(UUID)` | 是否存在 |
| `remove(UUID)` | 移除（触发 `onEvict`） |
| `online()` | 在线玩家 id 快照 |
| `size()` | 会话数量 |
| `clear()` | 清空全部（停服兜底） |

### 12.2 创建

```java
PlayerSessionStore<CombatState> sessions =
        new SimplePlayerSessionStore<>(YFCoreLib.events(), CombatState::new);
```

带转存回调（离线时把会话数据写入持久化仓库）：

```java
PlayerSessionStore<CombatState> sessions = new SimplePlayerSessionStore<>(
        YFCoreLib.events(),
        CombatState::new,
        (uuid, state) -> statsStore.set(uuid, state.toStats()));
```

### 12.3 生命周期行为

`SimplePlayerSessionStore` 自动挂接三个事件：

| 事件 | 行为 |
| --- | --- |
| `PlayerJoinEvent` | 加入在线集合，用工厂创建初始值 |
| `PlayerLeaveEvent` | 移出在线集合，移除数据并触发 `onEvict` |
| `ServerStoppingEvent` | `clear()`，逐个触发 `onEvict` |

它实现 `AutoCloseable`，`close()` 退订三个事件但**保留**已有数据（由调用方决定是否 `clear()`）。

### 12.4 与 PlayerDataStore 的分工

| 需求 | 选择 |
| --- | --- |
| 跨重启、跨维度、离线可读 | `PlayerDataStore` |
| 仅本次在线有效，无 IO | `PlayerSessionStore` |
| 会话结束需要转存 | 两者配合，用 `onEvict` 桥接 |

不要为了"统一"把所有状态都塞进 `PlayerDataStore`——每次战斗状态变更都写盘会产生大量无谓 IO；也不要让会话缓存无限增长——`SimplePlayerSessionStore` 已通过离线回收解决。

---

## 13. 网络通信 Network

### 13.1 核心类型

| 类型 | 作用 |
| --- | --- |
| `Packet` | 包标记接口，唯一方法 `typeId()` |
| `PacketType<T>` | 包类型声明：id、接收侧、消息类、编解码器、处理器 |
| `PacketHandler<T>` | 处理器，`handle(T packet, PacketContext context)` |
| `PacketContext` | `(UUID sender, Side side, Consumer<Packet> reply)` |
| `NetworkChannel` | 通道：注册类型、发送、判断是否已注册 |
| `SimpleChannelRegistry` | 通道内的类型表，冲突即抛错 |
| `RateLimiter` | 固定窗口限流 |
| `NetworkProvider` | 服务发现接口 |

### 13.2 定义一个包

```java
public record SyncRequest(String field) implements Packet {
    @Override
    public String typeId() {
        return "mymod:sync_request";
    }
}
```

`typeId()` 必须在**实例层面**与 `PacketType.id()` 一致，因为发送时是通过 `packet.typeId()` 反查类型的。

### 13.3 定义编解码器

```java
public final class SyncRequestCodec implements DataCodec<SyncRequest> {
    @Override
    public JsonElement encode(SyncRequest v) {
        JsonObject json = new JsonObject();
        json.addProperty("field", v.field());
        return json;
    }

    @Override
    public SyncRequest decode(JsonElement json) {
        return new SyncRequest(json.getAsJsonObject().get("field").getAsString());
    }
}
```

传输格式是 **UTF 字符串形式的 JSON**，单包上限 1,000,000 字符（`MAX_PAYLOAD`）。超限的包在接收侧读取时抛错并被捕获，不会拖垮连接，但会导致该包丢失。**大块数据请拆包或改用分片协议**。

### 13.4 注册与收发

```java
NetworkChannel channel = YFCoreLib.network("mymod:main");

PacketType<SyncRequest> REQUEST = new PacketType<>(
        "mymod:sync_request",
        Side.SERVER,                 // 客户端 → 服务端
        SyncRequest.class,
        new SyncRequestCodec(),
        (packet, ctx) -> {
            // 已在服务端主线程，可安全访问世界
            ctx.reply(new SyncResponse(compute(packet.field())));
        });

channel.register(REQUEST);
```

`PacketType` 的 `receiver` 语义：

| receiver | 含义 |
| --- | --- |
| `Side.SERVER` | 客户端发给服务端 |
| `Side.CLIENT` | 服务端发给客户端 |
| `Side.COMMON` | 双向 |

### 13.5 发送方法

| 方法 | 方向 | 说明 |
| --- | --- | --- |
| `sendToServer(Packet)` | C→S | 客户端调用 |
| `sendToPlayer(UUID, Packet)` | S→C | 玩家已离线时静默丢弃并记录 debug |
| `broadcast(Packet)` | S→C | 全部在线玩家 |
| `sendToNear(dimension, x, y, z, radius, Packet)` | S→C | 指定维度内半径范围内的玩家 |
| `isRegistered(PacketType<?>)` | — | 判断具体类型是否已注册 |

方向不符会抛 `IllegalArgumentException`，例如对 `receiver=SERVER` 的包调用 `broadcast`。

```java
// 服务端
channel.sendToPlayer(player.getUUID(), new SyncResponse("ok"));
channel.sendToNear("minecraft:overworld", 100, 64, 100, 32, new EffectPacket("glow"));

// 客户端
channel.sendToServer(new SyncRequest("health"));
```

### 13.6 处理上下文

`PacketContext` 是 record，构造时校验 `side` 与 `reply` 非空：

- `sender()`：发送方 UUID。服务端处理 C2S 时非空；客户端处理 S2C 时为 `null`。
- `side()`：接收侧（`SERVER` / `CLIENT`）。
- `reply(packet)`：原路回包。服务端回给来源玩家，客户端回发到服务器。

### 13.7 线程模型

- **处理器总在接收侧主线程执行**。Fabric 桥用 `server.execute(...)` / `client.execute(...)` 切线程；Forge 桥同理。因此 `handle` 里可以直接改世界状态、发消息、调用 MC API。
- **发送也应在主线程调用**。这是 MC 包发送的惯例，跨线程发送在不同加载器上表现不一致。
- 单个包解码或处理失败会被捕获并 warn，**不影响连接**，也不影响后续包。

### 13.8 注册时机

- 通道名与所有 `PacketType.id` 都必须形如 `命名空间:路径`，否则创建/注册时抛 `IllegalArgumentException`。
- 同一个通道内 `id` 重复会抛 `IllegalStateException`。
- **必须在首次发送前完成注册**，未注册的类型发送时抛带指引的异常。
- Fabric 的 S2C 接收器若在客户端入口激活前注册，会进入 pending 队列，由 `YFCoreLibClientFabric` 在 `onInitializeClient` 统一注册。业务侧无需关心，但要知道：**客户端入口未加载时（专用服务器）不会注册 S2C 接收器，这是正确的**。
- Forge 的 `SimpleChannel` 在通道创建时绑定，因此 `YFCoreLib.network(name)` 应在模组初始化期调用并缓存引用。

### 13.9 完整双向示例

```java
// 请求（C→S）
public record RequestPacket(String key) implements Packet {
    public String typeId() { return "mymod:request"; }
}

// 响应（S→C）
public record ResponsePacket(String key, String value) implements Packet {
    public String typeId() { return "mymod:response"; }
}

public final class MyNetwork {
    private static final NetworkChannel CHANNEL = YFCoreLib.network("mymod:main");

    public static void initServer() {
        CHANNEL.register(
                new PacketType<>("mymod:request", Side.SERVER, RequestPacket.class,
                        REQUEST_CODEC, (packet, ctx) ->
                                ctx.reply(new ResponsePacket(packet.key(), lookup(packet.key())))),
                new PacketType<>("mymod:response", Side.CLIENT, ResponsePacket.class,
                        RESPONSE_CODEC, (packet, ctx) -> onResponse(packet))
        );
    }

    public static void request(String key) {
        CHANNEL.sendToServer(new RequestPacket(key));   // 客户端调用
    }
}
```

### 13.10 RateLimiter

固定窗口限流，按 key 统计窗口内次数：

```java
private static final RateLimiter LIMITER = new RateLimiter(5, 1000);  // 每 key 每秒 5 次

if (!LIMITER.tryAcquire("mymod:request:" + sender)) {
    // 超限：直接忽略或断开
    return;
}
```

- `tryAcquire` 是原子的（`compute` + `AtomicInteger`），多线程安全。
- 窗口过期后自动开新窗口。
- **必须定期 `sweep()`**，否则过期的 key 会永久留在 map 里，构成内存泄漏。挂到每 200 刻执行一次即可：

```java
YFCoreLib.events().register(ServerTickEvent.class, e -> {
    if (e.side() == Side.SERVER && serverTicks % 200 == 0) LIMITER.sweep();
});
```

`size()` 可用于监控当前跟踪的 key 数量。

---

## 14. 快照同步 SyncEngine

`SyncEngine` 把"服务端状态变化 → 客户端拿到最新值"抽象成一个可测试的引擎，避免每个功能都手写"比较 + 发包 + 登录补发"。

### 14.1 接口

| 方法 | 侧 | 说明 |
| --- | --- | --- |
| `publish(UUID, S)` | 服务端 | 发布最新快照，按开关决定是否立即发送 |
| `snapshot(UUID)` | 服务端 | 读取当前快照，未发布返回 `null` |
| `onJoin(UUID)` | 服务端 | 玩家加入，按 `syncOnLogin` 重发 |
| `onLeave(UUID)` | 服务端 | 清缓存 |
| `tick()` | 服务端 | 每刻驱动，按 `syncEveryTicks` 周期重发 |
| `subscribe(BiConsumer<UUID, S>)` | 客户端 | 订阅收到的快照；`AutoCloseable` 退订 |
| `receive(UUID, S)` | 客户端 | 平台桥收到快照后调用，分发给监听器 |

配置方法（链式）：`syncOnLogin(boolean)`、`syncOnChange(boolean)`、`syncEveryTicks(int)`，以及对应 getter。

### 14.2 创建

```java
SimpleSyncEngine<PlayerSnapshot> sync = new SimpleSyncEngine<>(
        (playerId, snapshot) -> CHANNEL.sendToPlayer(playerId, new SnapshotPacket(snapshot)));
```

`Sender<S>` 是函数式接口 `void send(UUID playerId, S snapshot)`。把网络依赖注入进来，引擎本身零平台依赖，因此可以纯 JVM 单测。

### 14.3 三种触发模式

| 模式 | 默认 | 行为 |
| --- | --- | --- |
| `syncOnChange` | `true` | `publish` 时若快照与上次**不 equals** 则发送；首次发布也算变化 |
| `syncOnLogin` | `true` | `onJoin` 时若有缓存快照则重发 |
| `syncEveryTicks` | `0` | 大于 0 时每 N 刻重发全部快照；`0` 关闭 |

```java
sync.syncOnLogin(true)
    .syncOnChange(true)
    .syncEveryTicks(100);   // 每 5 秒兜底重发，防丢包导致长期不一致
```

**为什么需要周期重发**：`syncOnChange` 依赖可靠传输。若某次包因超限或异常被丢弃，客户端会长期停留在旧值。周期重发是最终一致性的兜底。

### 14.4 服务端接入

```java
// 玩家加入（就绪后）
YFCoreLib.lifecycle().addHook(player -> sync.onJoin(player.id()));

// 状态变化
public void updatePlayer(ServerPlayer player, PlayerSnapshot snap) {
    sync.publish(player.getUUID(), snap);       // 自动按需发送
}

// 离开
YFCoreLib.events().register(PlayerLeaveEvent.class, e -> sync.onLeave(e.player().id()));

// 每刻驱动
YFCoreLib.events().register(ServerTickEvent.class, e -> {
    if (e.side() == Side.SERVER) sync.tick();
});
```

### 14.5 客户端接入

```java
AutoCloseable sub = sync.subscribe((playerId, snapshot) -> applyToClient(snapshot));
// 页面/模块关闭时
sub.close();
```

客户端侧的 `receive` 由网络包处理器调用：

```java
new PacketType<>("mymod:snapshot", Side.CLIENT, SnapshotPacket.class, SNAPSHOT_CODEC,
        (packet, ctx) -> clientSync.receive(packet.playerId(), packet.snapshot()));
```

### 14.6 快照对象的要求

`SimpleSyncEngine` 用 `equals` 判断是否变化，因此快照类型必须正确实现 `equals`/`hashCode`：

- `record` 自动满足（推荐）。
- 普通类必须手写，否则退化为引用比较，导致"内容没变也重发"。
- 快照必须是**不可变**的。若客户端或服务端持有可变对象并在外部修改，引擎无法感知变化。

---

## 15. 内容注册 DSL

`common` 模块的 `YFRegistries` 把 Fabric 的"立即注册"和 Forge 的"延迟注册"收敛成同一套声明式 API。

> 注意：这部分依赖 Minecraft 类型，只能用于 `common` 或平台模块，不能放进 `api`。

### 15.1 入口

```java
import mcmod.yfcorelib.common.registry.ContentNamespace;
import mcmod.yfcorelib.common.registry.YFRegistries;

ContentNamespace mod = YFRegistries.namespace("mymod");
```

`namespace(modId)` 按 modId 缓存，重复调用返回同一实例。**Forge 必须在模组构造期首次调用**（原因见[第 3 节](#3-初始化时序)）。

### 15.2 ContentNamespace 方法表

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `registrar(ResourceKey)` | `Registrar<T>` | 任意静态注册表 |
| `items()` | `Registrar<Item>` | 物品 |
| `blocks()` | `Registrar<Block>` | 方块 |
| `blockEntities()` | `Registrar<BlockEntityType<?>>` | 方块实体 |
| `entities()` | `Registrar<EntityType<?>>` | 实体 |
| `effects()` | `Registrar<MobEffect>` | 状态效果 |
| `sounds()` | `Registrar<SoundEvent>` | 音效 |
| `particles()` | `Registrar<ParticleType<?>>` | 粒子 |
| `menus()` | `Registrar<MenuType<?>>` | 容器菜单 |
| `tabs()` | `Registrar<CreativeModeTab>` | 创造模式标签页 |
| `enchantments()` | `Registrar<Enchantment>` | 附魔 |
| `recipeSerializers()` | `Registrar<RecipeSerializer<?>>` | 配方序列化器 |
| `recipeTypes()` | `Registrar<RecipeType<?>>` | 配方类型 |
| `block(path, factory, props)` | `BlockEntry<T>` | 注册方块 + 自动同名 BlockItem |
| `block(path, factory, props, withItem)` | `BlockEntry<T>` | 控制是否生成物品 |
| `sound(path)` | `RegistrationRef<SoundEvent>` | 可变音域音效 |
| `sound(path, variableRange)` | `RegistrationRef<SoundEvent>` | 指定音域（固定为 16 格） |
| `menu(path, MenuSupplier)` | `RegistrationRef<MenuType<T>>` | 菜单（VANILLA 特性集） |
| `entity(path, EntityType.Builder)` | `RegistrationRef<EntityType<T>>` | 实体 |
| `id(path)` | `ResourceLocation` | 拼完整 id |

### 15.3 Registrar

| 方法 | 说明 |
| --- | --- |
| `modId()` | 命名空间 |
| `key()` | 注册表 key |
| `id(path)` | 完整 `ResourceLocation` |
| `add(path, Supplier<T>)` | 声明条目；同 path 重复立即抛 `IllegalStateException` |
| `contains(path)` | 是否已声明 |
| `paths()` | 已声明 path（按声明顺序） |
| `size()` | 数量 |

**path 规则**：仅允许小写字母、数字、`_`、`-`、`.`、`/`，不得包含命名空间与冒号。非法 path 在 `add` 时立刻抛错，而不是拖到注册事件阶段——后者堆栈里只有注册表代码，定位极难。

### 15.4 RegistrationRef 与延迟注册

```java
RegistrationRef<Item> ref = mod.items().add("ruby", () -> new Item(new Item.Properties()));

ref.id();          // mymod:ruby
ref.isPresent();   // Forge 注册事件前 false；Fabric 恒 true
ref.get();         // 取值
```

**核心约束：Forge 下不得在模组构造期调用 `get()`。** 此时 `DeferredRegister` 还没有执行注册事件，取到的值无效，会抛错。正确处理方式是：

- 声明阶段只用 `RegistrationRef` 传递引用，不取值。
- 需要实例的代码放到注册完成之后（例如在 `ServerStartedEvent` 或创造模式标签页填充回调里）。

Fabric 下 `isPresent()` 恒为 `true`，因此**不要在业务里用 `isPresent()` 做双端分支**，它只用于调试与 dev 环境断言。

### 15.5 BlockEntry

| 方法 | 说明 |
| --- | --- |
| `path()` | 注册路径 |
| `block()` | 方块引用 |
| `item()` | 物品引用（`Optional`，`withItem=false` 时为空） |
| `get()` | 方块实例（Forge 延迟语义同上） |
| `asItem()` | 物品实例（`Optional`） |

`block()` 内部先声明方块，再用 `() -> new BlockItem(blockRef.get(), ...)` 声明物品。这个 supplier 在**注册阶段**才被调用，因此 Forge 下也是安全的——这是自动配 BlockItem 能同时兼容两端的关键。

### 15.6 完整示例

```java
public final class MyContent {
    private static final ContentNamespace MOD = YFRegistries.namespace("mymod");

    public static RegistrationRef<Item> RUBY;
    public static BlockEntry<Block> RUBY_BLOCK;
    public static RegistrationRef<SoundEvent> RUBY_SOUND;

    /** Fabric 在 onInitialize 调用；Forge 在模组构造函数调用 */
    public static void register() {
        RUBY = MOD.items().add("ruby", () -> new Item(new Item.Properties().stacksTo(16)));

        RUBY_BLOCK = MOD.block("ruby_block", Block::new, BlockBehaviour.Properties.of());

        RUBY_SOUND = MOD.sound("ruby_chime");

        // 自定义注册表（未提供便捷方法的类型）
        MOD.registrar(Registries.POTION)
                .add("ruby_brew", () -> new Potion(new MobEffectInstance(MobEffects.GLOWING, 200)));
    }
}
```

只生成方块、不生成物品（技术性方块）：

```java
MOD.block("ruby_wire", WireBlock::new, BlockBehaviour.Properties.of(), false);
```

### 15.7 常见错误

| 症状 | 原因 |
| --- | --- |
| Forge 启动报 `get() before registration` 类错误 | 在构造期对 `RegistrationRef` 取值 |
| 服务端有物品，客户端没有 | 物品/方块的资源（模型、贴图）未提供；DSL 只管注册，不管资源 |
| `IllegalStateException: 重复注册` | 同一 path 被 `add` 两次，检查是否有多处初始化调用；入口方法应做幂等保护 |
| Forge 注册表里没有条目 | `namespace()` 首次调用晚于模组构造期 |
| `IllegalArgumentException: 非法注册路径` | path 含大写或冒号，去掉命名空间前缀即可 |

---

## 16. 属性修改快照

`common` 模块的 `AttributeModifierSnapshot` 解决两类问题：**修改器叠加导致数值漂移**、**临时加成结束后无法精确还原**。

### 16.1 两种模式

| 模式 | 方法 | 原理 | 适用 |
| --- | --- | --- | --- |
| 修改器模式 | `applyModifier` | 用稳定 UUID 先移除再添加；**baseValue 不动** | 装备/药水/临时加成 |
| 覆盖模式 | `overrideBase` | 首次捕获 baseValue 作为快照，再覆盖 | 变身/等级成长类属性 |

修改器模式的优点：无论施加多少次，`baseValue` 从未被改动，移除后自然回到原始值，**不存在累积误差**。这是绝大多数场景应该选的方式。

覆盖模式的优点：能改变属性上限本身。代价是需要自己管理快照的持久化。

### 16.2 创建

```java
AttributeModifierSnapshot snapshot = new AttributeModifierSnapshot("mymod");
```

每个模组一个实例即可（无状态共享问题，内部按实体 UUID 分组存储）。

### 16.3 修改器模式

```java
snapshot.applyModifier(player, Attributes.MAX_HEALTH, "set_bonus",
        4.0, AttributeModifier.Operation.ADDITION);

snapshot.removeModifier(player, Attributes.MAX_HEALTH, "set_bonus");
```

同一个 `key` 反复施加只会替换，不会叠加：

```java
snapshot.applyModifier(player, Attributes.MAX_HEALTH, "set_bonus", 4.0, ADDITION);
snapshot.applyModifier(player, Attributes.MAX_HEALTH, "set_bonus", 4.0, ADDITION);
// 结果仍是 +4，而不是 +8
```

### 16.4 覆盖模式

```java
snapshot.overrideBase(player, Attributes.MAX_HEALTH, "transform", 40.0);

// 会话内还原
snapshot.restore(player, Attributes.MAX_HEALTH, "transform");

// 跨重启还原：业务自己持久化快照值
playerValues.save(id, args -> {
    snapshot.capturedBase(player, Attributes.MAX_HEALTH, "transform")
            .ifPresent(v -> args.addProperty("saved_health_base", v));
});

// 重启后
snapshot.restoreBase(player, Attributes.MAX_HEALTH, "transform", savedBase);
```

| 方法 | 说明 |
| --- | --- |
| `capturedBase(entity, attr, key)` | 读取首次快照值（`Optional<Double>`） |
| `restore(entity, attr, key)` | 移除修改器 + 还原 baseValue + 清除快照 |
| `restoreBase(entity, attr, key, saved)` | 用外部持久化值还原 |
| `clear(entity)` | 清理该实体全部快照（防泄漏） |
| `clear()` | 清空全部 |
| `modifierId(key)` | 获取稳定 UUID |

### 16.5 稳定 UUID 原理

`SnapshotIds.stable(modId, key)` 用 `UUID.nameUUIDFromBytes("modId:key".getBytes(UTF_8))` 生成。同一个 `key` 在任何一次启动、任何一台服务器上都得到同一个 UUID。

这解决了一个真实问题：如果每次施加都用 `UUID.randomUUID()`，反复施加会在属性实例里堆积大量同名修改器，数值不断叠加，且重启后无法识别哪些是"自己的"修改器。稳定 UUID 让"先移除再添加"成为精确操作。

### 16.6 异常与边界

- 目标实体不持有该属性时抛 `IllegalArgumentException`，消息包含属性注册名与 key，便于定位。
- `overrideBase` 重复调用**不会**覆盖首次快照（`putIfAbsent`），因此多次覆盖后 `restore` 仍回到最初值。
- 实体死亡/卸载前应调用 `clear(entity)`，否则快照表会保留已不存在的玩家 UUID。
- **1.20.1 注意**：属性 API 尚未 Holder 化，必须用 `entity.getAttribute(Attribute)` 拿 `AttributeInstance`，不存在 `Holder<Attribute>` 写法。这也是本类放在 `common`（允许 MC 类型）而不是 `api` 的原因。

---

## 17. 通用注册中心 Registry

`api.registry` 下的 `Registry<T>` 是一个**与 MC 无关**的通用注册表，用于模组自定义内容索引，例如"元素类型""技能定义""任务模板"。

### 17.1 接口

| 方法 | 说明 |
| --- | --- |
| `register(String id, T value)` | 注册；id 重复或已冻结抛 `IllegalStateException` |
| `tag(String id, String tag)` | 给条目打标签（一条可多标签） |
| `get(String id)` | `Optional<T>` |
| `getOrThrow(String id)` | 不存在抛 `NoSuchElementException` |
| `values()` | 按注册顺序 |
| `byTag(String tag)` | 按标签筛选，按注册顺序 |
| `ids()` | 有序 id 集合 |
| `freeze()` | 冻结，之后拒绝写操作 |
| `isFrozen()` | 是否已冻结 |

### 17.2 使用

```java
Registry<ElementType> elements = new SimpleRegistry<>("mymod/elements");

elements.addListener(new RegistryListener<ElementType>() {
    @Override
    public void onRegister(String id, ElementType value) {
        LOG.info("注册元素 {}", id);
    }
});

elements.register("mymod:fire", new ElementType("fire", 1.5));
elements.register("mymod:ice", new ElementType("ice", 0.5));
elements.tag("mymod:fire", "damage");
elements.tag("mymod:fire", "offensive");

elements.freeze();   // 之后任何 register/tag 都抛错

List<ElementType> offensive = elements.byTag("offensive");
```

### 17.3 语义

- 写操作（`register`/`tag`/`freeze`）持 `ReentrantLock`，保证"冲突检测 + 顺序追加"原子。
- 读操作返回不可变快照（`List.copyOf` / `Set.copyOf`），可安全在遍历中注册。
- 监听器回调异常被捕获记录，不影响注册流程。
- `tag` 需要 id 已存在，否则抛 `IllegalArgumentException`；同一 tag 重复添加会被 `addIfAbsent` 去重。
- 冻结后不再允许 `tag`，因为标签索引也是写操作。
- 与 MC 的 `Registry` 同名但无关，注意 import 时区分。

---

## 18. 工具类

### 18.1 Ids

```java
Ids.MOD_ID;          // "yfcorelib"
Ids.id("core");      // "yfcorelib:core"
```

### 18.2 RegistryPaths

纯字符串校验，不依赖 MC：

```java
RegistryPaths.requireNamespace("mymod");      // 通过
RegistryPaths.requireNamespace("MyMod");      // 抛错：仅允许小写字母、数字、_ . -
RegistryPaths.requirePath("ruby_sword");      // 通过
RegistryPaths.requirePath("mymod:ruby");      // 抛错：路径不得含冒号
```

- 命名空间：`[a-z0-9_.-]+`
- 路径：`[a-z0-9_./-]+`

提前校验的价值：错误 id 若拖到注册事件才暴露，堆栈里全是加载器代码，且通常一次只报一条，逐个排查成本极高。

### 18.3 SnapshotIds

```java
UUID id = SnapshotIds.stable("mymod", "speed_bonus");
```

### 18.4 AtomicFiles

见[第 11.4 节](#114-atomicfiles)。

### 18.5 RateLimiter

见[第 13.10 节](#1310-ratelimiter)。

### 18.6 DataCodecs

见[第 11.1 节](#111-datacodec)。

---

## 19. 线程模型与安全约束

### 19.1 线程归属总表

| 组件 | 调用线程 | 内部安全机制 |
| --- | --- | --- |
| `SimpleEventBus` | 调用 `post` 的线程 | `ConcurrentHashMap` + `CopyOnWriteArrayList`；监听器异常隔离 |
| `TickScheduler` | 平台 tick 线程执行任务；提交可任意线程 | COW 列表 + `AtomicBoolean`/`AtomicLong` |
| `SimpleTimerStore` | 任意线程 | `ConcurrentHashMap`，读时惰性清理 |
| `SimpleConfigManager` | 任意线程 | 单一 `synchronized` 锁 |
| `JsonPlayerDataStore` | 任意线程 | `ConcurrentHashMap` + 脏集合 |
| `JsonWorldDataStore` | 任意线程 | `synchronized(lock)`，懒加载 |
| `SimplePlayerSessionStore` | 事件线程（主线程） | `ConcurrentHashMap` + 并发 set |
| `SimpleSyncEngine` | 主线程 | 并发 map + COW；`volatile` 开关 |
| `RateLimiter` | 任意线程 | `compute` + `AtomicInteger` |
| 网络处理器 | **接收侧主线程** | 桥层切线程 |
| `AttributeModifierSnapshot` | 主线程（需操作实体） | `ConcurrentHashMap` |

### 19.2 必须遵守的约束

1. **不要跨线程操作 Minecraft 世界/实体**。库提供的是数据结构层的线程安全，不是 MC API 的线程安全。网络处理器已在主线程，直接写逻辑即可。
2. **不要在静态初始化器里调用 `YFCoreLib.platform()`**。ServiceLoader 依赖平台模块已加载。
3. **Forge 下不要在构造期取 `RegistrationRef` 的值**。
4. **玩家离线必须回收缓存**：`PlayerDataStore.unload`、`PlayerSessionStore`（自动）、`SyncEngine.onLeave`、`AttributeModifierSnapshot.clear`。
5. **限流器必须 `sweep()`**，否则 key 无限增长。
6. **事件监听器要退订**：页面/模块销毁时 `Subscription.close()`，否则全局总线会长期持有已废弃对象。

### 19.3 异常处理原则

库内所有"可能被业务代码污染"的入口都做了隔离：

- 事件监听器抛 `Throwable` → 记录 SEVERE，继续后续监听器。
- 调度任务抛 `Throwable` → 记录 SEVERE，继续本轮其他任务。
- 网络包解码/处理失败 → 记录 warn，连接不受影响。
- 数据条目损坏 → 跳过该条目，其余正常。
- 文件损坏 → 回退 `.bak`，再失败则空表启动。

业务侧应沿用这个原则：**单个数据损坏不应导致整体不可用**。

---

## 20. 诊断命令与自检

### 20.1 命令

| 命令 | 权限 | 作用 |
| --- | --- | --- |
| `/yfcorelib info` | 无 | 输出版本横幅、加载器、MC 版本、配置列表、计时器数量 |
| `/yfcorelib selftest` | 等级 2 | 运行六项运行时自检并逐条输出 PASS/FAIL |

示例输出：

```
YFCoreLib core=yfcorelib:core
loader=fabric mc=1.20.1 loaderVersion=0.19.5 dev=true
配置=无 计时器=0
YFCoreLib 自检：全部通过
[PASS] 事件总线 - 订阅、分发与退订均生效
[PASS] 调度器 - 延迟/重复/取消行为符合预期
[PASS] 配置合并 - 默认合并、用户值保留、未知键保留与写回均通过
[PASS] 玩家会话 - 加入创建、在线集合与离开回收均通过
[PASS] 网络编解码 - 包类型注册与编解码回环通过（实际传输需联机验证）
[PASS] 注册 DSL - 演示物品与方块均已注册，方块物品自动生成
```

### 20.2 自检覆盖范围

| 项 | 验证内容 |
| --- | --- |
| 事件总线 | 订阅、分发、退订后不再命中 |
| 调度器 | `delay(1)` 与 `repeat(1)` 同刻到期、取消后停止 |
| 配置合并 | 用户值不被默认值覆盖、未知键保留、写回可读 |
| 玩家会话 | 加入创建、在线集合、离开回收 |
| 网络编解码 | 类型注册 + encode/decode 回环 |
| 注册 DSL | 仅 dev：演示物品/方块进入 `BuiltInRegistries`，方块自动带 BlockItem |

自检全部是纯内存操作，不写磁盘、不发网络包，可以随时在线上服务器执行。

**为什么不用 GameTest 做这件事**：Forge 的 GameTest 需要独立 test 源集与结构模板，且 Fabric 侧没有等价机制，无法做到"双端同一份验收"。运行时自检双端执行同一段代码，才是本库需要的对称验证。

---

## 21. 常见问题

### Q1：`cfg.get(key)` 返回的总是默认值？

检查三点：

1. 是否在 `YFCoreLib.config(spec)` 返回之后调用（它内部会 `reload()`）。若直接 new `SimpleConfigManager`，必须先 `reload()`，否则抛 `IllegalStateException`。
2. 文件里的类型是否匹配。类型不符会整体替换为默认值并 warn，日志里能搜到"配置键类型不符"。
3. `ConfigKey` 是否真的来自该 `spec`。不同 spec 的 key 即使 path 相同也互不通用。

### Q2：Forge 启动时 `DeferredRegister` 报"entry not present"？

在模组构造期就调用了 `RegistrationRef.get()`。改为只持有引用，把取值逻辑移到 `ServerStartedEvent` 之后。

### Q3：客户端收不到 S2C 包？

依次排查：

1. `PacketType.receiver` 是否为 `CLIENT` 或 `COMMON`。
2. 通道上的 `register(...)` 是否在发送前完成。
3. 客户端入口是否已激活（专用服务器不会注册 S2C 接收器，属正常）。
4. 包体是否超过 1,000,000 字符上限——超限会在接收侧解码失败并 warn。

### Q4：事件监听器没被触发？

确认 `post` 的是**精确类型**。子类事件不会触发父类监听器，这是刻意设计。改用父类类型注册，或分别注册。

### Q5：计时器重启后归零？

`TimerPersistence` 只在服务端启动/停止/每 6000 刻保存。如果测试时直接杀进程（非正常停服），最后一次自动保存之后的数据会丢失。这是可接受的取舍：每 5 分钟落盘一次，避免每刻写盘。

### Q6：玩家数据内存持续增长？

`PlayerDataStore.get` 会缓存。必须在 `PlayerLeaveEvent` 里 `unload`。会话存储已自动回收，但持久化仓库不会。

### Q7：配置被改回默认值？

大概率是同一 spec 名被两个不同的 `ConfigBuilder` 定义。`YFCoreLib.config` 按 `spec.name()` 缓存，同名 spec 只会用第一个。确保每个配置名全局唯一。

### Q8：附属模组能直接用 `YFCoreLib.MOD_ID` 吗？

它固定是 `"yfcorelib"`。附属模组应使用自己的 modId 创建命名空间与配置，只在需要调用库能力时使用本库门面。

### Q9：为什么配置不支持列表/枚举/对象？

后端是 JSON 标量 + 严格类型校验。列表可拆成多个数值键，枚举用 `String` + 业务侧解析（并对未知值给默认分支）。这样做的好处是配置损坏时能精确到键级别恢复，而不是整个对象被替换。

### Q10：库的 api 模块能单独用吗？

可以。`api` 是纯 Java，除了 Gson/slf4j（compileOnly）外无依赖。事件总线、调度器、计时器、配置内核、JSON 仓库、同步引擎都可以在任何 Java 项目中独立使用与单测，不需要 Minecraft。

---

## 附录：包结构索引

```
mcmod.yfcorelib.api
├── YFCoreLib            门面
├── Side                 执行侧
├── Ids                  modId 与 id 拼接
├── config/              配置内核
│   ├── ConfigSpec / ConfigKey / ConfigBuilder / SimpleConfigSpec
│   ├── ConfigManager / SimpleConfigManager
│   ├── ConfigNode / JsonConfigNode / ConfigSource / FileConfigSource
│   └── ConfigMigrator / GsonFormats
├── data/                持久化
│   ├── DataCodec / DataCodecs
│   ├── PlayerDataStore / JsonPlayerDataStore
│   ├── WorldDataStore / JsonWorldDataStore
│   ├── PlayerSessionStore / SimplePlayerSessionStore
│   ├── AtomicFiles / SnapshotIds / GsonIo
├── event/               事件与生命周期
│   ├── EventBus / SimpleEventBus
│   ├── LifecycleQueue / LifecycleHook
│   ├── PlayerRef
│   └── Server*/Player* 事件
├── network/             网络
│   ├── Packet / PacketType / PacketHandler / PacketContext
│   ├── NetworkChannel / NetworkProvider / SimpleChannelRegistry
│   ├── RateLimiter / PacketIds
├── platform/            平台抽象
│   ├── Platform / PlatformProvider / Services / ModInfo
├── registry/            通用注册中心
│   ├── Registry / SimpleRegistry / Registration / RegistryListener
│   └── RegistryPaths
├── scheduler/           调度
│   ├── Scheduler / TickScheduler / ScheduledTask / Tickable
│   └── TimerStore / SimpleTimerStore
└── sync/                快照同步
    ├── SyncEngine / SimpleSyncEngine

mcmod.yfcorelib.common
├── attribute/AttributeModifierSnapshot
├── registry/            内容注册 DSL
│   ├── YFRegistries / ContentNamespace / Registrar / AbstractRegistrar
│   ├── RegistrationRef / RegistrarFactory / BlockEntry
├── core/TimerPersistence
├── command/YFCoreCommand
├── diagnostic/SelfTest
└── demo/DemoContent     仅 dev
```
