# 16. 项目：记账小工具

最后一章把前面学的合成一个能用的程序：读一份消费记录，按类别算出每类花了多少钱。每一步的代码都能直接运行，结果写在下面；中间会故意写错几次，和前面的章节一样。

## 16.1 准备

```bash
sprig init ledger
cd ledger
sprig resolve
sprig run
```

看到 `Hello, Sprig!` 就可以开始了。接下来的每一步，把示例代码粘进 `src/main.spr` 替换原来的内容，然后 `sprig run`。

## 16.2 值和类型

<<< @/snippets/tutorial/ledger/01_values.spr

```text
Coffee
3750
true
```

第 2 章的内容：`let` 不可改、类型推断、条件必须是 `Bool`。现在故意写错一次，把价格写成小数再交给一个整数变量：

<<< @/snippets/tutorial/ledger/01_values_error.spr

```text
SPR-NUM-CONVERSION [TYPE] main.spr:2:18: Cannot implicitly convert Float to Int in initializer; precision or range may change (expected Int, actual Float)
  hint: Use toIntExact() if the value must be whole, toIntTrunc() to drop the fraction, or java.lang.Math.round(x) to round to the nearest Int.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

很多语言会悄悄截掉小数部分，Sprig 不会。提示里列出了三种做法，用哪一种由你决定。

## 16.3 函数，以及为什么金额要存成"分"

<<< @/snippets/tutorial/ledger/02_money.spr

```text
6650
66.50
0.30000000000000004
```

先看最后一行：`0.1 + 0.2` 打印出来的是 `0.30000000000000004`。这不是 Sprig 的问题，计算机里的浮点数本来就没法精确表示 0.1。所以记账时，我们把金额存成整数"分"：12.50 元就是 `1250`。

再看函数本身：

- 参数和返回值都写了类型。看签名就知道 `total` 收一个整数列表，返回一个整数。
- `cents.divTrunc(100)` 是整数除法，`%` 取余数。
- `today` 后面写了类型 `List[Int]`。不写的话它是 `MutableList[Int]`，照样能传给要 `List[Int]` 的 `total`（第 5 章）；写出来是为了说明这个列表之后不会改。

::: details 练习：为什么不用 `cents / 100`？
Sprig 不允许两个整数直接用 `/` 相除：

<<< @/snippets/tutorial/ledger/02_money_error.spr

```text
SPR-NUM-DIVISION [TYPE] main.spr:2:7: Integer / would truncate; use a.divTrunc(b), or convert both operands explicitly (expected explicit division, actual Int / Int)
  hint: Write cents.divTrunc(100) to drop the remainder on purpose, or cents.toFloatExact() / 100.toFloatExact() for a Float result. text.fixed(value, decimals) from @std/text.spr prints a Float with that many decimals.
```

`7 / 2` 等于 3 还是 3.5？不同语言的答案不一样。Sprig 让你自己写出来。
:::

## 16.4 用类表示一笔支出

<<< @/snippets/tutorial/ledger/03_expense.spr

```text
Expense(item=Coffee, cents=1250, note=)
October
true
```

- 每个字段都写类型，`let` 字段创建后不能改，`var` 字段可以。
- `note` 有默认值，创建时可以不写。
- 创建对象必须写字段名：`Expense(item="Coffee", cents=1250)`。
- 方法里直接用字段，`is_big` 里的 `cents` 就是这个对象自己的金额。

::: details 练习：把 `cents=1250` 删掉会怎样？
`cents` 没有默认值，所以创建时必须提供：

```text
SPR-CALL-MISSING-FIELD [TYPE] main.spr:9:14: Missing required field 'cents:Int'
```
:::

## 16.5 消费类别：variant 和 match

一笔支出总属于某个类别。类别只有固定的几种，正好用 `variant`：

<<< @/snippets/tutorial/ledger/04_category.spr

```text
food
other: movie
```

- `Food` 和 `Transport` 不带数据，`Other` 带一个 `label`。
- `case Category.Other as other:` 把值绑定到 `other`，之后就能用 `other.label`。
- `match` 没有兜底分支，每一种都要写出来。

把 `match` 里 `Other` 那个分支删掉，再运行：

```text
SPR-MATCH-NONEXHAUSTIVE [FLOW] main.spr:7:12: Missing case: Category.Other
  hint: Add 'case Category.Other:' (there is no default case)
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

设想一个月以后，你给 `Category` 加了一种 `Shopping`。程序里所有忘了处理它的 `match`，编译时都会被一个个找出来。

## 16.6 找不到怎么办：可空值

<<< @/snippets/tutorial/ledger/05_lookup.spr

```text
3600
no tea today
MONDAY
```

- 返回类型 `Expense?` 表示"可能是 `null`"：按名字找，不一定找得到。
- 写了 `if taxi != null:` 以后，分支里 `taxi` 一定有值。
- Java 返回的对象一律当作可能为 `null`，所以 `LocalDate.parse` 的结果也要先判断。

如果不判断，直接用：

<<< @/snippets/tutorial/ledger/05_lookup_error.spr

```text
SPR-TYPE-NULLABLE [TYPE] main.spr:12:12: Cannot access 'cents' on a value that may be null (receiver type Expense?)
  hint: Bind the receiver to a let and check it with 'if value != null:' before using 'cents'.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

## 16.7 会失败的操作：把错误写进类型

用户输入的金额可能是 `12.50`，也可能是 `abc`。解析失败时，与其返回一个看不出问题的默认值，不如直接报错：

<<< @/snippets/tutorial/ledger/06_parse.spr

```text
1250
800
skipped: not an amount: twelve
```

- 签名里的 `throws Error` 告诉调用的人：这个函数可能失败。
- 调用方有两个选择：`try` / `catch` 处理，或者在自己的签名里也写上 `throws Error`。
- `if units == null or ...: throw ...` 之后，`units` 就被当作有值了。

::: details 练习：在一个没写 `throws` 的函数里调用 `parse_amount` 会怎样？
下面的 `show` 调用了 `parse_amount`，却既没有 `try`，也没有写 `throws`：

<<< @/snippets/tutorial/ledger/06_parse_unhandled.spr

编译器会要求你二选一：

```text
SPR-FLOW-THROWS [FLOW] main.spr:14:11: Call may throw Error; declare 'throws Error' or handle it with try/catch
```
:::

## 16.8 合起来：读文件，出报表

现在把前面的东西拼成一个能用的程序。真实的工具会去读你的消费记录文件；为了让示例直接就能运行，程序先把几行示例数据写进一个临时文件，再把它读回来。

开头导入要用到的模块：`@std/files` 读写文件，`@std/text` 处理文本。后者起名叫 `strings`，免得和 `parse_amount` 的参数 `text` 重名。

<<< @/snippets/tutorial/ledger/07_report.spr#imports

然后是数据：类别和支出。

<<< @/snippets/tutorial/ledger/07_report.spr#model

16.3 的 `format_money` 和 16.7 的 `parse_amount` 原样搬过来，这里不再重复。再加两个小函数，负责把文字转成类别，再把类别转回文字：

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
not an amount: 45.5
```

想读你自己的记录，就把 `temp_file()` 和 `write_utf8` 那两行去掉，把 `path` 换成你的文件路径。
:::

## 16.9 接下来可以做的

- 给它加一个 `tests/` 目录，为 `parse_amount` 写几个测试，再写一个 `compile_fail` 测试保证 `cents / 100` 这种写法被拒绝（第 12 章）。
- 用 `@std/json` 把支出存成 JSON 文件，仓库里的[任务清单示例](https://github.com/ColinHouse/Sprig/tree/main/examples/task-tracker)就是这么做的。
- 用 `@std/concurrent`（第 14 章）并行解析多个月的文件。
- 用 `sprig-web`（见 [Web 与 SQLite](/guide/web-sqlite)）把报表做成一个本地网页。

## 全书小结

你已经见过 Sprig 的全部核心：缩进成块的语法，写在签名上的类型，`let` 优先的绑定，没有隐式转换的数值，只读与可改分开的集合，没有继承的类加上按内容比较的 variant，必须穷尽的 `match`，写进类型的 `null` 和错误，克制的泛型，只捕获 `let` 的 lambda，以及一个把一切都做成命令行子命令、都能输出 JSON 的工具链。

- 想逐条查语法：[语言速查](/guide/language-tour)。
- 想看更多完整程序：[示例](/examples)。
- 想做 Minecraft 模组：[Fabric 模组](/guide/fabric)。
- 遇到问题，或者觉得哪里设计得别扭：欢迎[开一个 issue](https://github.com/ColinHouse/Sprig/issues)。
