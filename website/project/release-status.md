# 发布状态

**已发布 SDK：** [v0.5.0-beta.1](https://github.com/ColinHouse/Sprig/releases/tag/v0.5.0-beta.1)，编译器 `0.5.0-beta.1`，语言版本 `0.8-dev`，JDK17+，Apache-2.0。这是实验性 Beta，不是生产迁移承诺。Linux/macOS 为发行支持平台；Windows 仍是非阻塞预览。

Beta SDK 包含项目测试、wrapper 生成、schema-4 依赖锁、受限且显式的 Java/JVM 互操作，以及 CLI、HTTP、JSON、SQLite 和 Web 一方库。具体能力以发行资产及已安装 SDK 的 `sprig capabilities --json` 为准。没有中央包注册服务，Sprig 也尚未自举。

已下载发行 ZIP 的 Linux/macOS × JDK 17/26 验收、校验和及发布流程证据见[发行验证记录](https://github.com/ColinHouse/Sprig/blob/main/docs/releases/validation.md)。

v0.4 在 v0.3 基础上加入 canonical formatter、显式模块 re-export、表达式 `match`、
Unicode code-point 字符串语义、`sprig api` 模块/项目内省、managed SDK 升级，并完成
一轮对抗正确性审查：推断全局类型、生成类名冲突、默认字段 effect、`finally` 完成性、
CR 布局、JVM source/bridge 解析、索引赋值 key/index 检查、可空标量相等以及
安装器 ZIP 加固。

Linux/macOS × JDK17/26 通过源码与同一个实际 tag ZIP 的验证。
Windows 为独立、非阻塞的实验性预览。SDK 包含 Maven Resolver、锁定的项目
classpath、显式 `@std` IO/JSON 模块、SQLite/Web 库和三个 showcase。

源代码 SHA、ZIP SHA256、实际命令、CI 链接和限制见[验证记录](https://github.com/ColinHouse/Sprig/blob/main/docs/releases/validation.md)。
未打 tag 的源码构建报告 development；匹配干净 tag 的构建报告 prerelease。
SDK 不附带 JDK。Java SAM/数组/完整泛型适配、LSP/IDE 与自举仍是后续工作；
类型安全不证明数值稳定性。
