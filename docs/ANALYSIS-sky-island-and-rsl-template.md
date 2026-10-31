# Sky Island Generator / Random SkyBlock Island 代码分析
（只读分析；未运行 gradle；行号基于实际文件）

## 1 元信息

| 项 | `F:\Work\Sky Island Generator` | `F:\Work\Q\random-skyblock-island-template-1.20.1` |
|---|---|---|
| mod id | `skyisland`（gradle.properties:27） | `rsl`（fabric.mod.json:3；forge/gradle.properties:18） |
| 加载器 | 仅 Forge（mods.toml:1 javafml） | Fabric 根工程 + `forge/` 子工程（双加载器） |
| MC / Java | 1.20.1 / 17（build.gradle:16） | 1.20.1 / 17 |
| mappings | `official` 1.20.1（gradle.properties:19-21） | 根 yarn 1.20.1+build.10；forge official |
| 版本 | forge 47.4.22，range [47,) | fabric loader 0.19.3、loom 1.17-SNAPSHOT、fabric-api 0.92.11；forge 47.4.22 |
| 第三方依赖 | 无 maven 依赖（build.gradle:65-67） | 本地 jar：`libs/cloth-config-11.1.136-fabric.jar`、`libs/[模组菜单] modmenu-7.2.2.jar`；`forge/libs/cloth-config-11.1.136-forge.jar`、`forge/libs/[模组目录] catalogue-forge-1.20.1-1.8.0.jar` |
| mixin | `skyisland.mixins.json` 2 个；jar 清单 `MixinConfigs`（build.gradle:98）+ mods.toml [[mixins]] | 无（无 mixins json） |
| 内嵌第三方 | `CarpetSkyAdditions-1.20.zip` + 已解包同源码树 `CarpetSkyAdditions-1.20/`（310 文件、Kotlin DSL、无 libs/、未进 classpath，纯参考） | `gradle-9.5.1/` 整份 Gradle 发行版（683 文件）被提交 |
| 仓库卫生 | 提交了 `run/`、`logs/错误报告-2026-8-3_12.29.35.zip`、`temp/` 反编译源 | 提交了 `build/`、`.gradle/`、`temp/`、`forge/build/libs/rsl-forge-1.0.0.jar` |

## 2 源码结构（自有代码）

- repo1：5 个 Java。`com.skyisland`(1：SkyIslandMod 55)；`core.registry`(1：SkyRegistries 53)；`core.worldgen`(4：AbstractSkyIslandChunkGenerator 283 / Overworld 109 / Nether 52 / End 53)；`mixin`(2)。
- repo2：每加载器 13 个类（12 份 Fabric/Forge 复制 + 仅 Fabric 的 ModMenuIntegration）。`yifei.rsl`(1)；`commands`(5)；`config`(ModConfig 690|709、ModConfigScreen 35|41、TransformArea 34|37)；`game`(IslandGame 165|182、IslandBossbar 71|79、BlockReplacer 32|40)。
- 内嵌 CarpetSkyAdditions：84 个 Java（gen 3、gen.feature 7、helpers 15、mixin 40、settings 8、config 2、criterion 4、datafix 5、util 2）。

## 3 功能清单

**repo1（玩家可见）**：数据驱动世界预设 `data/skyisland/worldgen/world_preset/sky_island.json`，并追加进原版 preset 标签 `data/minecraft/tags/worldgen/world_preset/normal.json` → 创建世界界面直接出现"空岛"。主世界 (8,64,8) 5×5×1 草平台 + 手植橡树（OverworldSkyIslandGenerator:63-108），出生点硬设 (8,65,8)（SkyIslandMod:44-54）；下界 5×5×3 下界岩（NetherSkyIslandGenerator:30），要塞只留 MonsterThrone 烈焰人房（MixinNetherFortressStructure:37-57）；末地不铺平台、龙池改为"等效噪声高度"（MixinEndDragonFight:52-64 + Abstract:272-278）、白名单放行 end_spike（Abstract:161-205）。
**技术**：继承 `NoiseBasedChunkGenerator` 以满足 `ChunkMap.createRandomState`；`fillFromNoise` 不调 super 只放平台（Abstract:82-91）；自写"仅结构"装饰阶段（:142-214）；`getBaseHeight` 下限 63（:262）；三个 Codec 注册进 `BuiltInRegistries.CHUNK_GENERATOR`（SkyRegistries:33-52）。

**repo2（玩家可见）**：`/randomskyblock start|stop|reset|delay <1-3600>|delay show|area add|remove|rename|list|show|pos1|pos2|setfrompos`（权限 4）；每 N 秒（默认 180）整片区域随机换成另一种方块，200+ 候选；Bossbar 倒计时；Cloth Config 界面（Fabric 经 ModMenu，Forge 经 ConfigScreenHandler 扩展点，forge RandomSkyBlockIsland:45-51）；双层持久化：全局 `config/randomskyblockisland.json` + 存档 `<save>/rsl_areas.json`。
**技术**：无 mixin，纯服务端 tick 驱动；`BlockReplacer.fillArea` 三重循环 `setBlock(...,3)`。

## 4 可提取为通用库的候选点

| 能力 | 类（路径:行） | 解决的通用问题 | 复用 | 跨加载器 |
|---|---|---|---|---|
| 参数化平台生成器基类 | `core/worldgen/AbstractSkyIslandChunkGenerator.java:50-283`（模板方法 getPlatformBlock/getSupportBlock/shouldGeneratePlatform + x/y/z/size/layers） | 同结构不同参数的虚空世界，3 个子类各约 50 行 | 高 | 是 |
| 平台方块放置 + Heightmap 同步 | 同上 `placePlatform:94-133`（含区块相交裁剪 104-119、OCEAN_FLOOR_WG/WORLD_SURFACE_WG 更新 113-129） | 以方块而非 Feature 造几何体且维持高度图正确 | 高 | 是 |
| "仅结构"装饰阶段 | 同上 `applyBiomeDecoration:142-214`（按 step ordinal 分组、decorationSeed/featureSeed、CrashReport 包装） | 自定义 ChunkGenerator 中复用原版结构放置但不跑地物 | 高 | 是（CarpetSkyAdditions `SkyBlockChunkGenerator.java:87-229` 是同源实现） |
| 可写区域 BoundingBox | 同上 `getWritableArea:224-232` | 原版私有方法重实现 | 高 | 是（脆，依赖原版内部） |
| "等效噪声世界高度" | 同上 `getBaseHeightInEquivalentNoiseWorld:272-278` | 虚空世界需要"若有地形"的高度做锚点 | 高 | 是 |
| Codec 注册样板 | `core/registry/SkyRegistries.java:43-52` | 绕过 DeferredRegister、显式泛型注册内建注册表 Codec | 中 | 否（Forge RegisterEvent） |
| Mixin 双名注入约定 | `mixin/MixinEndDragonFight.java:52`、`MixinNetherFortressStructure.java:37`（`method={"a","m_xxx"}, remap=false` + `@Shadow(aliases=)`） | 无 refmap 时兼容 Mojang/SRG | 中 | 否 |
| 全局+存档级双层配置 | `config/ModConfig.java:47-49,543-591` + `game/IslandGame.java:30-31` | 一部分配置跨存档共享、一部分随存档 | 高 | 是 |
| 倒计时持久化 | `game/IslandGame.java:43-62,110-146` + ModConfig.isRunning/secondsRemaining | 定时任务跨重启恢复 | 高 | 是 |
| 区域 AABB 模型 | `config/TransformArea.java:3-33`（contains/getVolume/toString） | 整数方块区域模型 | 中 | 是 |
| Bossbar 倒计时 HUD | `game/IslandBossbar.java`（Fabric 71 / Forge 79，CommandBossBar vs ServerBossEvent） | 计时 HUD + 玩家进出维护 | 中 | 需适配层 |
| 区域批量填充 | `game/BlockReplacer.java:9-31` | 批量改方块 | 低（flags=3 触发邻块更新；Fabric 版每次 new BlockPos） | 是 |
| 命令前缀/权限样板 | `commands/*.java` 5 类各自 `literal("randomskyblock").requires(权限4)` | 统一指令树与权限 | 中 | 是 |
| 文本 fallback 工具 | `CarpetSkyAdditions-1.20/src/main/java/com/jsorrell/carpetskyadditions/util/SkyAdditionsText.java:10-16` | translatable 缺键回退 | 高 | 是 |
| 命名空间 ResourceLocation | 同目录 `util/SkyAdditionsResourceLocation.java:6-12` | 免写命名空间 | 高 | 是 |
| 内置数据包默认启用 | `helpers/DataConfigurationHelper.java:12-36` | 按配置改 WorldDataConfiguration enabled/disabled | 高 | 否（Fabric+Yarn 名） |
| GameEvent 一次性监听器 | `helpers/InstantListener.java:15-92`（冷却 + 振动标签/潜行/spectator 过滤） | 事件触发一次转换，免每 tick 扫描 | 高 | 是 |
| 规则配置 + 校验/迁移 | `settings/SkyAdditionsSettings.java:16-90`、`FieldPair.java:3-55`、`SettingFixer.java` | 规则重命名迁移、场景默认值覆盖 | 中 | 否（绑 Carpet） |
| Feature/Codec/json 三件套 | `gen/feature/SkyAdditionsFeatures.java`、`SpawnPlatformFeature(+Configuration)`、`SkyAdditionsConfiguredFeatures.java` | 自定义地物注册模板 | 高 | 是 |
| 不可变 Map 补键 | `helpers/DuplicateMapKey.java:7-12` | 零依赖小工具 | 低 | 是 |

## 5 两项目的重复代码

- **模板项目内部最大重复**：Fabric 根工程与 `forge/` 子工程 12 个类逐文件复制：`RandomSkyBlockIsland`、`commands/{Start,Stop,Area,Delay,Reset}Command`、`config/{ModConfig,ModConfigScreen,TransformArea}`、`game/{IslandGame,IslandBossbar,BlockReplacer}`。全部 `identical=False`，但差异只是命名映射（`ServerWorld→ServerLevel`、`Registries.BLOCK→ForgeRegistries.BLOCKS`）。`ModConfig` 690 vs 709 行几乎逐行相同。
- **repo1 与内嵌 CarpetSkyAdditions 的真重复**：`AbstractSkyIslandChunkGenerator.applyBiomeDecoration:142-214` ↔ `SkyBlockChunkGenerator.java:87-229`（同源，README:39 自述"参考"）；`MixinEndDragonFight:31-64` ↔ `EnderDragonFightMixin.java:22-44`（`portalLocation==null` 时 `getBaseHeightInEquivalentNoiseWorld(0,0,MOTION_BLOCKING_NO_LEAVES,level)-1` 完全一致）；`getWritableArea` 两边各自复制。
- **repo1 与 repo2 之间无共享 Java 类**（包名 `com.skyisland` vs `yifei.rsl`、加载器不同）。共同模式仅是：指令树 + JSON 配置 + 存档级配置 + `lang/zh_cn.json|en_us.json` 本地化。

## 6 模板项目的可复用资产

值得抽成脚手架：双加载器目录骨架（root=Fabric loom，`forge/`=ForgeGradle，各自 settings/gradle.properties）；`processResources` 占位符替换（build.gradle:34-41、forge/build.gradle:77-90）；`withSourcesJar`（:47-55）与 `reobfJar finalizedBy`（forge:107）；资源模板 `fabric.mod.json`（entrypoints/depends/suggests 全套）、`META-INF/mods.toml`（含第三方依赖段与 side 声明）、`pack.mcmeta`；配置界面接线三件套（ModConfigScreen → ModMenuIntegration / ConfigScreenHandler）；5 个命令类 + `rsl.message.*`/`rsl.config.*` 键名规范。
不值得继承：把 jar 塞进 `libs/`（应走 maven）、提交 `gradle-9.5.1/` 与 `build/`、`gradle.properties:4` 硬编码 `org.gradle.java.home=C:/Program Files/Java/jdk-21`。

## 7 坑与风险

1. 硬编码：`SkyIslandMod.java:51` 出生点 `new BlockPos(8,65,8)` 与 world_preset JSON 的 `platform_x/y/z` 重复（改 JSON 出生点不动）；`AbstractSkyIslandChunkGenerator:262` 魔数 `Math.max(platformY,63)`（主世界海平面假设被强加到下界/末地）；`OverworldSkyIslandGenerator:65` 只认中心区块（`>>4`）；`:193` 字符串硬编码 `"end_spike"`；`MixinNetherFortressStructure:41,50` 固定 Y=64、7×8×9 尺寸写死。
2. 版本耦合重：结构装饰与 `getWritableArea` 复制原版内部实现（Abstract:142-214、224-232），升 1.21 必碎；Mixin 方法名写死 SRG `m_64093_`/`m_214086_`，换映射即失效（README:59-65 自述未生成 refmap）。
3. 业务写死在底层：`isRuinedPortal:217-221` 与 end_spike 白名单（:191-205）属于"空岛策略"，却固化在生成器基类里，应数据驱动。
4. repo2：`ModConfig.generateConfigJson:381-412` 手拼 JSON 无转义（区域名含引号即产出坏 JSON）；`extractValue:649-686` 用 `indexOf` 找键，遇同名子串会误配；Fabric 版 `Paths.get("config",...)`（:347,363）依赖进程 CWD（Forge 版已改 `FMLPaths`，forge ModConfig:358）；`IslandGame.transformIsland:120` 在 `allowedBlocks` 被 `clear()`（:445）后可能为空 → 索引越界；`BlockReplacer` 大区域三重循环 + flags=3 卡主线程；`IslandBossbar` Fabric 版 `HashSet`（:17）非线程安全，Forge 版已改 `ConcurrentHashMap`；`fabric.mod.json:26` 要求 loader ≥0.16.2 而 gradle 用 0.19.3。

## 最值得抽进通用库的 Top 5

1. **`AbstractSkyIslandChunkGenerator`（Abstract:50-283）整类**——"参数化虚空平台生成器 + 等效噪声高度"模板方法，3 个子类合计 214 行即可派生新维度世界。
2. **结构专用装饰阶段（Abstract:142-214）+ `getWritableArea`（:224-232）**——从生成器拆成独立可复用类，顺带消除 repo1 与 CarpetSkyAdditions 的同源重复。
3. **多加载器 Gradle 脚手架 + 资源模板**（build.gradle:34-41、forge/build.gradle:77-90、fabric.mod.json、mods.toml、pack.mcmeta）。
4. **全局+存档级双层配置与持久化倒计时**（ModConfig:47-49,543-591 + IslandGame:43-62）——但内部实现须换成 Gson/Codec。
5. **区域 AABB + 批量方块填充 + Bossbar 计时 HUD**（TransformArea:3-33 / BlockReplacer:9-31 / IslandBossbar）——统一为使用 `MutableBlockPos` 与并发安全集合的实现。
