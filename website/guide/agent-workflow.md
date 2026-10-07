# 和 AI 助手一起写代码

Sprig 在设计时就考虑到了 AI 编程助手。编译器的报错有固定的错误码、准确的位置和修改提示，还能输出成 JSON，助手可以照着改，不用去猜一段文字是什么意思。

不过要说清楚：这并不能保证 AI 写出来的代码就是对的。它能保证的是，出错时你和助手都能看清错在哪里、为什么错，修改的过程也方便人来检查。

## 先问问装好的编译器

不同版本的 SDK 功能不一样。让助手先用这几条命令了解手上的编译器，不要凭记忆写代码：

```sh
sprig version
sprig capabilities --json
sprig help language --json
sprig api java.time.LocalDate --json
```

`capabilities` 列出的是这个 SDK 实际实现了的功能。注意，一段代码能通过语法解析，不代表后面每一步都没问题。类型检查、生成 Java、在 JVM 上运行，你的改动依赖哪一步，就要实际跑过哪一步才算数。

第一次用 Sprig 的话（不管是你还是助手），先看 `sprig help language`。它就是一段完整的小程序，用到了 `if/elif/else`、循环、变量、函数和读标准输入。每个主题还会列出内置方法，并附上一个示例程序。从 Python、Java 或 C 带过来的写法，比如 `else if`、`readLine()`、`List<Int>`，报错会直接告诉你 Sprig 里怎么写。

## 一个修错的循环

1. 先读相关的参考页和附近已有的测试。如果是在 Sprig 仓库里工作，还要先读 `AGENTS.md`。
2. 写一个能说明问题的小程序，运行 `sprig check --json path/to/file.spr`。
3. 出错时，读错误码、位置、期望的类型和实际的类型，以及修改提示。还不清楚的话，运行 `sprig explain <错误码> --json`。
4. 想明白这个错误意味着什么，再动手改。编译器不会替你选择有损的转换，也不会因为你需要就放宽对 Java API 的限制。
5. 运行 `sprig test --json`（前提是你的 SDK 支持这个命令），再跑相关的测试。给 Sprig 仓库贡献代码时，还要跑完整的贡献检查。最后自己看一遍改动，确认没有把生成的文件提交进去。

JSON 里各个字段的含义见 [Agent 工具参考（英文）](/en/reference/tooling/agent-guide)，所有错误码见[错误码（英文）](/en/reference/tooling/diagnostic-codes)。

## 例子：修一个类型错误

把字符串赋给整数：

<<< @/snippets/tutorial/type_error.spr

`sprig check --json` 返回的错误里有这些字段（省略了其余部分）：

```json
{
  "code": "SPR-TYPE-ASSIGN",
  "phase": "TYPE",
  "message": "Type mismatch in initializer",
  "expectedType": "Int",
  "actualType": "String",
  "relatedHelp": "types"
}
```

`expectedType` 和 `actualType` 说得很清楚：这里需要 `Int`，给的却是 `String`。接下来怎么改，取决于你本来想做什么：要么把值改成整数，要么把变量声明成 `String`。不要只是为了让编译通过，就随手加一个类型转换。

入门教程里也有好几个故意写错的例子，比如[第 2 章](/tutorial/ch02-values)，它们都由文档检查实际验证过。给 Sprig 加新功能时也是这样：既要有能通过的程序，也要有应该被拒绝的程序，测试才能证明编译器真的会拒绝错误的写法。

## 用 AI 写 Sprig，效果怎么样

维护者用一个比较便宜的编程模型试过上面的工作方式。这只是一次早期的尝试：没有对照实验，没有拿 Java 做对比，也没有测量效率。所以它说明不了 Sprig 比 Java 更适合 AI，也说明不了 AI 用 Sprig 写得更对、更快。

这个项目想验证的想法范围更小：固定的错误码、可以查询的 Java API 和功能清单，能让修错的每一步都有据可查。欢迎你按上面的流程自己试试。反馈时请附上 SDK 版本、能复现问题的最小源码、运行的命令和完整的报错。

## 接下来

- [入门教程](/tutorial/)
- [已发布版本和源码的功能状态（英文）](/en/reference/language/feature-status)
- [项目测试（英文）](/en/reference/tooling/testing)
- [参与贡献（英文）](/en/project/contributing)
