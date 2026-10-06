---
layout: home
hero:
  name: Sprig
  text: 写给人看，<br>也写给 AI 看
  tagline: 一门跑在 JVM 上的小语言，语法像 Python。写错了，编译器会告诉你错在哪一行、为什么错、可以怎么改。
  image:
    src: /logo-round.png
    alt: Sprig
  actions:
    - theme: brand
      text: 开始入门教程
      link: /tutorial
    - theme: alt
      text: 安装
      link: /guide/getting-started
    - theme: alt
      text: GitHub
      link: https://github.com/ColinHouse/Sprig
features:
  - title: 漏掉的分支会被找出来
    details: 给一个类型加了新的情况，所有忘了处理它的 match 都会在编译时报错，不会等到运行时才出问题。
  - title: 空值要先检查
    details: 可能为空的值写成 T?，用之前必须判断。Java 方法返回的对象也一样。
  - title: 报错可以直接拿来修
    details: 每个错误都有固定的错误码、准确的位置和修改提示，还能输出成 JSON，交给编辑器或 AI 助手处理。
---

## 先看一眼

<<< @/snippets/home/first_look.spr

运行结果：

```text
3.14159
7.0
MONDAY
```

如果以后给 `Shape` 加一种 `Triangle`，却忘了改 `area`，`sprig check` 会直接指出来：

```text
SPR-MATCH-NONEXHAUSTIVE [FLOW] main.spr:9:12: Missing case: Shape.Triangle
  hint: Add 'case Shape.Triangle:' (there is no default case)
```

## 能用来做什么

- 命令行小工具和自动化脚本
- 处理 JSON 和文本数据
- 小型 Web 服务和 SQLite 应用（见[示例](/examples)）
- 调用 Maven 上现成的 Java 库
- 写 Minecraft 模组里的业务逻辑（见 [Fabric 集成](/guide/fabric)）

## 现在能用吗？

Sprig 还在早期。当前发布的版本是实验性的 v0.6.0-beta.1：上面列的事情都已经能做，每一项都有能直接运行的例子。不过它还没有泛型推断和接口，也不建议用在生产环境。完整清单见[已知限制](/en/reference/language/known-limitations)（英文）。

## 五分钟跑起来

需要 JDK 17 或更新的版本。[装好 SDK](/guide/getting-started) 以后：

```bash
sprig init hello
cd hello
sprig resolve
sprig run
```

看到 `Hello, Sprig!` 就可以开始[入门教程](/tutorial)了：用半小时写一个记账小工具。

## 参与进来

- 发现了 bug，或者觉得哪里设计得别扭：欢迎[开一个 issue](https://github.com/ColinHouse/Sprig/issues)。
- 想贡献代码：先看[贡献指南](/en/project/contributing)（英文），再挑一个[开着的 issue](https://github.com/ColinHouse/Sprig/issues?q=is%3Aissue+is%3Aopen)。
- 用 AI 助手写的代码也欢迎，但请你自己读懂、测过再提交。

[English](/en/) · [Apache-2.0](https://github.com/ColinHouse/Sprig/blob/main/LICENSE) · [GitHub](https://github.com/ColinHouse/Sprig)
