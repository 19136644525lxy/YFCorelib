# PLAN-01 模块划分与包结构

> 配套：`PLAN-00`（v2 决策）、`PLAN-02`（接口签名）、`PLAN-03`（里程碑）
> 适用：LTS 1.20.1（Fabric + Forge，Java 17）

---

## 1. 模块图

```
            ┌────────────────────────────┐
            │ api（纯 Java，零 MC/零加载器）│
            └─────────────┬──────────────┘
                          │
            ┌─────────────▼──────────────┐
            │ common（MC 类型，零加载器） │
            └──────┬──────────────┬──────┘
                   │              │
            ┌──────▼─────┐  ┌─────▼──────┐
            │ fabric     │  │ forge      │
            └────────────┘  └────────────┘
```

- `api`：只放契约、数据模型、纯算法。可被任何加载器、任何 MC 版本引用。
- `common`：共享实现。允许 Minecraft 类型，禁止加载器包。
- `fabric` / `forge`：平台适配与入口。只允许在这里出现加载器 API。
- 下游模组只依赖 `api`（编译期）+ 平台产物（运行期）。

---

## 2. 目录结构

```
YFCoreLib/
├── settings.gradle
├── build.gradle                 # 根：公共插件/Java/仓库/发布约定
├── gradle.properties            # MC/加载器/版本号（锁 release，不用 SNAPSHOT）
├── api/
│   ├── build.gradle             # java-library，无 loom
│   └── src/
│       ├── main/java/mcmod/yfcorelib/api/
│       └── test/java/mcmod/yfcorelib/api/
├── common/
│   ├── build.gradle             # architectury common + loom
│   └── src/main/java/mcmod/yfcorelib/core/
├── fabric/
│   ├── build.gradle             # loom + fabric loader
│   └── src/main/java/mcmod/yfcorelib/fabric/
├── forge/
│   ├── build.gradle             # forge + loom
│   └── src/main/java/mcmod/yfcorelib/forge/
└── docs/
```

`settings.gradle` 只 include 上述四个模块；`1.21.1/`、`1.21.11/` 下的模板 zip 不参与构建，留给 `PLAN-03` 的脚手架任务处理。

---

## 3. 包结构

### 3.1 `api`（纯 Java）

| 包 | 内容 |
|---|---|
| `api` | `YFCoreLib` 门面、常量、`Ids` |
| `api.platform` | `Platform`、`ModInfo`、`Env`、`PlatformProvider` |
| `api.config` | `ConfigSpec`、`ConfigKey`、`ConfigMigrator`、`ConfigSource` |
| `api.registry` | `Registry`、`RegistryKey`、`RegistryListener`、`Freezable` |
| `api.net` | `Packet`、`PacketType`、`NetworkChannel`、`PacketContext` |
| `api.data` | `PlayerDataStore`、`WorldDataStore`、`DataCodec`、`SyncChannel` |
| `api.event` | `EventBus`、`LifecycleEvent`、`PlayerEvent`、`ServerEvent` |
| `api.schedule` | `Scheduler`、`ScheduledTask`、`Tickable` |
| `api.mixin` | `MixinCallback`、`AccessHelper`、`MixinConfigValidator` |
| `api.text` | `TextMeasure`、`TextLayout`、`TextLine` |
| `api.math` | `Easing`、`Color`、`WeightedRandom`、`Noise`、`VecMath` |
| `api.interop` | `SoftDependency`、`CapabilityKey`、`CapabilityRegistry` |
| `api.util` | 纯工具：`NbtUtil`、`JsonUtil`、`ReflectionCache`、`Preconditions` |

### 3.2 `common`

| 包 | 内容 |
|---|---|
| `core` | 内部入口、`DefaultServices` |
| `core.platform` | `PlatformImpl`（服务实现基类） |
| `core.config` | 版本化合并器、JSON/Gson 后端、迁移执行 |
| `core.registry` | `RegistryImpl`、分类索引、冻结 |
| `core.net` | 包注册表、序列化、限流、广播 |
| `core.data` | 玩家/世界数据实现、同步引擎 |
| `core.event` | 事件总线、生命周期桥、延迟队列 |
| `core.schedule` | tick 调度实现、持久化计时器 |
| `core.mixin` | 访问助手、配置校验器 |
| `core.text` | 字体度量缓存、折行 |
| `core.math` | 纯算法的 MC 适配（无加载器） |

### 3.3 `fabric` / `forge`

| 包 | 内容 |
|---|---|
| `fabric` | 入口 `YFCoreLibFabric`、`FabricPlatform`、Attachment/Capability 适配、网络适配 |
| `forge` | 入口 `YFCoreLibForge`、`ForgePlatform`、Capability 适配、网络适配 |

---

## 4. 依赖规则（必须可 grep 验证）

| 模块 | 禁止 import |
|---|---|
| `api` | `net.minecraft.*`、`net.fabricmc.*`、`net.minecraftforge.*`、`net.neoforged.*` |
| `common` | `net.fabricmc.*`、`net.minecraftforge.*`、`net.neoforged.*`、`*.fabric.*`、`*.forge.*` |
| `fabric` | `net.minecraftforge.*`、`net.neoforged.*` |
| `forge` | `net.fabricmc.*`、`net.neoforged.*` |

补充：

1. 平台发现统一用 `ServiceLoader<PlatformProvider>`；`@ExpectPlatform` 仅用于无法避免的静态入口，且实现必须落到 `fabric`/`forge` 包。
2. 禁止在 `common` 写 `if (FabricLoader...)` / `if (FMLLoader...)`。
3. 禁止用反射访问加载器内部。
4. 下游模组的业务代码不得直接依赖 `common` 之外的实现类。

---

## 5. "放哪一层"判定规则

| 问题 | 结论 |
|---|---|
| 用到 `ItemStack`/`Level`/`Identifier` 吗 | 是 → 不能进 `api` |
| 用到 `FabricLoader`/`ModList`/`DeferredRegister` 吗 | 是 → 只能进平台模块 |
| 只有纯计算（数学/JSON/NBT 结构/字符串/颜色） | 进 `api` |
| 需要 MC 但两端逻辑相同 | 进 `common` |
| 两端 API 形态不同（Capability vs Attachment、事件注册） | 接口在 `api`，实现各平台一份 |
| 需要 Mixin | `common` 放注入逻辑与回调，平台放配置注册 |

---

## 6. 构建约定

1. **Architectury Loom**，`common` 声明 `architectury { common("fabric", "forge") }`。
2. **映射统一用 Mojang official**（Fabric 与 Forge 共用），避免 Yarn/official 转换成本；`api` 不受影响。
3. Java 17：`options.release = 17`，`sourceCompatibility/targetCompatibility = 17`。
4. 所有发布模块 `withSourcesJar()`。
5. `loom_version`、Forge、Fabric Loader 版本**锁定 release**，禁止 `SNAPSHOT`。
6. `gradle.properties` 分层：MC/加载器/依赖版本集中；模组版本单一来源。
7. 构建命令不带 `clean`。
8. 根 `build.gradle` 统一配置编码 UTF-8；禁止各模块各写一份 Java 版本。

### 6.1 已验证的构建配置（spike 结论）

| 项 | 值 |
|---|---|
| Gradle | 8.8（Loom 1.7 不兼容 Gradle 9） |
| JDK | 17 |
| architectury-plugin | 3.4.164 |
| Architectury Loom | 1.7.435 |
| Architectury API | 9.2.14（1.20.1） |
| Forge | `net.minecraftforge:forge:1.20.1-47.4.27`（坐标必须带 MC 前缀） |

必须遵守的 4 个细节：

1. `forge/gradle.properties` 写 `loom.platform=forge`，否则报 `Loom is not running on Forge`。
2. `forge` 模块的 `loom { forge { convertAccessWideners = false } }` 必须存在，`forge` 依赖符号才会注册。
3. `api` 是纯 Java 模块，平台模块需同时配置：
   - `implementation project(":api")`（编译/开发类路径）
   - `shadowCommon(project(":api"))`（打进产物 jar）
   - Forge 额外加 `developmentForge(project(":api"))`，否则 dev 运行报 `ClassNotFoundException`。
4. `api` 不应用 Loom；`common` 允许 MC 类型，但仍禁止加载器 import。

验证结果：`:api:build :common:build :fabric:build :forge:build` 通过；`:fabric:runServer` 与 `:forge:runServer` 均打印 `YFCoreLib spike core=yfcorelib:core`。

---

## 7. 命名与资源约定

| 项 | 约定 |
|---|---|
| mod id | `yfcorelib` |
| group | `mcmod.yfcorelib` |
| 入口类 | `mcmod.yfcorelib.fabric.YFCoreLibFabric` / `...forge.YFCoreLibForge` |
| Mixin 配置 | `yfcorelib.mixins.json`（主）、`yfcorelib.client.mixins.json`（客户端） |
| Mixin 包 | `mcmod.yfcorelib.mixin`（common）+ 平台 `mixin` 子包 |
| 翻译键 | `yfcorelib.<模块>.<键>` |
| 数据目录 | `config/yfcorelib/`、`data/yfcorelib/` |
| 资源路径 | `assets/yfcorelib/` |

Mixin 规则：

1. 每加一个 Mixin 类，必须同步在两个 json 中确认注册，禁止 `ExampleMixin` 残留。
2. `required: true`、`defaultRequire: 1`；客户端 Mixin 只放 client json。
3. 禁止直接反射混淆名；统一走 `AccessHelper`。

---

## 8. 测试与示例

1. `api/src/test/java` 用 JUnit 5；只测纯逻辑：配置合并、注册表冲突、调度队列、文本度量、数学。
2. `common` 的 MC 相关逻辑用 GameTest 或最小集成测试，不追求覆盖率。
3. 每个能力合入时必须附带：单测 + 一个示例用法（示例模组在 `PLAN-03` 的 M6 建立）。
4. 验收命令：`gradlew :api:test :common:build :fabric:build :forge:build`（不加 clean）。

---

## 9. 当前工程改造清单（M0）

1. 根 `settings.gradle` 增加 `api`/`common`/`fabric`/`forge`，移除单工程假设。
2. `gradle.properties` 锁版本、改 group、补 Forge 版本。
3. 删除 `ExampleMixin` 与 `yfcorelib.mixins.json` 中的注册（保留空配置或新增真实 Mixin）。
4. `fabric.mod.json` 替换占位描述/作者/主页/license；新增 `forge` 的 `mods.toml`。
5. 清理 `README.md` 与各 `build.gradle` 的 MDK 英文注释。
6. `YFCoreLib.id()` 保留并迁移到 `api.Ids`，`YFCoreLib` 改为平台入口门面。
7. 保留 `icon.png`；补 `pack.mcmeta` 如需。
