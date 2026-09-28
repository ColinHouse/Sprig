# 发布状态

**最新已发布 SDK：** [v0.3.0-alpha.1](https://github.com/ColinHouse/Sprig/releases/tag/v0.3.0-alpha.1)，编译器 `0.3.0-alpha.1`，JDK17+，Apache-2.0。这是实验性 Alpha，不是生产迁移承诺。

**v0.4.0-alpha.1 发布候选**（annotated tag workflow 执行后成为已发布版本）在 v0.3 基础上加入 canonical formatter、显式模块 re-export、表达式 `match`、
Unicode code-point 字符串语义、`sprig api` 模块/项目内省、managed SDK 升级，并完成
一轮对抗正确性审查：推断全局类型、生成类名冲突、默认字段 effect、`finally` 完成性、
CR 布局、JVM source/bridge 解析以及安装器 ZIP 加固。

候选验证：Linux/macOS × JDK17/26 源码门槛、grammar、docs 与编辑器检查已通过；
annotated tag workflow 将构建并 smoke-test 同一个实际 ZIP，证据随后写入验证记录。
Windows 为独立、非阻塞的实验性预览。SDK 包含 Maven Resolver、锁定的项目
classpath、显式 `@std` IO/JSON 模块、SQLite/Web 库和三个 showcase。

源代码 SHA、ZIP SHA256、实际命令、CI 链接和限制见[验证记录](https://github.com/ColinHouse/Sprig/blob/main/docs/RELEASE_VALIDATION.md)。
未打 tag 的源码构建报告 development；匹配干净 tag 的构建报告 prerelease。
SDK 不附带 JDK。Java SAM/数组/完整泛型适配、LSP/IDE 与自举仍是后续工作；
类型安全不证明数值稳定性。
