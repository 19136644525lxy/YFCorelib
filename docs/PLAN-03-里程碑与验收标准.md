# PLAN-03 里程碑与验收标准

> 适用：v1（LTS 1.20.1，Fabric + Forge，Java 17）
> 原则：一次只推进一个里程碑；每批必须有"可运行 + 可单测 + 可回滚"的交付物。

---

## 0. 总览

| 里程碑 | 主题 | 主要受益仓库 |
|---|---|---|
| M0 | 骨架、元数据与模板清理 | 全部 |
| M1 | 平台抽象与基础工具 | 全部 |
| M2 | 注册、调度、事件桥 | Nyctocosm、Pdopn、DCC、Mild |
| M3 | 配置、持久化、属性快照 | Pdopn、Frilien、Calendar、Nyctocosm |
| M4 | 网络与数据同步 | Calendar、Frilien、Nyctocosm、DCC |
| M5 | Mixin、文本、数学、反射、互操作 | Mild、Subtitle、HtmlCraft、Punctuation |
| M6 | 双端对齐与试点接入 | Pdopn + Nyctocosm 试点 |
| M7 | CURRENT 26.2 线 | Frilien、Mild、Calendar（v2） |

---

## M0 骨架、元数据与模板清理

**交付物**

1. `settings.gradle` 拆为 `api` / `common` / `fabric` / `forge`。
2. Architectury Loom 接入，`common` 与两平台可编译。
3. `gradle.properties` 锁定 release 版本（去掉 `loom-remap 1.17-SNAPSHOT`）。
4. `fabric.mod.json` 与新增的 `mods.toml` 全部替换占位元数据。
5. 删除 `ExampleMixin` 及其注册；清理 MDK 英文注释与模板 README。
6. `YFCoreLib.id()` 迁到 `api.Ids`，入口类分平台。

**验收**

- `gradlew :api:build :common:build :fabric:build :forge:build` 通过（不加 clean）。
- 两个平台各自能进入开发环境并打印启动横幅。
- `api` 模块 grep 无 `net.minecraft`/`net.fabricmc`/`net.minecraftforge`。
- `common` grep 无加载器 import。
- 无 `ExampleMixin`、无 `MinecraftServer.loadWorld` 注入。

**风险与退路**

- Architectury 接入失败时，退回"纯多模块 + ServiceLoader"，不引入版本预处理。

---

## M1 平台抽象与基础工具

**交付物**

1. `Platform` / `PlatformProvider` / `Services`。
2. `FabricPlatform`、`ForgePlatform`（loader 名、版本、目录、`isModLoaded`）。
3. `Ids`、`ModInfo`、软依赖 `SoftDependency`。
4. 分模块日志与启动依赖清单打印。

**验收**

- 同一份调用代码在两平台输出一致（除 loader 名/版本）。
- `isModLoaded("pehkui")` 在 Random Height 环境返回 true，在无 Pehkui 环境返回 false 且不抛异常。
- `Ids` 单测：命名空间、翻译键、贴图/模型路径。
- `api` 零 MC 依赖。

---

## M2 注册、调度、事件桥

**交付物**

1. `Registry<T>` + `Registration<T>`（冲突检测、标签索引、冻结、并发安全）。
2. 注册 DSL：item/block/entity/effect/sound/particle/menu/tab/enchantment/recipe。
3. `Scheduler` + `ScheduledTask` + `Tickable` + `TimerStore`。
4. `EventBus` + 生命周期事件 + `LifecycleHook` 延迟加载队列。
5. Mixin→事件桥的最小实现（仅用于生命周期事件）。

**验收**

- 重复 id 注册立即抛错；冻结后写入被拒绝。
- 延迟/周期任务可取消，取消后不再执行；服务端与客户端调度互不干扰。
- 计时器跨世界重启恢复（写入存档后重载验证）。
- 事件订阅可关闭，关闭后不再收到事件；并发注册/触发无 `ConcurrentModificationException`。
- 单测覆盖：注册冲突、冻结、队列顺序、取消。

**试点**

- Nyctocosm 的 `SpellRegistry` 改用 `Registry<T>`（不改业务行为）。
- Pdopn 的死亡回调改用 `EventBus`（保留原方法签名）。

---

## M3 配置、持久化、属性快照

**交付物**

1. 配置内核：`ConfigSpec`/`ConfigKey`/`ConfigBuilder`/`ConfigManager`/`ConfigMigrator`，JSON 后端。
2. `PlayerDataStore<T>`：Fabric Attachment / Forge Capability 两套实现。
3. `WorldDataStore<T>`：两端存档适配，原子写入与损坏回退。
4. `AttributeModifierSnapshot`：稳定 UUID + 修改前 baseValue + 精确还原。

**验收**

- 配置合并：用户已改值不被覆盖；新增默认键自动补齐；未知键保留；版本迁移只执行一次。
- 类型错误配置项整体替换并记录 warn，不导致启动失败。
- 玩家数据：下线/重启/维度切换后仍可读；损坏条目跳过并记录。
- 世界数据：写入中断后能从 `.bak` 回退。
- 属性快照：连续施加/还原 100 次，baseValue 不漂移。
- 单测覆盖：合并器、迁移、序列化、快照。

**试点**

- Pdopn：`PdopnConfig`、`PlayerDataStore`、`PdopnEntityModifier` 三处替换。
- Frilien：`capability` 与 `attachment` 两套玩家数据收敛为一份 `PlayerDataStore`。

---

## M4 网络与数据同步

**交付物**

1. `Packet` / `PacketType` / `PacketHandler` / `PacketContext` / `NetworkChannel`。
2. 两端注册与序列化适配；C2S/S2C/广播/按维度/按距离。
3. `SyncEngine<S>`：登录同步、变更同步、定时同步、增量快照。

**验收**

- 注册冲突立即抛错；未注册包发送时明确报错。
- C2S→服务端→S2C 回环测试通过。
- 网络线程不直接改世界状态，统一切主线程执行。
- 登录即同步、变更即同步、定时同步三种模式均可单独关闭。
- 单测覆盖：编码解码、限流、快照比较。

**试点**

- Calendar：`CalendarSyncPacket` + `CalendarClientCache` + `AutoSyncScheduler` 收敛为 `SyncEngine`。
- Frilien：`SyncPlayerDataPacket` 改用 `SyncChannel`。

---

## M5 Mixin、文本、数学、反射、互操作

**交付物**

1. `MixinCallbacks`（并发安全、可注销）。
2. `AccessHelper` + `ReflectionCache`（失败可见）。
3. `MixinConfigValidator`（package/类/配置一致性校验）。
4. `TextMeasure`（真实字体度量、CJK、折行、省略、缓存）。
5. `Easing`/`Color`/`Rng`/`WeightedRandom`/`Noise`。
6. `SoftDependency` + `CapabilityRegistry`。

**验收**

- 校验器能对"配置指向旧包名/类不存在/生产 Mixin 未注册"给出 error（用 Subtitle 1.21.10 的结构做 fixture）。
- 反射访问不存在字段时抛 `ReflectionException` 并记录 owner/name，不静默返回默认值。
- 文本度量：中英混排折行不越界；相同文本二次调用命中缓存。
- 数学工具单测：缓动端点、颜色混合、权重分布（固定种子）。
- 缺失软依赖时返回 `Optional.empty()`，不抛 `NoClassDefFoundError`。

**试点**

- Mild：16 个 entity mixin 逐步改用统一访问助手。
- Subtitle：接入 `MixinConfigValidator`，修掉 1.21.10 配置错位。
- HtmlCraft/Calendar：`TextMeasure` 替换估算式排版。

---

## M6 双端对齐与参考样本验证

**定位**：本库面向后续新模组，不改造现有仓库；参考样本只用于验证能力是否可用。

**交付物**

1. Fabric 与 Forge 行为对齐表（每项能力逐一验证）。
2. 示例模组：每个能力至少一个可运行示例。
3. 在参考样本中做最小接入验证，不要求替换其现有实现。

**验收**

- 同一份 `common` 源码在两端构建通过，运行时行为一致。
- 参考样本能编译并运行接入代码，且业务代码中没有加载器分支。
- 参考样本的 `mods.toml` / `fabric.mod.json` 依赖声明与实际使用一致（作为规范示例）。
- `gradlew :api:test :common:build :fabric:build :forge:build` 全绿。

**验证顺序（参考样本，不是改造目标）**

1. **Pdopn（Fabric）**：配置、玩家持久化、调度、回调桥、属性快照——覆盖 M2/M3 大半能力。
2. **Nyctocosm（Forge）**：注册中心、网络、boss/法术 tick 调度——验证 Forge 端与大规模注册。

---

## M7（v2）CURRENT 26.2 线

**触发条件**：M6 完成，且后续 26.2 新模组需要共享能力。

**范围**：只移植已验证的能力（platform、config、registry、schedule、data、text、math），不一次性全量移植；Java 25、Fabric + NeoForge。

**不做**：1.21.x 独立实现；跨版本 Stonecutter 预处理。

---

## 参考样本中的已知问题（仅记录，不在本项目修复）

| # | 仓库 | 问题 | 证据 | 备注 |
|---|---|---|---|---|
| 1 | Subtitle 1.21.10 | `fabric.mod.json` 引用旧包名 mixin 配置，生产 Mixin 实际不生效 | 已核实 | M5 校验器 + 手工修配置 |
| 2 | DCC | `mods.toml` 未声明 `dcrapi` / `kotlinforforge`，缺前置直接崩 | 已核实 | 独立修复 |
| 3 | Calendar | 月份上界 12 与配置允许任意月数冲突 | 沿用 v1 分析，待复核 | 独立修复 |
| 4 | Pdopn | 指令权限校验、Netty 线程与服务端线程并发写 | 沿用 v1 分析，待复核 | M3/M4 |
| 5 | 多仓库 | `ExampleMixin` 与 MDK 注释残留 | 已核实 | M0 各自清理 |
| 6 | Subtitle | 四份 Fabric 树 + 两份 NeoForge 树无共享机制 | 已核实 | 迁移到 CURRENT 后收敛 |

---

## 验收纪律（每个里程碑都适用）

1. 纯逻辑必须有 JUnit 5 单测；没有单测的能力不合入。
2. 任何平台分支必须有双端验证记录，不能只在一端跑通。
3. `api` 与 `common` 的禁止 import 规则用脚本检查，纳入构建。
4. 不提前引入渲染/UI/世界生成能力。
5. 每次合入只处理一个能力，避免一次改动跨多个模块。
