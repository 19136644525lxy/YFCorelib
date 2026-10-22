# 工程化与测试基建 规格说明

## 1. 背景与目标

M2–M4 与注册 DSL、玩家会话内存态已完成，但缺少长期有效的工程约束与运行时自检手段：

- 模块边界（api 零 MC 依赖、common 零加载器依赖）目前只靠人工 grep 保证；
- 没有 CI，改动是否破坏双端构建只能本地验证；
- 注册 DSL 缺少常驻的运行时验证载体；
- 缺少一条可在真实服务端执行的诊断命令，用于现场确认事件总线、调度器、配置、会话与网络编解码是否正常。

本规格覆盖四项工作：导入边界检查任务、CI 工作流、开发环境示例内容、`/yfcorelib` 诊断命令。全部改动双端同步。

## 2. 设计决策

### 2.1 导入边界检查（根 build.gradle）

在根 [build.gradle](file:///f:/Work/YFCoreLib/build.gradle) 末尾追加一个 `subprojects` 块，仅对 `api`、`common` 注册 `checkImportBoundary` 任务：

- `api` 禁用导入前缀：`net.minecraft.`、`net.fabricmc.`、`net.minecraftforge.`
- `common` 禁用导入前缀：`net.fabricmc.`、`net.minecraftforge.`
- 扫描范围：`src/main/java/**/*.java`；逐文件正则匹配行首 `import`：
  - api：`(?m)^\s*import\s+net\.(minecraft|fabricmc|minecraftforge)\.`
  - common：`(?m)^\s*import\s+net\.(fabricmc|minecraftforge)\.`
- 违规时抛 `GradleException` 并列出全部违规文件路径；任务分组 `verification`
- 挂到既有 `check`：`tasks.named("check") { dependsOn "checkImportBoundary" }`

原理：编译成功不代表边界正确（平台模块与 common 在同一类路径上，越界导入仍能编译）。用源码扫描把架构纪律固化为可失败检查，CI 与本地 `build` 都会执行。

### 2.2 CI 工作流

新增 `.github/workflows/build.yml`：

- 触发：push 到 `common` 分支、任意 `pull_request`
- 运行器：`ubuntu-latest`，JDK 17（temurin），`gradle/actions/setup-gradle@v3`
- 步骤：`chmod +x gradlew` → `./gradlew :api:build :common:build :fabric:build :forge:build --no-daemon`
- 不用 `clean`（遵循构建命令禁用 clean 的项目规则）

注意：Fabric/Forge 的 Loom 构建需要下载 MC 资源，CI 首次运行耗时较长属正常，不是失败。

### 2.3 开发环境示例内容 DemoContent

新增 `common/src/main/java/mcmod/yfcorelib/common/demo/DemoContent.java`：

- `public static final String ITEM_PATH = "demo_item"`、`BLOCK_PATH = "demo_block"`
- `public static synchronized void register()`，幂等：
  1. 已注册直接返回；
  2. `YFCoreLib.platform().isDevelopment()` 为 false 时直接返回（发布版零影响）；
  3. `ContentNamespace ns = YFRegistries.namespace(YFCoreLib.MOD_ID)`；
  4. `ns.items().add(ITEM_PATH, () -> new Item(new Item.Properties()))`；
  5. `ns.block(BLOCK_PATH, Block::new, BlockBehaviour.Properties.of())`（自动生成同名 BlockItem）；
  6. info 日志输出两个 id。
- 不提供贴图/模型资源：演示内容不进创造模式物品栏，仅在开发环境用于验证注册链路，避免为演示向两个平台模块复制资源文件。

接入点：

- `YFCoreLibFabric.onInitialize()` 中 `TimerPersistence.register()` 之后调用；
- `YFCoreLibForge` 构造器中 `TimerPersistence.register()` 之后调用（Forge 延迟注册要求命名空间在构造期创建）。

原理：开发环境自动注册一对内容，使注册 DSL 在每次 `runServer` 时都被真实走一遍，回归时无需再写临时代码。

### 2.4 诊断命令 `/yfcorelib`

新增两个类：

**`common/src/main/java/mcmod/yfcorelib/common/diagnostic/SelfTest.java`**

- `public record Result(String name, boolean passed, String detail)`
- `public static List<Result> runAll()`，固定顺序执行五项纯内存检查，任何一项抛异常都转为失败结果，不影响后续项：
  1. **事件总线**：新建 `SimpleEventBus`，订阅 `ProbeEvent(int value)` 后 post 两次（2+3），close 后再 post 一次，断言命中值累计 5；
  2. **调度器**：新建独立 `TickScheduler`（不驱动全局调度器），`delay(1)` 与 `repeat(1)` 各一，断言两次 `tick()` 后计数为 2、`cancel()` 返回 true、再 tick 后计数不变；
  3. **配置合并**：内存 `ConfigSource` 预置 `{"enabled":false,"unknown":7}`，用 `ConfigBuilder` 定义 `enabled:Boolean=true`、version=1，断言 reload 后用户值 false 未被覆盖、`unknown` 保留、版本为 1，`set(true)` 后 save+reload 读回 true；
  4. **玩家会话**：新建局部事件总线 + `SimplePlayerSessionStore<String>`（工厂返回 "default"），post 伪造 `PlayerJoinEvent`（`PlayerRef` 匿名实现）后 `has` 为真，post `PlayerLeaveEvent` 后 `size()==0`；
  5. **网络编解码**：构造 `PacketType<ProbePacket>`（id `yfcorelib:selftest`，codec 用 Gson），`SimpleChannelRegistry.register` 后 `require(id)==type`，`encode`→`decode` 文本一致。传输层仍需真实客户端联机验证，结果中注明。
- 开发环境额外第 6 项 **注册 DSL**：`BuiltInRegistries.ITEM/BLOCK` 中存在 `DemoContent` 的两个 id。
- `public static boolean allPassed(List<Result>)`。

**`common/src/main/java/mcmod/yfcorelib/common/command/YFCoreCommand.java`**

- `public static LiteralArgumentBuilder<CommandSourceStack> builder()`，用 Brigadier 直接构建（不依赖 `Commands` 静态初始化；Brigadier 的 `CommandDispatcher.register` 只接受 builder，故返回 builder 而非 node）：
  - `/yfcorelib info`：输出横幅、loader、mc、dev、配置管理器名称集合、计时器 key 数量；
  - `/yfcorelib selftest`：`requires(source -> source.hasPermission(2))`，逐条输出自检结果（PASS/FAIL + 明细），返回 1（全过）或 0。
- 输出用 `sendSuccess(Supplier<Component>, false)`，中文文案。

双端注册：

- Fabric：`CommandRegistrationCallback.EVENT` 中 `dispatcher.register(YFCoreCommand.builder())`，写在 `YFCoreLibFabric.onInitialize()`；
- Forge：在 `ForgeLifecycleBridge.register(IEventBus bus)` 内追加 `bus.addListener((RegisterCommandsEvent e) -> e.getDispatcher().register(YFCoreCommand.builder()))`；1.20.1 的 `RegisterCommandsEvent` 在 Forge 事件总线上，传入的 `bus` 即 `MinecraftForge.EVENT_BUS`。

不使用 Forge GameTest 的原因：GameTest 仅 Forge 提供且需额外 test 源集与结构模板，无法在 Fabric 侧对称验证；本项目验收目标是"双端行为一致"，故改用双端都可执行的运行时自检命令。

## 3. 文件清单

新增：

- `.github/workflows/build.yml`
- `common/src/main/java/mcmod/yfcorelib/common/demo/DemoContent.java`
- `common/src/main/java/mcmod/yfcorelib/common/diagnostic/SelfTest.java`
- `common/src/main/java/mcmod/yfcorelib/common/command/YFCoreCommand.java`
- `.trae/specs/engineering-test-infra/spec.md`、`tasks.md`（本规格）

修改：

- `build.gradle`（导入边界任务）
- `fabric/src/main/java/mcmod/yfcorelib/fabric/YFCoreLibFabric.java`（DemoContent + 命令注册）
- `forge/src/main/java/mcmod/yfcorelib/forge/YFCoreLibForge.java`（DemoContent）
- `forge/src/main/java/mcmod/yfcorelib/forge/ForgeLifecycleBridge.java`（命令注册）

## 4. 验证步骤

1. 边界任务自证：临时在 `api/src/main/java` 下加一个含 `import net.minecraft.core.Registry;` 的 Java 文件，运行 `.\gradlew.bat :api:checkImportBoundary --no-daemon` 预期失败并列出该文件；删除临时文件后再次运行预期成功。common 同理用 `net.fabricmc.` 违例验证一次。
2. 构建与测试：`.\gradlew.bat ':api:test' ':common:build' ':fabric:build' ':forge:build' --no-daemon`，全程不得加 `clean`。
3. 双端运行：分别后台执行 `:fabric:runServer`、`:forge:runServer`，日志写入 `$env:TEMP`；等待 `Done (` 与横幅 `YFCoreLib core=yfcorelib:core | loader=fabric|forge mc=1.20.1 dev=true`。
4. 自检实证：在双端入口临时注册 `ServerStartedEvent` 监听器，把 `SelfTest.runAll()` 结果逐行写日志；Fabric 侧另用 `ServerLifecycleEvents.SERVER_STARTED` 临时断言 `server.getCommands().getDispatcher().getRoot().getChild("yfcorelib") != null`，Forge 侧在 `RegisterCommandsEvent` 临时断言同一节点存在。确认自检全部 PASS 后删除全部临时代码。验证结束后 `Stop-Process -Name java -Force`。
5. 复查 `git status`，确认无临时文件、无构建产物残留。

## 5. 验收标准

- `checkImportBoundary` 能阻断违例且已挂入 `check`；
- 四个模块构建成功，api 106 项单测保持全绿；
- 双端 dev 环境均注册 `yfcorelib:demo_item`、`yfcorelib:demo_block`，发布环境不注册；
- 双端 `/yfcorelib` 命令节点存在，`selftest` 六项（dev）全部 PASS；
- CI 工作流文件语法正确（YAML 可被解析），命令与本地验证一致；
- 未新增任何使用说明类文档，除本规格目录外不产生额外文档。

## 6. 不在本次范围

- 真实客户端联机的 C2S↔S2C 回环验证（需附属模组接入后手动联机）；
- M5 工具箱（Mixin 回调、反射缓存、文本测量、数学随机）；
- Git 提交与推送（用户未要求）。

## 7. 需批准事项

- 是否同意新建 `.trae/specs/engineering-test-infra/` 下的本规格与任务清单；
- 是否同意在根 `build.gradle` 增加导入边界任务并把 `check` 作为强制入口；
- 是否同意 DemoContent 不带贴图/模型资源（开发环境专用）；
- 是否同意以"双端运行时自检命令 + 临时启动断言"替代 Forge GameTest。
