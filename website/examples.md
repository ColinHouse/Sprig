# 应用程序示例

先做完[可执行入门教程](/tutorial)。这里按学习进阶列出有实际用途的程序；教程源码在 `website/snippets/`，独立项目在 `examples/`，测试夹具仍放 `tests/`。

## 从本地程序开始

- [Task Tracker](https://github.com/ColinHouse/Sprig/tree/main/examples/task-tracker)：本地 JSON CLI、文件 I/O、类型化模型；不访问网络。
- [json_select](https://github.com/ColinHouse/Sprig/tree/main/examples/json_select)：多文件 JSON 命令行程序，使用 `sprig-cli`。
- [config_summary](https://github.com/ColinHouse/Sprig/tree/main/examples/config_summary)：从小型 JSON 配置文件生成可重复的摘要，并展示错误输入的诊断。
- [agent_tools](https://github.com/ColinHouse/Sprig/tree/main/examples/agent_tools)：查询 Java/Sprig API、诊断摘要与 API 差异工具。

## JVM 应用与库

- [application_foundation](https://github.com/ColinHouse/Sprig/tree/main/examples/application_foundation)：HTTP、JSON codec、UTC 时间与文件处理。
- [sqlite](https://github.com/ColinHouse/Sprig/tree/main/examples/sqlite) 与 [sqlite_migrations](https://github.com/ColinHouse/Sprig/tree/main/examples/sqlite_migrations)：Maven JDBC、事务、持久化和迁移。
- [ledger](https://github.com/ColinHouse/Sprig/tree/main/examples/ledger)：精简账户/交易 HTTP 后端与重启后持久化。
- [mini_web](https://github.com/ColinHouse/Sprig/tree/main/examples/mini_web)：类型化路由、JSON 与 OpenAPI。

第三方库仍由普通 Maven 坐标与锁文件管理。请查看[首方库目录](https://github.com/ColinHouse/Sprig/tree/main/libraries)和[JVM 互操作指南](/guide/jvm-interop)。

## 面向编译器与生态的案例

- [repository_audit](https://github.com/ColinHouse/Sprig/tree/main/examples/showcases/repository_audit)：遍历仓库并生成 JSON 报告。
- [maven_slug](https://github.com/ColinHouse/Sprig/tree/main/examples/showcases/maven_slug)：锁定 Maven 库、查询 API、离线复用缓存。
- [source_analyzer](https://github.com/ColinHouse/Sprig/tree/main/examples/showcases/source_analyzer)：使用 Sprig frontend API 分析受支持的源码子集。
- [test_runner](https://github.com/ColinHouse/Sprig/tree/main/examples/test_runner)：普通 Sprig 项目中的 runtime、table、临时文件、子进程和 expected-diagnostic 测试。

[Fabric/Loom dogfood](/guide/fabric) 是单独的高要求框架集成案例，展示 host-owned classpath 和窄 Java adapter 的边界。

示例覆盖了有限的已测场景，不代表所有库 API 或生产工作负载都受支持。每个目录的 README 说明启动命令、锁文件和已知边界。
