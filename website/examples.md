# 示例程序

做完[入门教程](/tutorial/)以后，可以看看这些更完整的程序。它们都放在仓库的 `examples/` 目录里，每个目录的 README 都写了怎么运行、用到了哪些依赖，以及还有哪些限制。

## 先从本地小工具看起

- [Task Tracker](https://github.com/ColinHouse/Sprig/tree/main/examples/task-tracker)：命令行任务清单，数据存在本地的 JSON 文件里。演示文件读写、带类型的数据模型，以及用 `@std/json_codec` 读取 JSON 字段，不需要联网。
- [json_select](https://github.com/ColinHouse/Sprig/tree/main/examples/json_select)：从 JSON 里挑选字段的命令行工具，由多个文件组成，用 `sprig-cli` 库解析命令行选项。
- [config_summary](https://github.com/ColinHouse/Sprig/tree/main/examples/config_summary)：读取一个小的 JSON 配置文件，输出格式固定的摘要；输入有误时会给出清楚的错误信息。
- [agent_tools](https://github.com/ColinHouse/Sprig/tree/main/examples/agent_tools)：三个用 Sprig 写的小工具，读取编译器输出的 JSON，用来查询 Java 和 Sprig API、汇总错误信息、比较 API 的差异。

## 用到 Java 库的应用

- [application_foundation](https://github.com/ColinHouse/Sprig/tree/main/examples/application_foundation)：HTTP、JSON 编解码、UTC 时间和文件处理。
- [sqlite](https://github.com/ColinHouse/Sprig/tree/main/examples/sqlite) 和 [sqlite_migrations](https://github.com/ColinHouse/Sprig/tree/main/examples/sqlite_migrations)：通过 Maven 使用 JDBC，演示事务、数据持久化和数据库迁移。
- [parallel_words](https://github.com/ColinHouse/Sprig/tree/main/examples/parallel_words)：用 `sprig-concurrent` 并行统计词数，任务、线程池、通道、计数器和锁各用一次，见[并发](/guide/concurrency)。
- [ledger](https://github.com/ColinHouse/Sprig/tree/main/examples/ledger)：精简的记账 HTTP 后端，重启后数据还在。详见 [Web 与 SQLite](/guide/web-sqlite)。
- [mini_web](https://github.com/ColinHouse/Sprig/tree/main/examples/mini_web)：带类型的路由、JSON 和 OpenAPI 文档。

第三方 Java 库照常用 Maven 坐标加进项目，由锁文件固定版本，见[项目](/guide/projects)和 [JVM 互操作](/guide/jvm-interop)。Sprig 自己维护的库都在 [`libraries/`](https://github.com/ColinHouse/Sprig/tree/main/libraries) 目录里。

## 更大一些的程序

- [repository_audit](https://github.com/ColinHouse/Sprig/tree/main/examples/showcases/repository_audit)：遍历一个代码仓库，生成 JSON 报告。
- [maven_slug](https://github.com/ColinHouse/Sprig/tree/main/examples/showcases/maven_slug)：锁定一个 Maven 库，查询它的 API，离线时复用缓存。
- [source_analyzer](https://github.com/ColinHouse/Sprig/tree/main/examples/showcases/source_analyzer)：用 Sprig 写的源码分析工具，遍历语法树，列出函数、变量和引用。它只覆盖语言的一部分，不是完整的编译器 API。
- [test_runner](https://github.com/ColinHouse/Sprig/tree/main/examples/test_runner)：在普通的 Sprig 项目里写各种测试，包括运行时测试、表格式测试、临时文件、子进程，以及预期会编译失败的测试。

另外，[Fabric 模组](/guide/fabric)是一个单独的案例：在 Minecraft 模组里用 Sprig 写逻辑，Gradle 和 Loom 负责构建，Java 那边只保留很薄的一层接口。

这些示例只覆盖了测试过的场景，不代表所有库 API 或者生产环境的负载都能支持。
