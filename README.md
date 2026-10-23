# YFCoreLib

YF 系列模组共用的基础库，目标版本 Minecraft 1.20.1，支持 Fabric 与 Forge。

## 模块

- `api`：纯 Java 契约层，不依赖 Minecraft 或加载器。
- `common`：共享实现，允许 Minecraft 类型，禁止加载器 import。
- `fabric` / `forge`：平台适配与入口。

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

面向附属模组开发者的完整接口说明见 [API.md](API.md)。

## 发布

`api` 与 `common` 发布到 GitHub Packages，坐标 `mcmod.yfcorelib:yfcorelib-api` / `mcmod.yfcorelib:yfcorelib-common`。

1. 在用户级 `~/.gradle/gradle.properties` 写入凭据（不要提交到仓库）：

```properties
gpr.user=GitHub 用户名
gpr.key=具有 write:packages 权限的 PAT
```

也支持环境变量 `GITHUB_ACTOR` / `GITHUB_TOKEN`（CI 用）。

2. 执行发布：

```powershell
.\gradlew.bat :api:publish :common:publish
```

3. 发布前可先验包结构：

```powershell
.\gradlew.bat :api:publishToMavenLocal :common:publishToMavenLocal
```

`fabric` / `forge` 的最终 jar 不走 Maven，作为 Release 附件分发。

## 许可

Apache License 2.0，全文见 `LICENSE`。
