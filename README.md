# YFCoreLib

YF 系列模组共用的基础库，目标版本 Minecraft 1.20.1，支持 Fabric 与 Forge。

当前版本 **0.1.0**，变更见 [CHANGELOG.md](CHANGELOG.md)。

## 模块

- `api`：纯 Java 契约层，不依赖 Minecraft 或加载器。
- `common`：共享实现，允许 Minecraft 类型，禁止加载器 import。
- `fabric` / `forge`：平台适配与入口。

## 快速开始

30 秒版本：**业务模组不需要写任何初始化代码**，库的入口已在自己的元数据里声明，直接调静态方法即可。

```java
// 配置：键用常量声明，类型错误在声明期就抛
public static final ConfigKey<Integer> MAX_HEAT =
        ConfigKey.of("world.temperature.max", Integer.class, 40, "体温上限");
public static final ConfigSpec SPEC = ConfigBuilder.create("mymod").define(MAX_HEAT).build();

int max = YFCoreLib.config(SPEC).get(MAX_HEAT);

// 事件
YFCoreLib.events().register(PlayerDeathEvent.class, e -> flush(e.player().id()));

// 网络（收包已在主线程）
NetworkChannel ch = YFCoreLib.network("mymod:main");
ch.register(new PacketType<>("mymod:heat", Side.CLIENT, HeatPacket.class, codec, handler));

// 调度与计时
YFCoreLib.scheduler(Side.SERVER).repeat(20, this::tick);
YFCoreLib.timers().set("mymod:cooldown", 200);

// 内容注册（Forge 必须在模组构造期调用）
YFRegistries.namespace("mymod").items().add("ruby", () -> new Item(new Item.Properties()));
```

完整的"从零接上一个模组"走查（配置迁移、玩家/世界持久化、文本度量、工具箱、十个常见坑）见
[EXAMPLES.md](docs/EXAMPLES.md)。

运行时排障入口：`/yfcorelib selftest`（自检）与 `/yfcorelib info`（版本/加载器/配置列表）。

## 构建

```powershell
.\gradlew.bat build
```

## 运行验证

```powershell
.\gradlew.bat :fabric:runServer
.\gradlew.bat :forge:runServer
```

## 文档

- [API.md](API.md)：面向附属模组开发者的完整接口说明
- [docs/EXAMPLES.md](docs/EXAMPLES.md)：接入示例与常见坑
- [docs/VERIFY-00-验证清单.md](docs/VERIFY-00-验证清单.md)：构建/双端/自检的验收清单与验证记录
- [CHANGELOG.md](CHANGELOG.md)：版本变更

## 发布

`api` 与 `common` 发布到 GitHub Packages，坐标 `mcmod.yfcorelib:yfcorelib-api` / `mcmod.yfcorelib:yfcorelib-common`。

1. 在用户级 `~/.gradle/gradle.properties` 写入凭据（不要提交到仓库）：

```properties
gpr.user=GitHub 用户名
gpr.key=具有 write:packages 权限的 PAT
```

也支持环境变量 `GITHUB_ACTOR` / `GITHUB_TOKEN`（CI 用）。若设置了 `GRADLE_USER_HOME`，上述文件位于 `$GRADLE_USER_HOME/gradle.properties`。

2. 执行发布：

```powershell
.\gradlew.bat :api:publish :common:publish
```

3. 发布前可先验包结构：

```powershell
.\gradlew.bat :api:publishToMavenLocal :common:publishToMavenLocal
```

`fabric` / `forge` 的最终 jar 不走 Maven，作为 Release 附件分发。

### 发布 0.1.0

1. 更新 `gradle.properties` 的 `mod_version` 与 [CHANGELOG.md](CHANGELOG.md)
2. 构建并确认产物：

```powershell
.\gradlew.bat :api:test :common:build :fabric:build :forge:build
dir fabric\build\libs, forge\build\libs
```

3. 提交并打 tag：

```powershell
git add -A
git commit -m "release: 0.1.0"
git tag -a v0.1.0 -m "YFCoreLib 0.1.0"
git push origin HEAD --tags
```

4. 在 GitHub 用该 tag 建 Release，附件上传 `yfcorelib-fabric-1.20.1-0.1.0.jar` 与 `yfcorelib-forge-1.20.1-0.1.0.jar`（**不要**上传 `-dev-shadow.jar`，那是未重映射的中间产物）；正文可直接引用 CHANGELOG 里 0.1.0 一节。
5. `api` / `common` 若也要发布，按上一节执行 `:api:publish :common:publish`。

## 许可

Apache License 2.0，全文见 `LICENSE`。
