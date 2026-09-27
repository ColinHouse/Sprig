# 发布状态

**已发布 SDK：** [v0.3.0-alpha.1](https://github.com/ColinHouse/Sprig/releases/tag/v0.3.0-alpha.1)，编译器 `0.3.0-alpha.1`，语言
`0.8-dev`，JDK17+，Apache-2.0。这是实验性 Alpha，不是生产迁移承诺。

Linux/macOS × JDK17/26 已通过源码与同一个实际 tag ZIP 的验证。
Windows 为独立、非阻塞的实验性预览。SDK 包含 Maven Resolver、锁定的项目
classpath、显式 `@std` IO/JSON 模块和三个 showcase。`build --emit-java-only`
可在 javac 之前查看经过静态检查的 Java 源码。

源代码 SHA、ZIP SHA256、实际命令、CI 链接和限制见[唯一验证记录](https://github.com/ColinHouse/Sprig/blob/main/docs/RELEASE_VALIDATION.md)。
未打 tag 的源码构建报告 development；匹配干净 tag 的构建报告 prerelease。
SDK 不附带 JDK。Formatter、Java SAM/数组/完整泛型适配、LSP/IDE 与自举仍是后续工作；
类型安全不证明数值稳定性。
