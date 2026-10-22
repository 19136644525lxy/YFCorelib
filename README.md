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

## 许可

Apache License 2.0，全文见 `LICENSE`。
