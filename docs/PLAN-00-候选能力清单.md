# YFCoreLib 生态盘点与总体路线（v2）

> 版本：2026-10-22 ｜ 替代 2026-02 的 v1 清单
> 依据：14 个公开仓库的本地源码实测（文件数/行数/build 配置/依赖声明），未运行 Gradle
> 仓库清单：Nyctocosm、Frilien's Magic、Dragon Curse Chronicles、Perpetual Day、Sky Island Generator、Random SkyBlock Island、Subtitle Highlight、Calendar、HtmlCraft API、DC Render API、NCAA、Punctuation、Mild、Random Height

---

## 0. 本版结论（先看这 8 条）

1. **仓库数 9 → 14**。新增 Frilien's Magic、Punctuation、Mild、Random Height 四个仓库；其中 Frilien's Magic 是第二大规模（1.20.1 约 9970 行）。
2. **加载器结构已翻转**。1.20.1 线上 Forge 8 个（Nyctocosm、Frilien、DCC、SkyIsland、Calendar、HtmlCraft、DC Render、Random Height），Fabric 7 个（Pdopn、RSL、DCC-fabric、Calendar-fabric、HtmlCraft-fabric、NCAA、Punctuation、Mild）。**"只做 Fabric"会漏掉一半工程和最大的两个仓库**。
3. **版本跨度 1.20.1 → 26.2**：1.20.1(Java 17) / 1.21.1/1.21.4/1.21.10/1.21.11(Java 21) / 26.1.2、26.2(Java 25)。26.x 已是主线新工作（Frilien 26.2 用 NeoForge + Java 25，Calendar/Mild 26.2 用 Fabric + Java 25）。
4. **v1 主战场仍是 1.20.1**（14 个仓库里 13 个有 1.20.1 源码树），**但必须 Fabric + Forge 双端**。v1 原"仅 Fabric"的决策作废。
5. **收敛为两条产品线**：`LTS 1.20.1`（Fabric+Forge，Java 17）与 `CURRENT 26.2`（Fabric+NeoForge，Java 25）。**1.21.x 作为过渡带，不再新建独立源码树**，Subtitle/DCC-NeoForge 迁移到 CURRENT 后退出。
6. **分层不变量**：`api` 纯 Java（零 MC、零加载器）；`common` 允许 MC 类型但零加载器 import；加载器差异只在 `fabric`/`forge`/`neoforge` 模块。
7. **构建改用 Architectury Loom**（common + fabric + forge/neoforge），替代当前单 Fabric 模板。理由：双加载器共享 common 是消重的前提，且 1.20.1 的 Forge 侧无法用 NeoForge moddev。
8. **v1 交付范围不扩大**：仍只做地基与高频刚需，渲染/UI 框架、世界生成工具箱、26.2 实现后置。

---

## 1. 仓库盘点（实测）

| 仓库 | 本地目录 | 源码树 | 加载器 | MC | Java | 规模（java/行） | 主要包 |
|---|---|---|---|---|---|---|---|
| The-Nyctocosm-Mod | `Nyctocosm` | 单树 | Forge 47.4.27 | 1.20.1 | 17 | 102 / 16550 | `com.yifei.nyctocosm`（boss 38、client 22、spell 15、world 7） |
| Frilien-s-Magic | `Frilien's Magic` | 双树 | Forge 47.4.27；NeoForge moddev | 1.20.1；26.2 | 17；25 | 87 / 9970；3 / 190 | `yifei.frliliens.magic`（spell 22、quest 14、capability 6、attachment 5、network 4） |
| Dragon-Curse-Chronicles | `Twelve Talismans` | 三树 + 前置库 | Forge；Fabric；NeoForge | 1.20.1；1.20.1；1.21.1 | 17；17；21 | 54 / 7363；58 / 7226；56 / 7346 | `com.qituo.dcc` |
| DC-Render-API | `Twelve Talismans\DC Render API` | Forge + neoforge | Forge | 1.20.1 | 17 + Kotlin | 18 / 1676（另有 Kotlin） | `com.qituo.dcrapi` |
| Perpetual-Day-or-Perpetual-Night | `Perpetual day or perpetual night` | 单树 | Fabric | 1.20.1 | 17 | 27 / 4574 | `yifei.pdopn`（含 callback/config/storage/temperature/thirst） |
| Sky-Island-Generator | `Sky Island Generator` | 单树 | Forge 47.4.22 | 1.20.1 | 17 | 8 / 729 | `com.skyisland` |
| Random-SkyBlock-Island | `Q\random-skyblock-island-template-1.20.1` | 双树 | Fabric + Forge | 1.20.1 | 17 | 13 / 1516；12 / 1670 | `yifei.rsl` |
| Subtitle-Highlight | `Subtitle-Highlight-Unofficial reset` | 6 树 | Fabric ×4 + NeoForge ×2 | 1.20.6/1.21.1/1.21.4/1.21.10/1.21.11 | 21 | 每树 10 / 515；NeoForge 3 / 190、7 / 514 | `Yeah_Zero.Subtitle_Highlight` / `yeah_zero.subtitle_highlight` |
| Calendar-Mod | `Calendar` | 四树 | Forge + Fabric ×3 | 1.20.1；1.20.1/26.1.2/26.2 | 17；25 | 21 / 3806；23 / 3895 / 4076 / 4076 | `com.calendar.mod` |
| HtmlCraft-API | `Calendar\HtmlCraftAPI` | 双树 | Forge + Fabric | 1.20.1 | 17 | 23 / 4153（每树） | `com.htmlcraft.api` |
| NCAA | `ncaa` | 单树 | Fabric | 1.20.1 | 17 | 8 / 234 | `com.yifei.ncaa` |
| Punctuation | `Punctuation` | 单树 | Fabric | 1.20.1 | 17 | 17 / 1599 | `yifei.pua`（api 6 + impl 4） |
| Mild | `Mild` | 双树 | Fabric | 1.20.1；26.2 | 17；25 | 13 / 416；33 / 2492（16 个 entity mixin + AI service） | `qituo.mild` |
| Random-Height | 未克隆 | 单树（GitHub） | Forge | 1.20.1 | 17 | 6 个类 | `yifei.randomheight` |

**口径说明**：文件数/行数只统计 `src/main/java`，不含 `src/client`、Kotlin、`temp/`、反编译参考源。Subtitle 的客户端代码在独立 `src/client/java`，未计入上表。

---

## 2. 版本 / 加载器矩阵

| MC 线 | Java | Fabric | Forge | NeoForge | 仓库 |
|---|---|---|---|---|---|
| 1.20.1 | 17 | ✔ | ✔ | — | Nyctocosm、Frilien、DCC、Pdopn、SkyIsland、RSL、Calendar、HtmlCraft、DC Render、NCAA、Punctuation、Mild、Random Height |
| 1.21.1 | 21 | ✔ | — | ✔ | Subtitle、DCC-NeoForge |
| 1.20.6 / 1.21.4 / 1.21.10 / 1.21.11 | 21 | ✔ | — | ✔(1.21.11) | Subtitle |
| 26.1.2 | 25 | ✔ | — | — | Calendar |
| 26.2 | 25 | ✔ | — | ✔ | Calendar、Mild、Frilien |

**判断**：1.20.1 是唯一"双加载器同时覆盖、且仓库数最多"的版本线，适合作为 LTS；26.2 是新工作线；1.21.x 只是 Subtitle/DCC 的过渡产物。

---

## 3. 重复基础设施（实测，按复用收益排序）

1. **配置系统 10+ 份、5 种写法**：`PdopnConfig`（Gson+版本合并）、`DcRenderApiConfig`（Properties）、`CalendarConfig`（NBT 手写）、`NyctocosmConfig`（ForgeConfigSpec）、`TalismanConfig`、`Frilien config.Config`、`MildConfig`、`RandomHeightConfig`、`PunctuationConfig`、RSL 双层 JSON、`ncaa ConfigManager`。无一统一支持版本迁移/字段校验。
2. **玩家数据持久化两套范式并存**：Frilien 同时有 `capability`（Forge Capability，6 类）与 `attachment`（5 类）两套包；Nyctocosm 用 Capability；Pdopn 用自管存档 `PlayerDataStore`；Calendar 用 `SavedData`。这是**跨加载器抽象最直接的证据**。
3. **网络包手写**：Frilien `ModNetwork` + `CastBasicAbilityPayload`/`SelectSpellPayload`/`SyncPlayerDataPacket`；Calendar 4 个 network 类；Nyctocosm `Network`；DCC `ModNetwork` + `ParticleGroupPacket`；DC Render 网络层。每包 60~90 行样板。
4. **注册样板复制**：Nyctocosm（38 个 boss、15 个 spell）、Frilien、DCC（items/effects/enchantments/recipes/sounds）、Pdopn 各自维护 `DeferredRegister`/内建注册表。
5. **自研注册中心同构**：Nyctocosm `SpellRegistry`、Frilien `SpellRegistry`、Subtitle 字幕类型表、DC Render 多个 Manager、HtmlCraft `StyleManager`。
6. **tick 调度手写**：Nyctocosm boss tick、DCC 的 8 个 manager 手工 tick、Calendar `AutoSyncScheduler`（volatile 世界日）、Mild 26.2 AI service tick、Random Height `TickHandler`。
7. **事件/回调桥重复**：Pdopn `callback` 包、Frilien `handler` 8 类、Calendar `HudRenderSubscriber`、Mild mixin 事件、HtmlCraft 客户端钩子。
8. **Mixin 使用密集且无工具**：Mild 26.2 有 16 个 entity mixin + 1 个 item mixin；Subtitle 有客户端 mixin 两件套；Pdopn 5 个；Punctuation 1 个；DCC 另有 mixin 配置。**Subtitle 已经出过一次包名/配置错位**（见第 4 节）。
9. **属性/状态快照**：Pdopn 的属性修饰符快照 + 延迟加载队列；Frilien 的 `handler` 中飞行/法力隐藏等状态恢复，和 Pdopn 是同构问题。
10. **文本度量/排版**：HtmlCraft `LayoutEngine`、Calendar HUD；中文 UI 全部受影响。
11. **数学/随机/噪声/缓动/颜色**：DC Render 的 Kotlin 算法、SkyIsland 世界生成、Mild AI、HtmlCraft 布局都在各自实现。
12. **世界生成工具箱**：SkyIsland `AbstractSkyIslandChunkGenerator` 模板方法 + Heightmap 双同步；RSL 的区块批量替换。
13. **资源/数据包加载**：Calendar `StyleManager` 多层资源栈；HtmlCraft 模板加载；Subtitle 数据驱动字幕类型。
14. **软依赖探测**：Random Height 已正确声明 Pehkui（mods.toml mandatory）；RSL 正确声明 cloth-config；但 **DCC 的 mods.toml 完全没有 dcrapi/kotlinforforge**（见第 4 节）。各仓库写法仍不统一（Fabric `FabricLoader` vs Forge `ModList`）。
15. **跨模组 API/实现分离**：Punctuation 已有 `api` + `api.impl`（PunctuationAPI/RenderAPI/RaycastAPI/ParticleAPI），Subtitle 有 `api` 包，HtmlCraft/DC Render 对外发布 API。**接口/实现的注册与发现方式各不相同**。

---

## 4. 已证实的事故与空壳（写文档/抽库时不要复制）

| 位置 | 事实 |
|---|---|
| `Subtitle-Highlight\1.21.10` | `fabric.mod.json` 引用的 `subtitle_highlight.client.mixins.json` 仍指向旧包 `Yeah_Zero.Subtitle_Highlight.Mixin`，注册 `SubtitleEntryMixin`/`SubtitlesHudMixin`；而 1.21.10 源码包已改为 `yeah_zero.subtitle_highlight.Mixin`。同时新增的 `subtitle-highlight.client.mixins.json` 只注册 `ExampleClientMixin`。→ **生产 mixin 实际不生效**，且残留两份客户端配置。 |
| `Subtitle-Highlight\1.21.10` | `src/main` 的 `subtitle-highlight.mixins.json` 只注册 `ExampleMixin`，模板残留。 |
| `Twelve Talismans\src\main\resources\META-INF\mods.toml` | 依赖只声明 `forge`/`minecraft`；代码却 `project(':DC Render API')` + `kotlinforforge` jar → **缺前置直接 `NoClassDefFoundError`**。 |
| `Twelve Talismans` | Forge/Fabric/NeoForge 三棵树各 54~58 文件、约 7.2k~7.4k 行，均为整份复制，无 include。 |
| `Calendar` | Forge 1 树 + Fabric 3 树（1.20.1/26.1.2/26.2），每树 21~23 文件、3.8k~4.1k 行。 |
| `HtmlCraftAPI` | `RenderPipeline` 的 `RenderCommand` 体系未被调用；百分比宽度用哨兵值，`getWidth()>0` 判断失效（沿用 v1 分析）。 |
| `DC Render API` | README 宣称的多个 API 在源码中不存在；`ControlableParticle.getParticle()` 恒为 null；事件总线无 `post()`（沿用 v1 分析）。 |
| `Calendar` | `CalendarDate` 月份硬编码上界 12 与配置允许任意月数冲突（沿用 v1 分析，待二次确认）。 |
| 所有多版本仓库 | 无 Stonecutter/include/composite；"多版本"= 复制源码树。Subtitle 四份 Fabric 树均为 10 文件/515 行。 |
| 模板残留 | YFCoreLib 自身、Subtitle、NCAA、Random Height 均保留 `ExampleMixin` 或 MDK 英文注释。 |

---

## 5. 可直接提取的资产（算法/基础设施，不含业务）

| 资产 | 来源 | 去向 |
|---|---|---|
| 版本化配置合并器（以文件实际内容为基准补默认值、不覆盖用户值、保留未知键） | `Pdopn\config\PdopnConfig` | `api`/`common` 配置内核 |
| 存档级玩家 KV 持久化 + 异常兜底 | `Pdopn\storage\PlayerDataStore` | 持久化模块 |
| 属性修饰符快照与精确还原（稳定 UUID + 修改前 baseValue） | `Pdopn\entity\PdopnEntityModifier` | 属性工具 |
| 延迟加载队列（JOIN 早于 server 就绪） | `Pdopn\temperature\PdopnTemperatureManager` | 生命周期桥 |
| Mixin→回调总线 | `Pdopn\callback\PdopnPlayerDeathCallback` | Mixin 工具包 |
| 声明式注册中心（分类缓存、冲突检测、生命周期钩子） | `Nyctocosm\spell\SpellRegistry`、`Frilien spell.SpellRegistry` | 注册中心 |
| 纯逻辑内核 + 单测样例 | Pdopn 的 4 个测试 | 测试规范 |
| HTML/CSS 引擎 + 迷你 DOM（零 MC 耦合） | `HtmlCraftAPI parser/style/core` | `api` 纯算法包 |
| 圆角/阴影/渐变光栅化 | `HtmlCraftAPI render.RenderPipeline` | 后续渲染底座 |
| 缓动/时间线/Perlin/颜色/向量 | DC Render API（Kotlin） | `api` 数学包（改写为 Java） |
| 参数化空岛 ChunkGenerator + Heightmap 同步 | `SkyIslandGenerator AbstractSkyIslandChunkGenerator` | 世界生成工具箱（后置） |
| 服务端状态→客户端缓存同步模型 | `Calendar AutoSyncScheduler + CalendarClientCache` | 数据同步引擎 |
| 粒子发包限流/LOD/优先级队列 | `DCC particles.SmartParticleDispatcher` | 粒子调度（后置） |
| API + impl 分离模板 | `Punctuation api` / `api.impl` | 跨模组 API 规范 |

---

## 6. 能力清单（v2 修订）

### P0 地基

| # | 能力 | 需求来源 | 说明 |
|---|---|---|---|
| 0.1 | 双加载器构建骨架（LTS 1.20.1） | 全部 13 个 1.20.1 仓库 | Architectury common+fabric+forge |
| 0.2 | `platform` 抽象 + ServiceLoader | 全部 | `isModLoaded`/`isClient`/`isDevelopment`/目录/版本 |
| 0.3 | `api` 纯 Java 契约层 | 全部 | 零 MC、零加载器 |
| 0.4 | ID/资源定位/翻译键/模型路径约定 | 全部 | 在现有 `YFCoreLib.id()` 上扩展 |
| 0.5 | 日志与启动诊断（分模块、依赖清单、异常聚合） | 全部 | 替代各仓库裸 `LoggerFactory` |

### P1 高频刚需

| # | 能力 | 需求来源 |
|---|---|---|
| 1.1 | 注册 DSL + 自动注册（item/block/entity/effect/sound/particle/menu/tab/enchantment/recipe） | Nyctocosm、Frilien、DCC、Pdopn、Calendar |
| 1.2 | 自研 `Registry<T>`（冲突检测/分类查询/冻结/线程安全） | Nyctocosm、Frilien、Subtitle、DC Render、HtmlCraft |
| 1.3 | 声明式网络包（C2S/S2C/广播/按维度/按距离 + 协议版本） | Frilien、Calendar、Nyctocosm、DCC、DC Render |
| 1.4 | 数据同步引擎（ServerState → ClientCache，登录/变更/定时/增量） | Calendar、Frilien、Nyctocosm |
| 1.5 | 玩家数据持久化（Fabric Attachment / Forge Capability 统一抽象 + 自动同步/迁移） | Frilien（两套并存）、Nyctocosm、Pdopn |
| 1.6 | 世界/存档数据（JSON/NBT 双后端、原子写入、损坏回退） | Calendar、Pdopn、RSL |
| 1.7 | 配置内核（版本迁移、字段校验、热重载、双端分区、注释/i18n） | 全部（10+ 份实现） |
| 1.8 | tick 调度器（延迟/周期/可取消/持久化计时器） | Nyctocosm、DCC、Calendar、Mild、Random Height |
| 1.9 | 生命周期事件桥 + 延迟加载队列 | Pdopn、Frilien、Calendar、Mild |
| 1.10 | 属性修饰符快照与还原 | Pdopn、Frilien、Nyctocosm |
| 1.11 | Mixin 工具包（回调总线/访问助手/配置校验/双名注入） | Mild（17 个）、Subtitle、Pdopn、Punctuation、DCC |

### P2 体验层

| # | 能力 | 需求来源 | 本轮 |
|---|---|---|---|
| 2.1 | `TextMeasure`（真实字体度量、CJK、折行、省略、缓存） | HtmlCraft、Calendar、全部中文 UI | 做 |
| 2.2 | 数学/随机/噪声/缓动/颜色/向量 | SkyIsland、DC Render、Mild AI、HtmlCraft | 做 |
| 2.3 | 反射/运行期工具（字段方法缓存、失败可见） | DCC、Frilien、Nyctocosm | 做 |
| 2.4 | 跨模组 API 互操作（软依赖、能力发现、版本协商、降级） | Punctuation、Subtitle、HtmlCraft、DC Render | 做 |
| 2.5 | 文本/富文本/i18n 兜底 | NCAA、Pdopn、全部 | 做 |
| 2.6 | NBT/Codec/JSON 工具 | 全部 | 做 |
| 2.7 | 绘图指令层（矩形/文本/贴图/渐变/圆角/GL 状态） | HtmlCraft、Calendar、DC Render | 后置到 v1.5 |
| 2.8 | HUD 元素注册与布局 | Pdopn、Calendar | 后置 |
| 2.9 | Screen/Widget 框架 | Calendar、DCC、HtmlCraft | 后置 |
| 2.10 | 按键绑定/输入 | Punctuation、Calendar | 后置 |

### P3 工程化

| # | 能力 | 说明 |
|---|---|---|
| 3.1 | 新模组脚手架（替代 `1.21.1/`、`1.21.11/` 两个模板 zip） | 统一目录/包名/元数据/mixin 配置/CI |
| 3.2 | DataGen 基类 | 跨加载器屏蔽 |
| 3.3 | 依赖治理（本地 jar、软依赖声明规范、version catalog） | 直接防住 DCC 类事故 |
| 3.4 | 测试基建（纯逻辑单测 + mixin 配置校验） | 沿用 Pdopn 的 4 个测试 |
| 3.5 | 示例模组 + API 教程 | 所有能力有可运行示例 |

### 明确不做（v1）

- Forge/NeoForge 之外的第三个加载器；Quilt 等
- 1.21.x 的独立实现（只保证 `api` 可平移）
- 26.2 实现（v2 再开）
- 世界生成工具箱、渲染/UI 框架本体、粒子调度器（2.7~2.10、1.12）
- 大而全配置界面（界面交给 Cloth Config/HtmlCraft，内核自己实现）

---

## 7. 技术路线（v2 决策）

### 7.1 两条产品线

| 线 | MC | Java | 加载器 | 定位 |
|---|---|---|---|---|
| LTS | 1.20.1 | 17 | Fabric + Forge | v1 主战场，覆盖 13 个仓库 |
| CURRENT | 26.2 | 25 | Fabric + NeoForge | v2，覆盖 Frilien/Mild/Calendar 新工作 |
| 过渡 | 1.21.1~1.21.11 | 21 | Fabric + NeoForge | 只服务 Subtitle/DCC，不新建源码树 |

### 7.2 构建

- **Architectury Loom**：`api` + `common` + `fabric` + `forge`（LTS）；`neoforge` 在 CURRENT 线加入。
- 不使用 Stonecutter 跨 1.20.1→26.2：API 差异（DataComponent、注册重构、`Identifier`/`ResourceLocation`、渲染重写、Java 17→25）无法靠预处理掩盖。
- 版本线通过**独立分支 + 共享 `api` 发布物**复用，而不是复制业务源码。

### 7.3 分层与依赖方向（硬约束）

```
api（纯 Java）  ←  common（MC 类型，零加载器 import）  ←  fabric / forge / neoforge
```

1. `api`：禁止 `net.minecraft.*`、`net.fabricmc.*`、`net.minecraftforge.*`、`net.neoforged.*`。
2. `common`：允许 `net.minecraft.*`，禁止任何加载器包与加载器入口类。
3. 平台模块：唯一允许出现加载器 API 的地方；只做适配，不写业务规则。
4. 平台能力通过 `ServiceLoader` 发现，禁止 `if (loader == ...)` 分支散落业务代码。
5. `common` 的 MC 相关实现必须能在 Fabric/Forge 两端编译通过；做不到的进平台模块。

### 7.4 发布与接入

- 自用工具箱，不做兼容承诺；但**每个纯逻辑能力必须有单测**。
- 包名/mod id 保持 `mcmod.yfcorelib`。
- **本库面向后续新模组**，不承担改造现有 14 个仓库的义务；现有仓库仅作为需求证据与验证样本。
- 验证顺序（参考样本）：Pdopn（Fabric 基础设施最成熟）→ Nyctocosm（Forge 规模最大）→ Calendar（同步引擎）→ Frilien（双持久化范式）。

---

## 8. v1 冻结范围（2026-10-22）

**做**：`0.1~0.5` → `1.1~1.11` → `2.1~2.6` → `3.1、3.3~3.5`
**后置**：`2.7~2.10`（渲染/UI）、世界生成、粒子调度、26.2 实现、1.21.x 实现

**v1 验收门槛**：
1. `api` 模块 `javadoc`/编译不引用任何 MC 或加载器类型。
2. `common` 模块全仓库 grep 无 `net.fabricmc` / `net.minecraftforge` / `net.neoforged` import。
3. 同一份 `common` 源码在 Fabric 与 Forge 两端口编译通过。
4. 纯逻辑（配置合并、注册表、调度队列、数学、文本度量）单测覆盖。
5. 至少 2 个试点仓库真实接入并删除原有重复实现。

---

## 9. 下一步

1. 评审本文件第 7 节的**双加载器 + 两条产品线**决策。
2. 按 `PLAN-01` 落地模块与包结构。
3. 按 `PLAN-02` 冻结接口签名。
4. 按 `PLAN-03` 执行 M0~M6 里程碑。
5. 现有仓库中的已知问题只作参考记录，不在本项目修复范围内（详见第 4 节）。
