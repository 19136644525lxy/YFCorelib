# 工程化与测试基建 任务清单

## 任务列表

- [x] ENG-1 导入边界任务
  - 在根 `build.gradle` 追加 `subprojects` 块，为 api/common 注册 `checkImportBoundary` 并挂到 `check`
  - 用临时违例文件验证 api（`net.minecraft.`）与 common（`net.fabricmc.`）均能失败，验证后删除
- [x] ENG-2 CI 工作流
  - 新增 `.github/workflows/build.yml`（JDK 17 temurin，四模块 `build`，`--no-daemon`，不加 clean）
- [x] ENG-3 DemoContent
  - 新增 `common/.../demo/DemoContent.java`（dev 门控、item + block、自动 BlockItem、幂等）
  - Fabric 入口与 Forge 构造器接入
- [x] ENG-4 诊断命令
  - 新增 `common/.../diagnostic/SelfTest.java`（事件总线/调度器/配置/会话/编解码/注册 DSL）
  - 新增 `common/.../command/YFCoreCommand.java`（info 无门槛，selftest 权限 2）
  - Fabric 用 `CommandRegistrationCallback` 注册；Forge 在 `ForgeLifecycleBridge` 用 `RegisterCommandsEvent` 注册
- [x] ENG-5 构建与运行验证
  - `:api:test :common:build :fabric:build :forge:build --no-daemon`
  - 双端 `runServer`，临时启动断言确认命令节点存在且自检全 PASS，随后删除临时代码
  - `git status` 复查无残留，结束 java 进程
- [x] ENG-6 汇报
  - 说明四项改动的原理、双端一致性、未使用 Forge GameTest 的理由、验证结果

## 依赖关系

ENG-1、ENG-2、ENG-3 可并行；ENG-4 依赖 ENG-3（自检包含注册 DSL 项）；ENG-5 依赖前三项全部完成。

## 完成定义

- 四模块构建通过，api 单测全绿
- 双端启动自检全 PASS，命令可执行
- 无临时文件、无新增说明文档、无 Git 提交（未获要求）
