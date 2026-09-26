# 发布状态

**源码候选版本：** 编译器 `v0.3.0-alpha.1`，语言 `v0.8-dev`，JDK 17+，Apache-2.0。
**当前公开 SDK：** [v0.2.0-alpha.1](https://github.com/ColinHouse/Sprig/releases/tag/v0.2.0-alpha.1)。

v0.3 源码增加 Apache Maven Resolver、统一锁定的项目 JVM classpath、Unix/Windows
启动器、小型类型安全 IO/JSON 层、三个工具应用和贡献流程。源码能力与已发布包分别记录；
这是实验性 Alpha，不是生产迁移承诺。

CI 定义 Linux/macOS/Windows × JDK17/26。发布 workflow 从干净 tag 构建一个 ZIP，
六个任务验证同一发行包后才允许发布。配置不等于实际通过；确切 SHA、已运行门禁和
剩余问题见[唯一验证记录](https://github.com/ColinHouse/Sprig/blob/main/docs/RELEASE_VALIDATION.md)。
SDK 不附带 JDK。

发布/registry、推断、型变、interfaces/traits、LSP/IDE、自举仍未实现。
Stage-1 仍是 probe；类型安全不保证数值稳定性。见[已知限制](/reference/known-limitations)。
