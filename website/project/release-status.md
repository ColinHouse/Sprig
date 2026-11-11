# 发布状态

当前发布的 SDK 是 [v0.9.0-beta.1](https://github.com/ColinHouse/Sprig/releases/tag/v0.9.0-beta.1)。

| 项目 | 当前状态 |
|---|---|
| SDK / 编译器 | `0.9.0-beta.1` |
| 语言版本 | `0.8-dev`，尚未冻结 |
| 运行环境 | JDK 21+；SDK 不附带 JDK |
| VS Code 插件 | Marketplace 上的 `ColinHouse.sprig-language`，版本 `0.3.1`；需单独安装 SDK |
| 许可证 | Apache-2.0 |
| 平台 | Linux 和 macOS 为发布支持平台；Windows 是实验性预览 |

这是实验性 Beta，适合试用和反馈问题，还不建议迁移生产项目。安装发布的 SDK 即可学习当前教程，无需先从源码构建；见[安装说明](/guide/getting-started)和[插件说明](/guide/editor)。

## 这个版本新增了什么

以下变化相对于 v0.8.0-beta.1，已包含在 v0.9.0-beta.1 SDK 中：

- Gradle 和 Fabric：每个项目生成的类有自己的包 `sprig.user.<项目名>`，Gradle 构建还会带上项目自己的一份运行时，所以两个 Sprig 模组可以装进同一个游戏。
- 编译器：在函数的循环里用 `text += part` 拼长文本，耗时和文本长度成正比。用到已弃用的 Java API 会给出 `SPR-JVM-DEPRECATED` 警告，`sprig api` 会报告 `deprecated` 和 `forRemoval`。Java 调用相关的报错带 `example` 链接，指向新的 [Java 互操作实用配方](/guide/java-interop-cookbook)。
- 库：sprig-sqlite 的 `sqlite.transaction` 和 `transaction_run`，以及读写 CSV 表格的新库 [sprig-csv](https://github.com/ColinHouse/Sprig/tree/main/libraries/sprig-csv)。
- 工具：`sprig build --emit-java-only --with-runtime`，`sprig project` 会打印生成的包名。

完整变化和限制见 [v0.9.0-beta.1 发布说明（英文）](https://github.com/ColinHouse/Sprig/blob/main/docs/releases/v0.9.0-beta.1.md)。安装版本的具体功能以 `sprig capabilities --json` 为准。

## 从 v0.8.0 升级

Linux/macOS 的托管安装运行 `sprig upgrade`；手动解压的 SDK（包括 Windows）需要下载并校验新 ZIP。升级后在项目中运行 `sprig resolve`，更新记录编译器身份的锁文件。

Sprig 源码不用改。这次 MINOR 升级要改的是 Gradle 项目（包括 Fabric 模组）里的 Java 代码：

- 引用生成类的地方要加上项目的包名：`sprig.user.$M_main` 改成 `sprig.user.<项目名>.$M_main`。`javac` 会指出每一处，`./gradlew sprigInfo` 会打印包名，见 [Gradle 集成](/guide/gradle#在-java-里用-sprig-写的类)。
- Gradle 插件和编译器要来自同一个 SDK。按模板从 `SPRIG_HOME/libraries/sprig-gradle` 引入插件的项目不用管。
- 工具输出上的变化见发布说明的[升级部分（英文）](https://github.com/ColinHouse/Sprig/blob/main/docs/releases/v0.9.0-beta.1.md#upgrade-from-v080-beta1)。

## 网站、源码和发布版

网站随 `main` 分支维护。当前教程使用的功能已经发布；后续源码可能先于下次 SDK 发布增加改动。`sprig version` 确认 SDK/编译器版本，`sprig capabilities --json` 确认语言版本和实际功能。`languageVersion: 0.8-dev` 不代表 SDK 没有发布。

下面这项已经在 `main` 上，但还不在 v0.9.0-beta.1 里：

- Java 直接调用生成的函数或 `new` 一个生成的类时，模块的顶层语句会先运行，所以顶层的 `let`、`var` 已经有值，见 [Gradle 集成](/guide/gradle#calling-from-java)。v0.9.0-beta.1 里它们还是 `null` 或 `0`，而 Fabric 模组的入口就是这样调用的。

## 怎么验证的

发布流程已通过 Linux/macOS × JDK 21/26 的下载 SDK 验证。另在 macOS/JDK 26.0.1 上重新下载官方 ZIP、核对 SHA-256，跑通 `init → resolve → run`，并在隔离的主目录里用 `sprig upgrade` 从 v0.8.0-beta.1 升级成功。源码提交、校验值、CI 链接及验证范围见[发布验证记录（英文）](https://github.com/ColinHouse/Sprig/blob/main/docs/releases/validation.md)。本地冒烟检查不代表 Windows 或全部编辑器交互都经过人工验证。

## 还在计划中

- 能像值一样索引的 Java 数组，以及完整的 Java 泛型
- 用 Sprig 自己来写编译器（自举）

类型检查也不能保证数值稳定性或应用逻辑正确。

## 更早的版本

v0.8 加入了 `if` 表达式、根据实参推断泛型类型参数、契约类和 `conform`、单行类、具名函数引用、错误类、可变集合的只读视图、`@std/concurrent` 的结构化 scope 和虚拟线程、`sprig build --bundle`，并把最低 JDK 从 17 提到 21。v0.7 让 lambda 可以调用会抛 `Error` 的函数，加入了函数类型上的 `throws Error` 和 `rethrows`、把 Sprig 函数当 Java 回调传、调用 Java 变长参数方法、`@std/sets`、`@std/random`、`@std/regex` 和 `@std/dates` 模块、`@std/test` 的测试运行器、`process.run`，以及会告诉你 Python、Java 或 C 的写法在 Sprig 里怎么写的报错。v0.6 加入了语言服务器、`requires T: Comparable`、`@std/lists`、`@std/nulls`、`@std/math` 和 `@std/json_codec` 模块、Gradle 插件和 Fabric 模板，以及第 5 版锁文件格式。v0.5 是第一个 Beta 版，加入了支持本地目录、Git 和 Maven 依赖的项目管理，`sprig test`，`sprig wrap`，需要显式书写的 Java 互操作，以及官方维护的命令行、HTTP、JSON、SQLite 和 Web 库。v0.4 在 v0.3 的基础上加入了代码格式化、显式的模块重导出、表达式形式的 `match`、按 Unicode 码点处理字符串、用 `sprig api` 查看模块和项目，以及 SDK 自动升级，同时集中修复了一批正确性问题。各个版本的说明都在 [`docs/releases/`](https://github.com/ColinHouse/Sprig/tree/main/docs/releases)。
