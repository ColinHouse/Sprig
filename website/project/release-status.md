# 发布状态

当前发布的版本是 [v0.7.1-beta.1](https://github.com/ColinHouse/Sprig/releases/tag/v0.7.1-beta.1)。

| | |
|---|---|
| 编译器 | `0.7.1-beta.1` |
| 语言版本 | `0.8-dev` |
| 运行环境 | JDK 17 或更新的版本（SDK 不附带 JDK） |
| 许可证 | Apache-2.0 |
| 平台 | 正式支持 Linux 和 macOS；Windows 是实验性预览 |

这是一个实验性的 Beta 版，适合拿来试用和反馈问题，还不建议迁移生产项目。

## 这个版本新增了什么

v0.7.1-beta.1 修好了一次评测用真实程序和 Java 对照在 v0.7.0-beta.1 里发现的问题，也让调用 Java 更顺手：

- `for i in range(n)` 改成计数，不再先建出整个列表，循环多长都只占固定的内存
- 顶层代码里有好几个热循环时，跑得和同样的 Java 一样快
- 把可能为 `null` 的值拼进文本会报错，不再打印出 `null`
- `Error` 不管怎么变成文本，显示的都是它的消息，`toString()` 也一样
- 字符串有了 `a.compareTo(b)`，正好是 Java `Comparator` 要的
- `Int` 可以直接传给 Java 的 `int` 参数，运行时检查范围；Java 签名里的通配符类型，比如 `List<? extends Entity>`，按它的上下界来读
- `sprig api` 会显示写在每个声明上方的注释
- 帮助和 `sprig capabilities` 说的就是编译器实际的行为，包括 `==` 比较两个类对象时看的是不是同一个对象

这是一个补丁版本：v0.7.0-beta.1 和 v0.6 里有的功能都还在。还没有中央的包仓库，编译器也还不是用 Sprig 自己写的（没有自举）。你装的 SDK 具体支持什么，以 `sprig capabilities --json` 为准。完整的说明见 [v0.7.1-beta.1 发布说明（英文）](https://github.com/ColinHouse/Sprig/blob/main/docs/releases/v0.7.1-beta.1.md)。

## 从 v0.7.0 升级

运行 `sprig upgrade`，然后在每个项目里运行一次 `sprig resolve`，因为锁文件里记着写它的编译器版本。

有两类 v0.7.0 能通过的写法现在会报错，因为它们可能打印出 `null`，或者运行时才出错。报错里会告诉你怎么改：

- 把可能为 `null` 的值拼进 `String`：先检查，或者用 `@std/nulls.spr` 里的 `or_else` 给个默认值。Java 方法返回的值也算可能为 `null`，`toString()` 的结果不算
- 把 Java 异常的 `message` 当 `String` 用：Java 可能让它是 `null`，所以它现在是 `String?`。直接拼接异常本身，或者先检查消息

还有一处写法照样能编译，但打印出来的不一样了：`Error` 的 `toString()` 现在返回它的消息，不再带 `sprig.runtime.SprigError: ` 前缀。

从 v0.6 升级的话，还要看一下 [v0.7.0-beta.1 发布说明（英文）](https://github.com/ColinHouse/Sprig/blob/main/docs/releases/v0.7.0-beta.1.md)里的升级部分。

## 发布之后的改动

网站按仓库 `main` 分支上的源码来写，源码可能比这个版本新。页面上介绍比 v0.7.1-beta.1 更新的内容时，会专门注明。目前这样的内容是语言服务器的快速修复，以及泛型调用根据你传的参数算出类型参数。

## 怎么验证的

这个版本的 SDK 压缩包在 Linux 和 macOS 上，分别用 JDK 17 和 26 做过验收，也核对了校验值和发布流程。具体的源码版本、压缩包的 SHA-256、运行的命令和 CI 链接，见[发布验证记录（英文）](https://github.com/ColinHouse/Sprig/blob/main/docs/releases/validation.md)。

从源码构建、没有打 tag 的版本，版本信息会标成 development；和某个干净的 tag 完全一致的构建，会标成 prerelease。

## 还在计划中

- 能像值一样索引的 Java 数组，以及完整的 Java 泛型
- 用 Sprig 自己来写编译器（自举）

另外，类型检查保证不了数值计算是否稳定。

## 更早的版本

v0.7 让 lambda 可以调用会抛 `Error` 的函数，加入了函数类型上的 `throws Error` 和 `rethrows`、把 Sprig 函数当 Java 回调传、调用 Java 变长参数方法、`@std/sets`、`@std/random`、`@std/regex` 和 `@std/dates` 模块、`@std/test` 的测试运行器、`process.run`，以及会告诉你 Python、Java 或 C 的写法在 Sprig 里怎么写的报错。v0.6 加入了语言服务器、`requires T: Comparable`、`@std/lists`、`@std/nulls`、`@std/math` 和 `@std/json_codec` 模块、Gradle 插件和 Fabric 模板，以及第 5 版锁文件格式。v0.5 是第一个 Beta 版，加入了支持本地目录、Git 和 Maven 依赖的项目管理，`sprig test`，`sprig wrap`，需要显式书写的 Java 互操作，以及官方维护的命令行、HTTP、JSON、SQLite 和 Web 库。v0.4 在 v0.3 的基础上加入了代码格式化、显式的模块重导出、表达式形式的 `match`、按 Unicode 码点处理字符串、用 `sprig api` 查看模块和项目，以及 SDK 自动升级，同时集中修复了一批正确性问题。各个版本的说明都在 [`docs/releases/`](https://github.com/ColinHouse/Sprig/tree/main/docs/releases)。
