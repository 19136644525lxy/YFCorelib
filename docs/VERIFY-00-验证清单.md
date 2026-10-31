# YFCoreLib 验证清单（0.1.0）

本文件最初写于"所有修改只经过静态审查"的阶段（当时开发环境的进程创建失败，`[exit code: 3221225794]` / `0xC0000142`，Gradle 一次也没跑起来）。**现在构建、单测与双端真机验证都已实际执行过**，结果见下面的「验证记录」；其余条目保留为"重跑时照着核对"的清单。

## 0. 验证记录

| 时间 | 项 | 结果 |
| --- | --- | --- |
| 2026-10-31 | `gradlew.bat :api:test :common:build :fabric:build :forge:build` | `BUILD SUCCESSFUL`，`30 actionable tasks: 27 executed, 3 up-to-date` |
| 2026-10-31 | `:api:test` | **221** 个用例全绿（`api/build/test-results/test/*.xml`，33 个套件，0 failure / 0 error） |
| 2026-10-31 | `MixinConfigValidator` 自检 | Fabric 生产环境 `/yfcorelib selftest` 6 项全 PASS，含 `refmap=yfcorelib.refmap.json` |
| 2026-10-31 | Forge Mixin 接线 | 首次 FAIL（`mods.toml` 的 `[[mixins]]` 不被 Forge 解析 → 配置从未加载）；改为 `loom { forge { mixinConfig ... } }` 后**双端全部通过** |

---

## 1. 构建与静态检查

```bat
gradlew.bat :api:test :common:build :fabric:build :forge:build
```

期望：

| 检查 | 期望结果 |
| --- | --- |
| `:api:test` | **221** 个用例全部通过（评审前为 107；缺陷修复批次 +22，M5 第一批 +48，M5 第二批 +33，M5 收尾（文本度量）+7，接入示例批次 +4） |
| `:api:checkImportBoundary` | 通过（`api` 不得 import `net.minecraft.*` / `net.fabricmc.*` / `net.minecraftforge.*`） |
| `:common:checkImportBoundary` | 通过（`common` 不得 import 后两者） |
| `:fabric:build` / `:forge:build` | `BUILD SUCCESSFUL`，产出 `*-fabric.jar` / `*-forge.jar` |

若要一次性看全：`gradlew.bat build`（含 `check`，会带上两个导入边界任务）。

**不要加 `clean`** —— 项目规则里 `clean` 需要额外的文件锁，会干扰并行构建。

## 2. 编译失败时优先检查的位置

如果构建报错，按这个顺序看，这几处是本轮改动面最大、最容易出现符号不匹配的地方：

1. **`ServerTickEvent` 的签名变更**：从 `record ServerTickEvent(Side side)` 改为 `record ServerTickEvent(){}`，新增 `ClientTickEvent`。已改的调用点是
   `FabricLifecycleBridge.java:30`、`ForgeLifecycleBridge.java:33`、`ForgeLifecycleBridge.java:43`、
   `YFCoreLibClientFabric.java:17`、`common/core/TimerPersistence.java:44`。
   仓库内已用 grep 确认没有残留的 `ServerTickEvent(Side` 或 `.side()`，但如果 api 之外还有外部使用方，这是**不兼容变更**。
2. **`ConfigKey.of(...)` 引用 `SimpleConfigManager.VERSION_KEY`**：`VERSION_KEY` 是 `public static final String`，同包引用，理论上无问题；若报错看可见性。
3. **`SimpleConfigSpec.Builder.build()` 的新校验**：注释指向未定义的键、`version < 0` 都会抛 `IllegalArgumentException`。仓库内没有生产代码用 `ConfigBuilder.comment(...)`，但外部注册的 spec 可能被拒。
4. **`SimpleConfigManager.set(...)` 的类型校验**：声明 `Integer` 的键传 `String` 现在直接抛错。
5. **`Platform.worldDir()`**：新增 `default` 方法，两端实现都在；若第三方实现了 `Platform` 接口，默认方法不影响它们。
6. **`LifecycleHook.onPlayerGone`**：`default` 方法，函数式接口性质不变，原有 lambda 仍可用。

## 3. 双端运行验证

```bat
gradlew.bat :fabric:runServer
gradlew.bat :fabric:runClient
gradlew.bat :forge:runServer
```

进入游戏后执行：

| 步骤 | 期望 |
| --- | --- |
| `/yfcorelib info` | 输出横幅 `YFCoreLib core=yfcorelib:core`、loader 名、MC 版本、`dev=`、已创建的配置名与计时器数量（该命令需要权限等级 2） |
| `/yfcorelib selftest` | 开发环境输出 **7** 条全 `[PASS]`（事件总线 / 调度器 / 配置合并 / 玩家会话 / 网络编解码 / **Mixin 配置** / 注册 DSL）；正式环境为 **6** 条（最后一条"注册 DSL"只在开发环境追加） |
| 注册 DSL | 开发环境下 `demo_item`、`demo_block` 存在于 `BuiltInRegistries`，且方块自动带 `BlockItem`；正式环境不注册 |
| 死亡事件 | 进服后用 `/kill` 或摔死，日志里应能看到 `PlayerDeathEvent` 的订阅回调被触发（可临时加一行日志确认）；**只有服务端玩家**会触发，客户端仅提示音 |

## 4. 需要重点人工确认的行为（本轮修复的主战场）

1. **客户端/服务端刻事件已分离**
   监听 `ServerTickEvent` 的代码不应在客户端刻被调用；监听 `ClientTickEvent` 的代码不在服务端刻被调用。
   `TimerPersistence` 已改为只听 `ServerTickEvent` 并用计数器（不是 `event.side()`）决定自动保存。

2. **计时器文件跟着存档走**
   启动服务器后应生成 `<存档目录>/data/yfcorelib/timers.json`。
   旧版本遗留的 `gameDir/data/yfcorelib/timers.json` 会在启动时被读取一次（日志有"发现旧版计时器文件"提示），下一次保存写入新位置。
   开第二个存档时两者不应互相覆盖。

3. **配置加载失败后拒绝写回**
   把某个配置文件改成非法 JSON 启动 → 日志报错、内存退回默认值；此时任何 `save()` 都应抛
   `IllegalStateException("配置加载失败，拒绝写回以免覆盖用户文件…")`，原文件保持原样。
   手动修好后 `reload()` 成功即可恢复写回。

4. **`Side.COMMON` 调度器直接报错**
   `YFCoreLib.scheduler(Side.COMMON)` 抛 `IllegalArgumentException`，不再是"拿到实例但永不执行"。

5. **网络包上限双端一致**
   发送超长 payload 时应在**发送侧**抛编码异常，而不是被 `writeUtf` 默认 32767 静默截断。
   Fabric 与 Forge 侧上限相同（`MAX_PAYLOAD`）。

6. **生命周期钩子可退订**
   `LifecycleQueue.addHook(...)` 返回 `EventBus.Subscription`，`close()` 后不再收到 `onPlayerReady` / `onPlayerGone`；
   重复 `close()` 安全；同一玩家重复 `playerGone` 只通知一次。

7. **Forge 注册引用**
   在模组构造期取 `RegistrationRef.get()` 应抛带条目 id 的 `IllegalStateException`，而不是裸 NPE。

8. **`ContentNamespace` 的方块物品**
   如果 BLOCK 未先于 ITEM 注册，生成 BlockItem 时应抛带指引的异常（提示改用 `withItem=false`），而不是空指针。

## 5. 本轮已修但无法自证的正确性项（建议补测）

这些属于并发/边界问题，静态审查通过不等于行为正确：

- `RateLimiter` 的窗口判定与计数在同一个 `compute` 内；系统时钟回拨不影响窗口过期。
- `SimpleSyncEngine.publish` 发送失败时**不**记录快照（下次仍会重发）。
- `JsonPlayerDataStore.unload` 写盘失败时保留内存数据。
- `JsonWorldDataStore.save` 未修改（`dirty == false`）时不写盘。
- `AtomicFiles` 并发写入不互相踩临时文件。
- `DataCodecs` 严格解码：`BOOLEAN.decode("1")`、`INTEGER.decode(3.9)` 抛异常。

## 6. M5 第一批（数学/随机/反射）单独验证

这一批是**新增能力**，不是修 bug，验证方式以单测为主、不需要进游戏：

| 项 | 期望 |
| --- | --- |
| `Easing` | 12 条内置曲线在 `t=0` 得 0、`t=1` 得 1；越界输入与 `NaN` 被夹紧；`EASE_OUT_BACK` 峰值 > 1 |
| `Color` | `argb ↔ Color` 往返无损；分量越界抛 `IllegalArgumentException`；`lerp` 端点精确、中间值四舍五入 |
| `Rng` | 同种子同序列；`derive("a")` 可复现且与 `derive("b")` 不同；`Rng.at(seed,x,z)` 对同一坐标恒定 |
| `WeightedTable` | 3:1 权重下 2 万次抽取中重项占比 75%±2%；空表 `pick` 返回空、`pickOr` 返回兜底；权重 ≤ 0 抛错 |
| `Noise` | 同种子确定；值域 `[-1,1]`；相邻采样连续；单层 fBm 等于 `noise2` |
| `ReflectionCache` | 能读父类私有字段；成员名拼错时异常消息列出候选名；形似混淆名会额外提示；同一成员缓存复用同一 `Field` 实例 |

工具类都只依赖 JDK，因此**在 `api` 模块内就能完整覆盖**，不涉及双端差异。

## 7. M5 第二批（Mixin 基建 / 软依赖）单独验证

同样只需跑 `:api:test`，不需要进游戏——本库自身**不注册任何 Mixin**，这两块是给业务模组用的基建。

| 项 | 期望 |
| --- | --- |
| `MixinCallbacks.fire` | 事件送达监听器；订阅 `close()` 后不再收到；`setEnabled(false)` 期间丢弃；监听器抛异常**不冒回** `fire` 调用方 |
| `MixinConfigParser` | 完整配置各字段解析正确；缺 `package`、`mixins` 不是数组、元素不是字符串、非法 JSON、顶层不是对象 → 全部抛 `IllegalArgumentException` |
| `MixinConfigValidator` | 类不存在 / refmap 不存在 / plugin 不存在 / 同一条目重复声明 / 空壳配置 → 各自产出 `Problem`；干净配置返回空列表；资源缺失返回 `Problem` 而非抛异常 |
| `SoftDependency` | `allLoaded` 全在才真、`anyLoaded` 一个即可；空数组前者真后者假；`null`/空白 id 抛 `IllegalArgumentException` |
| `SimpleCapabilityRegistry` | 注册后可取回同一实例；未注册返回空；重复注册抛 `IllegalStateException`；不同类型查同一 id 抛 `IllegalArgumentException`；`ids()` 是快照 |

> 真正容易出问题的是"把 Mixin 接进 jar"这一步（`mods.toml` / `fabric.mod.json` 的 `mixins` 字段、refmap 生成）。这一步本批**没有做**，理由见 §8。

## 7.5 M5 收尾（文本度量）单独验证

`TextMeasure` / `TextLayout` 是纯接口与纯数据，`:api:test` 已用假度量覆盖；有一项必须在游戏里看一眼：

| 项 | 期望 |
| --- | --- |
| `TextLayout.of(measure, text, maxWidth)` | 返回的 `width <= maxWidth`；`height == lineCount * measure.lineHeight()` |
| `TextLayout` 参数校验 | `maxWidth <= 0`、负 `width`/`height`、null 参数分别抛 `IllegalArgumentException` / `NullPointerException` |
| `lines()` 不可变 | 返回 `List.copyOf` 结果，`add` 抛 `UnsupportedOperationException` |
| `FontTextMeasure`（**需进游戏**） | 在客户端里对一段长中文文本按 240 像素折行，行数与换行位置跟原版 `Font#split` 一致；每行是**拍平后的纯文本**（样式会丢）；专用服务器上**不要**构造它（会因 `net.minecraft.client.gui.Font` 抛错） |

## 7.6 Mixin 接线验证（**必须进游戏**）

这是本轮唯一"改的是启动期行为"的部分：Mixin 接线失败的表现是**静默的**——游戏照常启动，只是事件永远不触发。所以必须实测。

| 项 | 期望 |
| --- | --- |
| 启动日志（Fabric） | 无 `Mixin apply failed` / `InvalidInjectionException` / `No refMap loaded`；有 `Mixing LivingEntityDeathMixin into ...` 字样 |
| 启动日志（Forge） | 同上。注意 Forge 的声明位置是 `forge/build.gradle` 的 `loom { forge { mixinConfig "yfcorelib.mixins.json" } }`，**不是** `mods.toml` 的 `[[mixins]]`（后者在 Forge 1.20.1 上不被解析，2026-10-31 实测过：配置在 jar 里但从未被加载，游戏照常启动、注入静默不发生） |
| `/yfcorelib selftest` | 第 6 项 `[PASS] Mixin 配置 - 1 个 Mixin 类声明有效且已注入（yfcorelib.mixins.json，refmap=yfcorelib.refmap.json）`；**生产环境**若 refmap 没被回填、或 `LivingEntity` 上找不到注入方法，这一项会 FAIL 并直接说明原因（开发环境跳过 refmap 断言，但仍查注入方法）。**2026-10-31 已在 Fabric 与 Forge 生产环境双端实测通过** |
| 死亡触发 | 服务端玩家死亡时，订阅 `PlayerDeathEvent` 的回调被调用一次；`e.player().id()` 等于死者 UUID；此时 `e.player().isOnline()` 仍为 true。**注意：selftest 只证明"注入方法在目标类上"，不证明"事件真的发出来"，这一条仍需手动死一次验** |
| 不误伤 | 非玩家生物死亡**不**触发；专用服务器上没有客户端相关报错 |
| refmap | 打包后的 jar 里 `yfcorelib.mixins.json` 含 Loom 注入的 `refmap` 字段；开发环境 `runServer` 也正常（说明 refmap 生成链路通） |

## 8. 明确未做的事

- `fabric` / `forge` 模块没有配置 `publishing`，`-fabric.jar` / `-forge.jar` 只作为 Release 附件，不按 Maven 坐标发布。
- `DemoContent` 没有贴图与模型（dev 专用，正式环境不注册）。
- M5 第二批的**代码**已完成（`MixinCallbacks` / `MixinConfigParser` / `MixinConfigValidator` / `SoftDependency` / `CapabilityRegistry`）。
- **Mixin 已接进 jar**：`common/src/main/resources/yfcorelib.mixins.json` + `fabric.mod.json` 的 `mixins` 字段 + `forge/build.gradle` 的 `loom { forge { mixinConfig ... } }`，仓库自带一个生产 Mixin `LivingEntityDeathMixin`（`LivingEntity#die` 头部发 `PlayerDeathEvent`，仅服务端玩家）。`refmap` 字段**故意不手写**，由 Loom 打包时注入——如果以后换成非 Loom 构建，必须自己生成。2026-10-31 已在双端生产环境实测通过 §7.6 的 selftest 项。
- 本库自身的 Mixin 只有上面这一个：刻意不提供"可取消"语义（`die` 时判定已完成，取消只会留下状态不一致的实体；要拦截请自己注入 `hurt`）。
- M5 的 `TextMeasure` / `TextLayout` 已实现（`api/text/`，纯接口与纯数据，7 个用例），基于原版 `Font` 的 `FontTextMeasure` 放在 `common/text/`；但它**只做了"度量与折行"，没有做渲染**（画字、换行符处理、描边/背景都属于使用方）。
- `FontTextMeasure` 只保证与 `Font#split` 行为一致，**未在真机跑过**，需要按 §7.5 最后一行在客户端验证一次。
- `JsonConfigNode.raw()` / `read()` / `set(String, JsonElement)` 仍对外暴露 Gson 类型，供"需要原始结构"的场景使用。

## 9. 变更面（便于 review）

| 模块 | 涉及文件 |
| --- | --- |
| 构建 | `common/build.gradle`（POM 裁剪 + `loom { mixin { defaultRefmapName } }` 以生成 refmap）、`fabric.mod.json`、`mods.toml`（版本范围收紧 + `mixins` 声明） |
| api 事件 | `ServerTickEvent`、新增 `ClientTickEvent`、`LifecycleQueue`、`LifecycleHook`、`SimpleEventBus` |
| api 调度 | `SimpleTimerStore`、`TickScheduler` |
| api 注册 | `SimpleRegistry`、`RegistryPaths`、`PacketIds` |
| api 配置 | `SimpleConfigManager`、`ConfigManager`、`ConfigKey`、`SimpleConfigSpec`、`JsonConfigNode`、`FileConfigSource` |
| api 数据 | `AtomicFiles`、`JsonPlayerDataStore`、`JsonWorldDataStore`、`SimplePlayerSessionStore`、`DataCodecs`、`SnapshotIds` |
| api 网络/同步 | `RateLimiter`、`SimpleChannelRegistry`、`SimpleSyncEngine` |
| api 平台 | `Platform`（`worldDir`）、`Services`、`ModInfo`、`Ids`、`YFCoreLib` |
| **api 新增（M5 第一批）** | `api/math/`：`Easing`、`Color`、`Rng`、`SimpleRng`、`Rngs`、`WeightedTable`、`Noise`；`api/util/ReflectionCache` |
| **api 新增（M5 第二批）** | `api/mixin/`：`MixinCallbacks`、`MixinConfig`、`MixinConfigParser`、`MixinConfigValidator`；`api/interop/`：`SoftDependency`、`CapabilityKey`、`CapabilityRegistry`、`SimpleCapabilityRegistry` |
| **api 新增（M5 收尾）** | `api/text/`：`TextMeasure`、`TextLayout` |
| **Mixin 接线** | `common/.../mixin/LivingEntityDeathMixin`（新增）、`common/src/main/resources/yfcorelib.mixins.json`（新增）、`api/.../event/PlayerDeathEvent`（新增）、`common/.../event/PlayerRefs`（新增）、`fabric.mod.json` 加 `mixins`、`forge/build.gradle` 加 `loom { forge { mixinConfig ... } }`、`SelfTest` 加「Mixin 配置」项 |
| **文档** | `docs/EXAMPLES.md`（新增）、`README.md`（快速开始 + 文档三条）、`API.md`（§6.2/§6.4/§10/§18.9/§20） |
| **common 新增（M5 收尾）** | `common/text/FontTextMeasure`（依赖客户端 `Font`） |
| common | `TimerPersistence`、`ContentNamespace`、`AbstractRegistrar`、`BlockEntry`、`YFCoreCommand`、`SelfTest` |
| fabric | `FabricPlatform`、`FabricNetworkChannel`、`FabricLifecycleBridge`、`YFCoreLibClientFabric` |
| forge | `ForgePlatform`、`ForgeNetworkChannel`、`ForgeLifecycleBridge`、`ForgeRegistrarFactory`、`ForgeRegistrationRef` |
| 测试 | 新增 `ConfigKeyTest`、`YFCoreLibTest`；扩充 `IdsTest`、`RegistryPathsTest`、`DataCodecsTest`、`SimpleTimerStoreTest`、`SimpleRegistryTest`、`LifecycleQueueTest`、`SimpleConfigManagerTest` |
| 测试（M5 第一批） | 新增 `EasingTest`、`ColorTest`、`RngTest`、`WeightedTableTest`、`NoiseTest`、`ReflectionCacheTest`（48 个用例） |
| 测试（M5 第二批） | 新增 `MixinCallbacksTest`、`MixinConfigParserTest`、`MixinConfigValidatorTest`、`SoftDependencyTest`、`SimpleCapabilityRegistryTest`（33 个用例） |
| 测试（M5 收尾） | 新增 `TextLayoutTest`（7 个用例，用假度量，不需要游戏） |
| 文档 | `API.md`、`.trae/specs/engineering-test-infra/spec.md`、本文件 |
