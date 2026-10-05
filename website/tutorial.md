# 入门教程：做一个记账小工具

这篇教程带你从零写一个小工具：读一份消费记录，按类别算出每类花了多少钱。

跟着做下来大约半小时。不需要会 Java，写过一点 Python 或 JavaScript 就够了。每一步的代码都能直接复制运行，运行结果就写在代码下面，你可以对照着看。

中间会故意写错几次。在 Sprig 里，读懂报错本身就是学这门语言的一部分。

## 0. 准备

你需要 JDK 17 或更新的版本，以及 Sprig SDK。还没装的话，先看[快速开始](/guide/getting-started)。

装好以后，新建一个项目：

```sh
sprig init ledger
cd ledger
sprig resolve
sprig run
```

看到 `Hello, Sprig!` 就说明一切正常。打开 `src/main.spr`，里面是这样的：

```sprig
# ledger entry point.

func main() -> Unit:
    print("Hello, Sprig!")

main()
```

接下来的每一步，都把示例代码粘进 `src/main.spr`，替换掉原来的内容，然后运行 `sprig run`。

> 教程里的报错信息来自 Sprig 的最新源码。如果你装的是 v0.5.0-beta.1，个别提示的措辞会不太一样，但错误码相同。

## 1. 值和类型

<<< @/snippets/tutorial/ledger/01_values.spr

```text
Coffee
3750
true
```

- `let` 声明的名字之后不能再改，`var` 可以。
- 类型可以不写，Sprig 会从值推断出来：`item` 是 `String`，`price` 是 `Int`。
- `cups > 2` 的结果是 `Bool`。`if` 和 `while` 的条件只接受 `Bool`，Sprig 不会把 `0` 或空字符串当成假。

现在故意写错一次：把价格写成小数，再交给一个整数变量。

<<< @/snippets/tutorial/ledger/01_values_error.spr

```text
SPR-NUM-CONVERSION [TYPE] main.spr:2:18: Cannot implicitly convert Float to Int in initializer; precision or range may change (expected Int, actual Float)
  hint: Use toIntExact() if the value must be whole, toIntTrunc() to drop the fraction, or java.lang.Math.round(x) to round to the nearest Int.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

很多语言会悄悄截掉小数部分，Sprig 不会。提示里列出了三种做法：要求它必须是整数、直接截掉小数、四舍五入。具体用哪一种，由你决定。

开头那一串 `SPR-NUM-CONVERSION` 是错误码。每种错误都有固定的码，后面第 8 步会讲怎么用它。

::: details 练习：在最后加一行 `item = "Tea"` 会怎样？
`item` 是用 `let` 声明的，编译器会拒绝：

```text
SPR-NAME-LET-ASSIGN [TYPE] main.spr:9:1: Cannot assign to immutable binding 'item'; declare it with var
```

如果这个名字确实需要改，就把 `let` 换成 `var`。
:::

## 2. 函数，以及为什么金额要存成"分"

<<< @/snippets/tutorial/ledger/02_money.spr

```text
6650
66.50
0.30000000000000004
```

先看最后一行：`0.1 + 0.2` 打印出来的是 `0.30000000000000004`。这不是 Sprig 的问题，计算机里的浮点数本来就没法精确表示 0.1。所以记账时，我们把金额存成整数"分"：12.50 元就是 `1250`。

再看函数本身：

- 参数和返回值都要写类型。看签名就知道 `total` 收一个整数列表，返回一个整数。
- `cents.divTrunc(100)` 是整数除法，会直接截掉小数；`%` 取余数。
- `today` 后面写了类型 `List[Int]`。不写的话，列表默认是可以修改的 `MutableList[Int]`，而 `total` 要的是只读的 `List[Int]`。Sprig 不会帮你悄悄转换，写清楚就好。

::: details 练习：为什么不用 `cents / 100`？
Sprig 不允许两个整数直接用 `/` 相除：

<<< @/snippets/tutorial/ledger/02_money_error.spr

```text
SPR-NUM-DIVISION [TYPE] main.spr:2:7: Integer / would truncate; use a.divTrunc(b), or convert both operands explicitly (expected explicit division, actual Int / Int)
  hint: Use divTrunc for deliberate truncation; use checked toFloat() for exact Float conversion.
```

`7 / 2` 等于 3 还是 3.5？不同语言的答案不一样。Sprig 让你自己写出来：想要 3 就用 `divTrunc`，想要 3.5 就先转成 `Float`。
:::

## 3. 用类表示一笔支出

<<< @/snippets/tutorial/ledger/03_expense.spr

```text
Expense(item=Coffee, cents=1250, note=)
October
true
```

- 每个字段都要写类型。`let` 字段创建后不能改，`var` 字段可以。
- `note` 有默认值，创建时可以不写。
- 创建对象时必须写字段名，比如 `Expense(item="Coffee", cents=1250)`。看起来多打了几个字，但以后读代码的人不用去翻字段顺序。
- 方法里可以直接用字段，`is_big` 里的 `cents` 就是这个对象自己的金额。
- `print` 一个对象会列出它的所有字段，调试的时候很方便。

::: details 练习：把 `cents=1250` 删掉会怎样？
`cents` 没有默认值，所以创建时必须提供：

```text
SPR-CALL-MISSING-FIELD [TYPE] main.spr:9:14: Missing required field 'cents:Int'
```
:::

## 4. 消费类别：variant 和 match

一笔支出总属于某个类别。类别只有固定的几种，正好用 `variant` 来写：

<<< @/snippets/tutorial/ledger/04_category.spr

```text
food
other: movie
```

- `variant` 把一个值可能的样子全部列出来。`Food` 和 `Transport` 不带数据，`Other` 带一个 `label`。
- `match` 按类别分别处理。`case Category.Other as other:` 把值绑定到 `other`，之后就能用 `other.label`。
- `match` 没有"其他情况"这种兜底分支，每一种都要写出来。

最后这条规则，要等你漏掉一种情况时才显出用处。把 `match` 里 `Other` 那个分支（`case Category.Other as other:` 和它下面那一行）删掉，再运行：

```text
SPR-MATCH-NONEXHAUSTIVE [FLOW] main.spr:7:12: Missing case: Category.Other
  hint: Add 'case Category.Other:' (there is no default case)
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

设想一个月以后，你给 `Category` 加了一种 `Shopping`。程序里所有忘了处理它的 `match`，编译时都会被一个个找出来，不会等到运行时才出问题。

## 5. 找不到怎么办：可空值

<<< @/snippets/tutorial/ledger/05_lookup.spr

```text
3600
no tea today
MONDAY
```

- 返回类型 `Expense?` 末尾的问号表示"可能是 `null`"：按名字找，不一定找得到。
- 用之前必须先判断。写了 `if taxi != null:` 以后，Sprig 就知道在这个分支里 `taxi` 一定有值。
- Java 的方法也一样。Java 返回的对象，Sprig 一律当作可能为 `null`，所以 `LocalDate.parse` 的结果也要先判断。

如果不判断，直接用：

<<< @/snippets/tutorial/ledger/05_lookup_error.spr

```text
SPR-TYPE-NULLABLE [TYPE] main.spr:12:12: Cannot access 'cents' on a value that may be null (receiver type Expense?)
  hint: Bind the receiver to a let and check it with 'if value != null:' before using 'cents'.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

除了把代码包进 `if x != null:`，还可以提前返回：先写 `if x == null: return ...`，这之后的代码里 `x` 就不再是可空的了。

## 6. 会失败的操作：把错误写进类型

用户输入的金额可能是 `12.50`，也可能是 `abc`。解析失败的时候，与其返回一个看不出问题的默认值，不如直接报错：

<<< @/snippets/tutorial/ledger/06_parse.spr

```text
1250
800
skipped: not an amount: twelve
```

- 签名里的 `throws Error` 告诉调用的人：这个函数可能失败。
- 失败时用 `throw Error("...")` 带上原因。
- 调用方有两个选择：用 `try` / `catch` 处理，或者在自己的签名里也写上 `throws Error`，交给上一层处理。
- `if units == null or ...: throw ...` 之后，`units` 就被当作有值了。这和第 5 步的提前返回是一个道理。

::: details 练习：在一个没写 `throws` 的函数里调用 `parse_amount` 会怎样？
比如下面的 `show`，它调用了 `parse_amount`，却既没有 `try`，也没有写 `throws`：

<<< @/snippets/tutorial/ledger/06_parse_unhandled.spr

编译器会要求你二选一：

```text
SPR-FLOW-THROWS [FLOW] main.spr:14:11: Call may throw Error; declare 'throws Error' or handle it with try/catch
  hint: Sprig keeps recoverable errors explicit; there is no implicit propagation.
```
:::

## 7. 合起来：读文件，出报表

现在把前面的东西拼成一个能用的程序。真实的工具会去读你的消费记录文件；为了让示例直接就能运行，程序先把几行示例数据写进一个临时文件，再把它读回来。

开头导入要用到的模块：`@std/files` 读写文件，`@std/text` 处理文本。后者起名叫 `strings`，免得和 `parse_amount` 的参数 `text` 重名。

<<< @/snippets/tutorial/ledger/07_report.spr#imports

然后是数据：类别和支出。

<<< @/snippets/tutorial/ledger/07_report.spr#model

第 2 步的 `format_money` 和第 6 步的 `parse_amount` 原样搬过来，这里不再重复。再加两个小函数，负责把文字转成类别，再把类别转回文字：

<<< @/snippets/tutorial/ledger/07_report.spr#categories

把一行 `Coffee, 12.50, food` 解析成一笔支出：

<<< @/snippets/tutorial/ledger/07_report.spr#parse

按类别汇总。这里需要一边遍历一边累加，所以用可以修改的 `MutableMap`。从 map 里取值得到的是 `Int?`，因为这个类别可能还没有记录：

<<< @/snippets/tutorial/ledger/07_report.spr#report

最后是入口：写文件、读文件、逐行解析、出报表：

<<< @/snippets/tutorial/ledger/07_report.spr#main

运行结果：

```text
Food: 30.50
Transport: 36.00
Other: 45.00
Total: 111.50
```

完整代码在[这里](https://github.com/ColinHouse/Sprig/blob/main/website/snippets/tutorial/ledger/07_report.spr)。

::: details 练习：把 `Movie` 那行的金额改成 `45.5` 会怎样？
`parse_amount` 要求小数点后正好两位，所以这一行会解析失败。错误一路抛到最外层的 `catch`：

```text
bad data: not an amount: 45.5
```

想读你自己的记录，就把 `temp_file()` 和 `write_utf8` 那两行去掉，把 `path` 换成你的文件路径。
:::

## 8. 卡住了怎么办

写 Sprig 的时候，大多数问题都不用去猜，直接问编译器就行。

**只检查，不运行。** `sprig check` 比 `sprig run` 快，而且一次会列出所有错误：

```text
$ sprig check
SPR-MATCH-NONEXHAUSTIVE [FLOW] main.spr:7:12: Missing case: Category.Other
  hint: Add 'case Category.Other:' (there is no default case)
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

**看不懂某个错误？** 把错误码交给 `sprig explain`，它会解释原因、常见的写错方式，并给出正反两个例子：

```text
$ sprig explain SPR-MATCH-NONEXHAUSTIVE
SPR-MATCH-NONEXHAUSTIVE: Every enum/variant case must have a match branch; there is no default.
Why it matters: An exhaustive match makes adding a variant case a compile-time change, not a silent fallthrough.
Common causes:
  - A variant or enum gained a case, or the match omitted one.
Safe fixes:
  - Add an explicit case branch for every missing case reported by the diagnostic.
...
```

**忘了语法？** 用 `sprig help` 加主题名，比如 `sprig help nullability`、`sprig help numerics`、`sprig help match`。主题列表见 `sprig help`。

**不确定一个 Java 方法怎么用？** `sprig api` 会告诉你它在 Sprig 里的签名，比如第 5 步的 `LocalDate.parse` 为什么返回 `LocalDate?`：

```text
$ sprig api java.time.LocalDate --member parse
Java API: java.time.LocalDate
constructors:
staticMethods:
  public static java.time.LocalDate java.time.LocalDate.parse(java.lang.CharSequence) => parse(CharSequence) -> LocalDate?
  ...
```

上面这些命令都可以加 `--json`，输出结构化的结果：错误码、精确到列的位置、修改建议。如果你用 AI 编程助手写代码，可以让它自己调用这些命令。它拿到的是确切的信息，不用从一段文字里猜问题出在哪。Sprig 从设计之初就考虑了这种用法。

## 下一步

- 想看更大一点的程序：[任务清单示例](https://github.com/ColinHouse/Sprig/tree/main/examples/task-tracker)是一个完整的命令行工具，用 JSON 文件保存数据。
- 想把语法系统过一遍：[语言速查](/guide/language-tour)。
- 想用别人写好的库：[项目与依赖](/guide/projects)讲本地包、Git 和 Maven 依赖；[JVM 互操作](/guide/jvm-interop)讲怎么调用 Java 库。
- 想做点更大的东西：[Web 与 SQLite](/guide/web-sqlite)，或者[用 Sprig 写 Minecraft 模组的逻辑](/guide/fabric)。
- 遇到问题，或者觉得哪里设计得别扭：欢迎[开一个 issue](https://github.com/ColinHouse/Sprig/issues)。
