# 发布状态

当前发布的版本是 [v0.5.0-beta.1](https://github.com/ColinHouse/Sprig/releases/tag/v0.5.0-beta.1)。

| | |
|---|---|
| 编译器 | `0.5.0-beta.1` |
| 语言版本 | `0.8-dev` |
| 运行环境 | JDK 17 或更新的版本（SDK 不附带 JDK） |
| 许可证 | Apache-2.0 |
| 平台 | 正式支持 Linux 和 macOS；Windows 是实验性预览 |

这是一个实验性的 Beta 版，适合拿来试用和反馈问题，还不建议迁移生产项目。

## 这个版本有什么

- 项目和依赖管理：`sprig add`、`resolve`、`deps`，支持本地目录、Git 仓库和 Maven 依赖（第 4 版锁文件）
- 项目测试：`sprig test`
- 给 Java 类生成 Sprig 包装代码：`sprig wrap`
- 规则明确、需要显式书写的 Java 互操作
- 官方维护的命令行、HTTP、JSON、SQLite 和 Web 库
- `@std` 标准库：文件、进程、文本、时间、JSON 和测试辅助
- 三个较大的示例程序

还没有中央的包仓库，编译器也还不是用 Sprig 自己写的（没有自举）。你装的 SDK 具体支持什么，以 `sprig capabilities --json` 为准。完整的说明见 [v0.5.0-beta.1 发布说明（英文）](https://github.com/ColinHouse/Sprig/blob/main/docs/releases/v0.5.0-beta.1.md)。

## 发布之后的改动

仓库 `main` 分支上的源码比这个版本新，网站也是按最新的源码写的，所以有几处和已发布的 SDK 不一样：

- 新增了 Gradle 插件和 Fabric 模板，见 [Gradle 集成](/guide/gradle)
- 锁文件格式升到第 5 版，`@std` 不再写进锁文件
- 新增了 `@std/math` 标准库模块
- `if x != null and ...` 这样的条件里，`and` 右边可以把 `x` 当作非空
- 一些报错的提示文字更清楚了，错误码没有变

想提前用上这些改动，可以从源码构建，见[快速开始](/guide/getting-started)里的「Windows 和从源码构建」。

## 怎么验证的

这个版本的 SDK 压缩包在 Linux 和 macOS 上，分别用 JDK 17 和 26 做过验收，也核对了校验值和发布流程。具体的源码版本、压缩包的 SHA-256、运行的命令和 CI 链接，见[发布验证记录（英文）](https://github.com/ColinHouse/Sprig/blob/main/docs/releases/validation.md)。

从源码构建、没有打 tag 的版本，版本信息会标成 development；和某个干净的 tag 完全一致的构建，会标成 prerelease。

## 还在计划中

- 适配 Java 的函数式接口（SAM）、数组和完整的泛型
- 语言服务器（LSP）和 IDE 支持
- 用 Sprig 自己来写编译器（自举）

另外，类型检查保证不了数值计算是否稳定。

## 更早的版本

v0.4 在 v0.3 的基础上加入了代码格式化、显式的模块重导出、表达式形式的 `match`、按 Unicode 码点处理字符串、用 `sprig api` 查看模块和项目，以及 SDK 自动升级，同时集中修复了一批正确性问题。各个版本的说明都在 [`docs/releases/`](https://github.com/ColinHouse/Sprig/tree/main/docs/releases)。
