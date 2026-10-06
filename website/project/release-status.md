# 发布状态

当前发布的版本是 [v0.7.0-beta.1](https://github.com/ColinHouse/Sprig/releases/tag/v0.7.0-beta.1)。

| | |
|---|---|
| 编译器 | `0.7.0-beta.1` |
| 语言版本 | `0.8-dev` |
| 运行环境 | JDK 17 或更新的版本（SDK 不附带 JDK） |
| 许可证 | Apache-2.0 |
| 平台 | 正式支持 Linux 和 macOS；Windows 是实验性预览 |

这是一个实验性的 Beta 版，适合拿来试用和反馈问题，还不建议迁移生产项目。

## 这个版本新增了什么

- lambda 可以调用会抛 `Error` 的函数，函数类型可以写 `throws Error`；`rethrows` 让 `lists.sort_by` 这类辅助函数只抛出你传进去的函数会抛的错误
- Java 需要回调的地方（`list.sort`、`forEach`、`removeIf`）可以直接传 Sprig 函数，也能调用 `Path.of("a", "b")`、`String.format` 这类变长参数的 Java 方法
- 新的标准库模块 `@std/sets`、`@std/random`、`@std/regex` 和 `@std/dates`；`@std/test` 有了能报告每一个失败检查的测试运行器；`process.run` 可以运行别的程序；`@std/lists`、`@std/text`、`@std/files` 和 `@std/time` 也多了不少辅助函数
- 报错照顾第一次用 Sprig 的人：从 Python、Java 或 C 带过来的写法，比如 `else if`、`readLine()`、`List<Int>`，会直接告诉你 Sprig 里怎么写；`sprig help language` 本身就是一段能运行的完整小程序
- `@std/files` 出错时一律抛 `Error`，比如 `cannot read data/x.txt: no such file`
- 新的检查：永远不会执行的 `catch`、永远不会发生的 `throws`，以及把 `Unit` 结果当成值来用
- `sprig check --bin` 和 `sprig build --bin`；不带文件的 `sprig check` 会检查项目里的每个 bin

v0.6 里有的功能都还在：语言服务器、`requires T: Comparable`、支持本地目录、Git 和 Maven 依赖的项目管理、`sprig test`、`sprig wrap`、Gradle 插件和 Fabric 模板，以及官方维护的命令行、HTTP、JSON、SQLite 和 Web 库。

还没有中央的包仓库，编译器也还不是用 Sprig 自己写的（没有自举）。你装的 SDK 具体支持什么，以 `sprig capabilities --json` 为准。完整的说明见 [v0.7.0-beta.1 发布说明（英文）](https://github.com/ColinHouse/Sprig/blob/main/docs/releases/v0.7.0-beta.1.md)。

## 从 v0.6 升级

运行 `sprig upgrade`，然后在每个项目里运行一次 `sprig resolve`，因为锁文件里记着写它的编译器版本。

有几种 v0.6 能通过的写法现在会报错，报错里会告诉你怎么改：

- 在 `@std/files` 的调用外面写 `catch problem: IOException` 不再能编译，改成捕获 `Error`
- 针对受检 Java 异常、却永远不会发生的 `catch` 或 `throws` 会报错，删掉就行
- `rethrows` 成了关键字，叫这个名字的变量或函数需要改名

## 发布之后的改动

网站按仓库 `main` 分支上的源码来写，源码可能比这个版本新。页面上介绍比 v0.7.0-beta.1 更新的内容时，会专门注明。

## 怎么验证的

这个版本的 SDK 压缩包在 Linux 和 macOS 上，分别用 JDK 17 和 26 做过验收，也核对了校验值和发布流程。具体的源码版本、压缩包的 SHA-256、运行的命令和 CI 链接，见[发布验证记录（英文）](https://github.com/ColinHouse/Sprig/blob/main/docs/releases/validation.md)。

从源码构建、没有打 tag 的版本，版本信息会标成 development；和某个干净的 tag 完全一致的构建，会标成 prerelease。

## 还在计划中

- 能像值一样索引的 Java 数组，以及完整的 Java 泛型
- 用 Sprig 自己来写编译器（自举）

另外，类型检查保证不了数值计算是否稳定。

## 更早的版本

v0.6 加入了语言服务器、`requires T: Comparable`、`@std/lists`、`@std/nulls`、`@std/math` 和 `@std/json_codec` 模块、Gradle 插件和 Fabric 模板，以及第 5 版锁文件格式。v0.5 是第一个 Beta 版，加入了支持本地目录、Git 和 Maven 依赖的项目管理，`sprig test`，`sprig wrap`，需要显式书写的 Java 互操作，以及官方维护的命令行、HTTP、JSON、SQLite 和 Web 库。v0.4 在 v0.3 的基础上加入了代码格式化、显式的模块重导出、表达式形式的 `match`、按 Unicode 码点处理字符串、用 `sprig api` 查看模块和项目，以及 SDK 自动升级，同时集中修复了一批正确性问题。各个版本的说明都在 [`docs/releases/`](https://github.com/ColinHouse/Sprig/tree/main/docs/releases)。
