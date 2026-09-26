# 发布状态

**已发布版本：** `v0.1.0-alpha.1`（prerelease，2026-09-24）
**仓库开发版本：** `0.2.0-alpha.1`（Sprig v0.8-dev：泛型核心已实现，尚未发布）
**仓库：** <https://github.com/ColinHouse/Sprig>
**许可证：** Apache-2.0

## 发行包

首个 Alpha 已作为 **prerelease** 发布：

<https://github.com/ColinHouse/Sprig/releases/tag/v0.1.0-alpha.1>

| 发行物 | 说明 |
|---|---|
| `sprig-v0.1.0-alpha.1-jdk.zip` | 编译器与 runtime、ANTLR 4.13.2、启动脚本、Hello World、许可证与第三方说明；**不含 JDK**。 |
| `sprig-v0.1.0-alpha.1-jdk.zip.sha256` | ZIP 的 SHA-256 校验值。 |

发布后已重新下载该 ZIP 并核对校验值、解压运行 `bin/sprig version`、`check` 与
`run`；发行包内的 `BUILD_INFO.txt` 记录源码版本 `6e7b57e`。发行说明见
`docs/releases/RELEASE_NOTES-v0.1.0-alpha.1.md`。

## 已经验证的内容

- 在 macOS Apple Silicon 上使用 OpenJDK 17.0.19、26.0.1 与 Python 3.14.6 从源码
  构建并测试通过；托管 CI 在 Linux 上以 JDK 17 和 26 运行同一套测试。
- 发布流程在 tag 上重新构建编译器、运行测试、打包并上传 ZIP 与校验文件。
- 编译器源码以 `javac --release 17` 构建（classfile 61），可在 JDK 17 或更新版本运行。
- 文档示例由 `tools/verify-doc-snippets.py` 实际执行。

## 尚未宣称的能力

- Stage-1 自举、包管理器、语言服务器、标准库发行版、完整的 Java 泛型/数组互操作都
  **尚未实现**。
- 这是 Alpha 版本，不构成生产稳定性或数值正确性承诺。
- 数值结果不保证算法稳定性或物理量单位正确。
- 除 macOS Apple Silicon（本地）与 Linux（CI）之外没有验证其他平台。

另见[已知限制](/reference/known-limitations)与
[Stage-1 路线图（英文）](/en/reference/STAGE1_ROADMAP)。
