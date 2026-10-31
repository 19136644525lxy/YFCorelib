# 更新日志

本文件记录 YFCoreLib 的版本变更。格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循语义化版本。

## [未发布]

（下一轮：M6 试点接入与 `TextMeasure` 渲染侧补完）

## [0.1.0] - 2026-10-31

首个可发布版本。目标 `Minecraft 1.20.1`，Fabric 与 Forge 双端。

### 模块

| 模块 | 坐标 / 产物 | 说明 |
| --- | --- | --- |
| `api` | `mcmod.yfcorelib:yfcorelib-api` | 纯 Java 契约层，编译期不依赖 Minecraft 与加载器 |
| `common` | `mcmod.yfcorelib:yfcorelib-common` | 共享实现，允许 Minecraft 类型，禁止加载器 import |
| `fabric` | `yfcorelib-fabric-1.20.1-0.1.0.jar` | Fabric 适配与入口 |
| `forge` | `yfcorelib-forge-1.20.1-0.1.0.jar` | Forge 适配与入口 |

### 新增

- **平台抽象**（`api.platform`）：`Platform` / `ModInfo` / `Services`，平台实现经 `ServiceLoader` 注入；`Platform.worldDir()` 提供跟随存档的目录。
- **注册 DSL**（`common.registry` + `api.registry`）：`YFRegistries.namespace(...)` 下的物品/方块/声音注册，`RegistrationRef` 延迟取值，自动 `BlockItem`；`SimpleRegistry` 通用注册表（标签索引、冻结、监听器可退订）。
- **调度与计时**（`api.scheduler`）：`TickScheduler`（延迟/重复/取消，异常隔离）、`SimpleTimerStore`（毫秒无关的刻计时、溢出饱和）、`RateLimiter`（单调时钟）。
- **事件与生命周期**（`api.event`）：`SimpleEventBus`（按精确类型分发）、`ServerTickEvent` / `ClientTickEvent`、`ServerStartedEvent` / `ServerStoppingEvent`、`PlayerJoinEvent` / `PlayerLeaveEvent` / `PlayerDeathEvent`、`LifecycleQueue`（登录后 1 刻的 ready 回调、离场回调）。
- **配置**（`api.config`）：`ConfigKey` 常量 + `ConfigBuilder` + 迁移器，默认值合并**绝不覆盖用户已改值**、保留未知键、版本号驱动回写、加载失败拒绝写回。
- **持久化**（`api.data`）：`JsonPlayerDataStore` / `JsonWorldDataStore`（原子写 + `.bak` 回退）、`AtomicFiles`、`SimplePlayerSessionStore`、`DataCodecs`（严格解码）。
- **网络**（`api.network`）：`NetworkChannel` / `PacketType` / `DataCodec`，双端 `requireDirection` 语义一致，处理器统一切到主线程，codec 必须是纯函数。
- **数据同步**（`api.sync`）：`SimpleSyncEngine`（服务端快照变化才广播、发送成功才落快照）。
- **Mixin 基建**（`api.mixin`）：`MixinCallbacks`（Mixin 只发事件）、`MixinConfigParser`、`MixinConfigValidator`（类存在性只查 `.class` 资源、refmap 校验、重复声明检测）。
- **文本度量**（`api.text` + `common.text`）：`TextMeasure` / `TextLayout` 纯接口与纯数据，`FontTextMeasure` 基于原版 `Font`。
- **数学与随机**（`api.math`）：`Easing`（12 条曲线 + `reverse` / `inOut` / `interpolate`）、`Color`（RGBA 值类型，`lerp` / `luminance` / `toHexArgb`）、`Rng`（可按坐标/字符串派生，同坐标恒定）、`WeightedTable`（O(log n) 抽取）、`Noise`（2D Perlin + fbm）。
- **反射工具**（`api.util.ReflectionCache`）：带缓存的字段/方法查找，找不到时给出候选名单，混淆名会提示。
- **互操作**（`api.interop`）：`SoftDependency`、`CapabilityKey` / `CapabilityRegistry`。
- **自带生产 Mixin**：`LivingEntityDeathMixin` 在 `LivingEntity#die` 头部发 `PlayerDeathEvent`（仅服务端玩家），双端真机验证生效。
- **运维入口**：`/yfcorelib info`（版本/加载器/配置列表）与 `/yfcorelib selftest`（自检，权限等级 2）。

### 工程质量

- `api` 单测 **221** 个（评审基线的 107 → 129 → 177 → 210 → 217 → 221），全部纯内存、不依赖游戏。
- `checkImportBoundary` 任务把架构纪律变成可失败检查：`api` 禁止 import `net.minecraft.*` / `net.fabricmc.*` / `net.minecraftforge.*`，`common` 禁止加载器。
- 日志统一走 slf4j；异常隔离覆盖事件监听器、生命周期钩子、调度任务、注册表监听器、同步引擎收发。
- 文档：[API.md](API.md) 完整接口说明 · [docs/EXAMPLES.md](docs/EXAMPLES.md) 接入示例 · [docs/VERIFY-00-验证清单.md](docs/VERIFY-00-验证清单.md) 验收清单与验证记录。

### 已知限制

- **不提供**：渲染/UI 框架、世界生成工具箱、配置界面、1.20.5+ 客户端/服务端分离（1.20.1 是唯一目标）。
- `TextMeasure` 只做度量与折行，**不做渲染**（画字、描边、背景由使用方负责）；`FontTextMeasure` 只保证与 `Font#split` 行数一致，每行是拍平后的纯文本，**样式会丢**。
- `fabric` / `forge` 的 jar 不按 Maven 坐标发布，只作 Release 附件；`common` 的 POM 已裁掉脚手架依赖（`net.fabricmc` / `dev.architectury`），但它们仍存在于构建期。
- `JsonConfigNode` 的 `raw()` / `read()` / `set(String, JsonElement)` 对外暴露 Gson 类型。
- `DemoContent`（dev 专用示例内容）没有贴图与模型，正式环境不注册。
- `ServerTickEvent` 与 `ClientTickEvent` 是两个独立类型（按精确类型分发），不要指望监听前者能收到客户端刻。

### 不兼容说明

0.1.0 是首个版本，没有历史包袱。但若不慎从开发中间态升级，注意 `ServerTickEvent` 已由 `record ServerTickEvent(Side side)` 改为 `record ServerTickEvent(){}`，`Easing` / `WeightedTable` 等工具类为首次出现。
