# 24. 综合项目：记账

最后一章不教新语法，而是把前 23 章学过的东西拼成一个完整的小程序：读一份消费记录，按类别算出每类花了多少钱，再打印总额。你会看到每个部分为什么写成那样，也会看到两个真实的编译错误和一个运行时的错误消息。

这一章你会学到：

- 怎么把一个小需求拆成 `variant`、`class` 和一组函数；
- 金额为什么存成整数「分」，而不是小数；
- 用 `try` / `finally` 保证程序只删自己创建的临时文件；
- 读文件、逐行解析、按类别汇总的完整流程；
- 两个故意写错的报错分别怎么读。

## 24.1 要做什么

输入是一份文本，一行一笔消费，逗号分开三项：品名、金额、类别。

```text
Coffee, 12.50, food
Taxi, 36.00, transport
Noodles, 18.00, food
Movie, 45.00, fun
```

程序要打印每个类别的合计和总额。类别只有 `food`、`transport` 和「其他」三种；`fun` 不是 `food` 也不是 `transport`，所以算「其他」。

## 24.2 完整程序

程序在下面。它先创建并写入一个临时文件（这样你不用准备数据就能运行），读完以后把它删掉。

<<< @/snippets/book/ch24_ledger.spr

```text
Food: 30.50
Transport: 36.00
Other: 45.00
Total: 111.50
```

先看输出：

- `Food: 30.50`：`Coffee` 的 12.50 加 `Noodles` 的 18.00，等于 30.50 元。
- `Transport: 36.00`：只有 `Taxi` 一笔。
- `Other: 45.00`：`fun` 不是已知类别，落到 `Other`。
- `Total: 111.50`：四笔加起来。

下面的小节按程序里的顺序解释。

## 24.3 类别：variant 和 match

类别是「固定几种、每种不带数据（`Other` 只是名字）」。这正是第 12 章的 `variant`：

```sprig
variant Category:
    Food
    Transport
    Other
```

`category_of` 把文本名转成 `Category`，不认识的返回 `Category.Other`。`label` 反过来，把 `Category` 变成报表里的名字；它用 `match`，三个分支一个不少——`match` 没有兜底分支，编译器要求每一种都写出来（第 12 章）。

## 24.4 一笔支出：class

```sprig
class Expense:
    let item: String
    let cents: Int
    let category: Category
```

三个字段都是 `let`：一笔记录创建后不该被改动（第 11 章）。创建时必须写字段名：`Expense(item=item, cents=cents, category=category)`。

`cents` 是 `Int`，不是 `Float`。第 3 章已经见过原因：`0.1 + 0.2` 打印出来是 `0.30000000000000004`，金额用小数会越算越歪。所以 12.50 元存成 `1250` 分，打印时再换算。

## 24.5 金额的两个函数

`parse_amount("12.50")` 把文本变成 `1250` 分：

```sprig
let parts = text.split(".")
```

`split(".")` 按小数点拆成 `["12", "50"]`，`parts.get(0)` 是 `"12"`，`toIntOrNull()` 把它变成 `12`，转不了则得到 `null`（第 4、13 章）。整数部分必须存在、最多一个小数点；小数部分必须正好两位，否则抛错：

```sprig
if cents == null or parts.get(1).length() != 2:
    throw Error("not an amount: " + text)
```

`throws Error` 写在签名里，调用者必须接住或继续声明（第 14 章）。

`format_money` 反着来：

```sprig
let units = cents.divTrunc(100)
let rest = cents % 100
```

`divTrunc` 是「去掉余数的整数除法」（第 17 章），1250 除以 100 得 12；`%` 取余数得 50。`rest < 10` 时要补一个零：50 分是 `".50"`，5 分是 `".05"`。

## 24.6 一行文本变成一笔支出

`parse_line` 把 `"Coffee, 12.50, food"` 拆开：

- `line.split(",")` 得到三个字段；不是三个就抛错。
- `strings.trim(...)` 去掉每个字段两边的空格（`@std/text`，第 20 章）。
- `parse_amount` 解析金额；`category_of` 解析类别。
- 最后创建一个 `Expense` 返回。

`load_file` 把文件读成行：

```sprig
for line in strings.lines(files.read_utf8(path)):
    if strings.trim(line) != "":
        expenses.append(parse_line(line))
```

`strings.lines` 按行拆开；最后一行如果以换行结尾，会多出一个空串，所以用 `trim` 过滤掉空行。每解析一行，往 `MutableList[Expense]` 里 `append`（第 8 章）。

## 24.7 汇总：map 的计数套路

`report` 用一个 `MutableMap[String, Int]` 记录每个类别的合计（第 9 章）：

```sprig
let previous = totals[name]
if previous == null:
    totals[name] = expense.cents
else:
    totals[name] = previous + expense.cents
sum += expense.cents
```

从 map 里取一个还没记录的类别得到 `null`，所以先取出来判断，再决定是新建还是累加。这里不能写 `totals[name] += expense.cents`：键不存在时那是运行时错误。

打印时按固定顺序 `["Food", "Transport", "Other"]` 找合计，没有记录的类别不会打印。

## 24.8 main：读文件、清理、只删自己创建的

`main` 分成四步，前三步和清理是一对：

```sprig
let path = files.temp_file()
files.write_utf8(path, "Coffee, 12.50, food\n...")
try:
    report(load_file(path))
finally:
    files.remove_file(path)
```

- `files.temp_file()` 在系统临时目录创建一个新文件，返回它的路径（`@std/files`，第 20 章）。
- `files.write_utf8(path, ...)` 把示例数据写进去。`\n` 是换行符（第 4 章）。
- `files.read_utf8(path)` 再读回来，`load_file` 解析。
- `finally` 块里的 `files.remove_file(path)` **删的就是第 1 行创建的那个临时文件**；`finally` 保证解析失败时也会清理（第 14 章）。

程序最后用 `try` / `catch` 接住 `main` 抛出的 `Error`，打印消息（第 14 章）。

::: details 想读你自己的记录文件，改 `main` 就好
不要只把 `path` 换成你的文件——上面的程序会先把示例数据写进去，覆盖你的记录，最后还会把你的文件删掉。正确的做法是让 `main` 只读不写，也不删：

```sprig
func main() -> Unit throws Error:
    report(load_file("my-expenses.txt"))
```

也就是把 `temp_file()`、`write_utf8` 和 `remove_file` 三行全部去掉。自己创建的文件，才由自己删除。
:::

## 24.9 故意写错：整数不能直接除

把 `format_money` 里的 `cents.divTrunc(100)` 换成 `cents / 100`：

<<< @/snippets/book/ch24_divide.spr

```text
SPR-NUM-DIVISION [TYPE] main.spr:2:7: Integer / would truncate; use a.divTrunc(b), or convert both operands explicitly (expected explicit division, actual Int / Int)
  hint: Write cents.divTrunc(100) to drop the remainder on purpose, or cents.toFloatExact() / 100.toFloatExact() for a Float result. text.fixed(value, decimals) from @std/text.spr prints a Float with that many decimals.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

报错在第 2 行第 7 列，指着 `/`。信息把两个选择写清楚了：想丢掉余数就写 `divTrunc`，想让结果是小数就把两边都转成 `Float`。Sprig 不替你猜 7 / 2 应该是 3 还是 3.5。

## 24.10 故意写错：漏掉一个 match 分支

`label` 里如果只写 `Food` 和 `Transport` 两个分支：

<<< @/snippets/book/ch24_nonexhaustive.spr

```text
SPR-MATCH-NONEXHAUSTIVE [FLOW] main.spr:7:12: Missing case: Category.Other
  hint: Add 'case Category.Other:' (there is no default case)
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

编译器在第 7 行第 12 列（`match` 的位置）告诉你缺哪一种，hint 直接给出要补的那一行。以后给 `Category` 加新类别的时候，所有忘了处理它的 `match` 都会像这样被找出来。

## 24.11 运行时也会失败：金额格式不对

静态检查能挡住类型错误，挡不住「文本长得不对」。把示例数据里 `Movie` 那一行的金额写成 `45.5`，程序会在解析这一行时抛错，被最外层的 `catch` 接住：

<<< @/snippets/book/ch24_bad_parse.spr

```text
not an amount: 45.5
```

`parse_amount` 坚持小数点后正好两位，`45.5` 只有一位，于是 `throw Error("not an amount: " + text)`。错误消息把出问题的原文带上了，这比返回一个看不出问题的默认值有用得多。

## 本章小结

- `variant` 表达固定几种类别，`match` 强制每一种都处理。
- 一笔记录用 `class`，字段用 `let`；金额存成整数「分」。
- `divTrunc` 和 `%` 负责分的换算；`throws Error` 把失败写进签名。
- map 求和的套路是：取值、判 `null`、再累加。
- 程序只删自己创建的文件；用 `try` / `finally` 保证一定清理。想读自己的文件时，整个 `main` 换成只读版本。
- 静态报错先看错误码、位置和 hint；运行时的错误消息由你自己设计得有用。

## 动手练习

**练习 1（简单）。** `format_money` 对 `5` 分和 `0` 分分别输出什么？写个小程序验证。提示：记住 `rest < 10` 时要补零。

::: details 参考答案
<<< @/snippets/book/ch24_ex_money.spr

```text
0.05
0.00
```

5 分是 `0.05` 元，0 分是 `0.00` 元。`divTrunc(100)` 得 0，`%` 得 5，补零成 `".05"`。
:::

**练习 2（简单）。** `category_of("fun")` 和 `category_of("transport")` 经过 `label` 之后分别打印什么？

::: details 参考答案
<<< @/snippets/book/ch24_ex_label.spr

```text
Other
Transport
```

`category_of` 对不认识的 `"fun"` 返回 `Category.Other`。
:::

**练习 3（中等）。** 在 `report` 里加一行，让报表在总额前多打印一条 `Records: 4`（记录条数）。提示：`expenses.size()` 是 `Int`，用 `+` 拼接时另一边要是 `String`。

::: details 参考答案
<<< @/snippets/book/ch24_ex_count.spr

```text
Food: 30.50
Transport: 36.00
Other: 45.00
Records: 4
Total: 111.50
```

新增的一行是 `print("Records: " + expenses.size())`，放在类别合计之后、总额之前。
:::

**练习 4（稍难）。** 把程序改成读你自己的 `my-expenses.txt`，写出新的 `main`，并说明为什么不能只改 `path`。

::: details 参考答案
```sprig
func main() -> Unit throws Error:
    report(load_file("my-expenses.txt"))
```

不能只改 `path`：原来的 `main` 会先执行 `write_utf8`，用示例数据覆盖你的文件，最后 `finally` 里的 `remove_file` 还会把它删掉。读取自己的数据时，`temp_file()`、`write_utf8` 和 `remove_file` 三行都不要；只有程序自己创建的文件才由程序负责删除。
:::

上一章：[工具链与 AI 助手](/tutorial/ch23-tooling)。

想继续深入 Java 边界：[附录 F. Java 互操作进阶](/tutorial/appendix-f-advanced-java)。

::: tip 学过其他语言？
如果你习惯用 `BigDecimal` 或 Java 的 `double` 记账：整数「分」是很多支付系统的真实做法，没有浮点舍入问题，进位规则也完全由你掌握。`try` / `finally` 和 Java 一样，只是 Sprig 的 `try` 可以没有 `catch` 而有 `finally`，适合这种「不管成败都要清理」的场景。
:::
