# 发布状态

当前发布的版本是 [v0.6.0-beta.1](https://github.com/ColinHouse/Sprig/releases/tag/v0.6.0-beta.1)。

| | |
|---|---|
| 编译器 | `0.6.0-beta.1` |
| 语言版本 | `0.8-dev` |
| 运行环境 | JDK 17 或更新的版本（SDK 不附带 JDK） |
| 许可证 | Apache-2.0 |
| 平台 | 正式支持 Linux 和 macOS；Windows 是实验性预览 |

这是一个实验性的 Beta 版，适合拿来试用和反馈问题，还不建议迁移生产项目。

## 这个版本新增了什么

- 语言服务器 `sprig lsp`，Neovim、Helix 等编辑器都能接入；[VS Code 插件](/guide/editor)会自动启动它
- `requires T: Comparable`：泛型代码可以比较大小、排序
- 新的标准库模块 `@std/lists`、`@std/nulls`、`@std/math` 和 `@std/json_codec`；`@std/text` 加了补齐用的函数，`@std/test` 加了会打印出两边值的断言，`@std/process` 可以读标准输入、写标准错误、指定退出码
- `if x != null and ...` 这样的条件里，`and` 右边可以把 `x` 当作非空
- Gradle 插件和 Fabric 模板，见 [Gradle 集成](/guide/gradle)
- 锁文件格式升到第 5 版，`@std` 不再写进锁文件
- 一批正确性修复：报错位置、和 Java 关键字同名的名字、顶层代码的执行顺序
- 命令变快了：编译器的 JVM 只启用 C1 即时编译器

v0.5 里有的功能都还在：支持本地目录、Git 和 Maven 依赖的项目管理，`sprig test`，`sprig wrap`，需要显式书写的 Java 互操作，以及官方维护的命令行、HTTP、JSON、SQLite 和 Web 库。

还没有中央的包仓库，编译器也还不是用 Sprig 自己写的（没有自举）。你装的 SDK 具体支持什么，以 `sprig capabilities --json` 为准。完整的说明见 [v0.6.0-beta.1 发布说明（英文）](https://github.com/ColinHouse/Sprig/blob/main/docs/releases/v0.6.0-beta.1.md)。

## 从 v0.5 升级

运行 `sprig upgrade`，然后在每个项目里运行一次 `sprig resolve`。v0.5.0-beta.1 写的锁文件是第 4 版，新的编译器不会直接读它，而是提示你重新解析。另外，有几种 v0.5 能通过的写法现在会报错，比如顶层代码用到写在后面的变量；报错里会告诉你怎么改。

## 发布之后的改动

网站按仓库 `main` 分支上的源码来写，源码可能比这个版本新。页面上介绍比 v0.6.0-beta.1 更新的内容时，会专门注明。

## 怎么验证的

这个版本的 SDK 压缩包在 Linux 和 macOS 上，分别用 JDK 17 和 26 做过验收，也核对了校验值和发布流程。具体的源码版本、压缩包的 SHA-256、运行的命令和 CI 链接，见[发布验证记录（英文）](https://github.com/ColinHouse/Sprig/blob/main/docs/releases/validation.md)。

从源码构建、没有打 tag 的版本，版本信息会标成 development；和某个干净的 tag 完全一致的构建，会标成 prerelease。

## 还在计划中

- 适配 Java 的函数式接口（SAM）、数组和完整的泛型
- 用 Sprig 自己来写编译器（自举）

另外，类型检查保证不了数值计算是否稳定。

## 更早的版本

v0.5 是第一个 Beta 版，加入了支持本地目录、Git 和 Maven 依赖的项目管理，`sprig test`，`sprig wrap`，需要显式书写的 Java 互操作，以及官方维护的命令行、HTTP、JSON、SQLite 和 Web 库。v0.4 在 v0.3 的基础上加入了代码格式化、显式的模块重导出、表达式形式的 `match`、按 Unicode 码点处理字符串、用 `sprig api` 查看模块和项目，以及 SDK 自动升级，同时集中修复了一批正确性问题。各个版本的说明都在 [`docs/releases/`](https://github.com/ColinHouse/Sprig/tree/main/docs/releases)。
