# 发布状态

**编译器候选版本：** `0.1.0-alpha.1`（语言设计：Sprig v0.7）
**仓库：** <https://github.com/ColinHouse/Sprig>
**许可证：** Apache-2.0

## 已经验证的内容

- 在 macOS Apple Silicon 上使用 OpenJDK 17.0.19、26.0.1 与 Python 3.14.6 从源码
  构建并测试通过；托管 CI 在 Linux 上以 JDK 17 和 26 运行同一套测试。
- `scripts/build.sh`、`scripts/test.sh` 与语法 smoke harness 通过；文档示例由
  `tools/verify-doc-snippets.py` 实际执行。
- 编译器源码以 `javac --release 17` 构建（classfile 61），可在 JDK 17 或更新版本运行。
- README 与本站引用的测试数字都来自这些实际运行。

## 发行包

**目前还没有可下载的正式发行包。** 请从源码构建（见[快速开始](/guide/getting-started)）。

发行流程已经准备好：`.github/workflows/release.yml` 会在推送 `v*` 标签时重新构建编译器、
运行测试、用 `scripts/package-alpha.sh` 打包 ZIP 与 SHA-256 校验文件，并发布为
prerelease。发行说明存放在仓库的 `docs/releases/`。**在发行包经过实际打包与验收之前，
不会创建 Release。**

## 尚未宣称的能力

- Stage-1 自举、包管理器、语言服务器、标准库发行版、完整的 Java 泛型/数组互操作都
  **尚未实现**。
- 数值结果不保证算法稳定性或物理量单位正确。
- 除 macOS Apple Silicon（本地）与 Linux（CI）之外没有验证其他平台。

另见[已知限制](/reference/known-limitations)与
[Stage-1 路线图（英文）](/en/reference/STAGE1_ROADMAP)。
