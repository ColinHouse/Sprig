# 发布状态

**当前 prerelease：** [v0.2.0-alpha.1](https://github.com/ColinHouse/Sprig/releases/tag/v0.2.0-alpha.1)
**语言：** `v0.8-dev` · **JDK：** 17+ · **许可证：** Apache-2.0

第二个公开 prerelease 包含多参数显式泛型、Equatable、本地/Git 依赖、schema-2 锁文件、
离线构建、Agent 查询工具与显式 JVM `--classpath`。

发行物为 `sprig-v0.2.0-alpha.1-jdk.zip` 及同名 `.sha256`，不含 JDK。
先校验 SHA-256，再解压运行 version/capabilities/check/run。
发布 workflow 在 tag 上重建、运行完整测试、核对版本并执行发行包 smoke。
托管 CI 覆盖 Linux 的 JDK 17、26；本地验证覆盖 macOS Apple Silicon。
编译器 class 使用 `javac --release 17`。

[发行说明](https://github.com/ColinHouse/Sprig/blob/main/docs/releases/RELEASE_NOTES-v0.2.0-alpha.1.md)
与[唯一验证记录](https://github.com/ColinHouse/Sprig/blob/main/docs/post-v0.7/V08_VALIDATION_REPORT.md)
记录范围和测试。已发布 alpha.1 的发行说明仍保留在 docs/releases。

这是实验性 Alpha。Maven 解析、项目级 Maven classpath、发布/registry、Comparable、
推断、variance、interfaces/traits、LSP/IDE 尚未实现。Stage-1 仍是 probe，不能自举；
类型安全也不保证算法数值稳定性。完整边界见[已知限制](/reference/known-limitations)。
