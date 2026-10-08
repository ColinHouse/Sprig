# 14. 错误处理

这一章你会学到：

- 用 `throws` 把“可能失败”写进函数签名；
- 错误怎样沿着调用链一层层向上传，直到有人 `catch`；
- `throw`、`try`/`catch`、`finally` 的写法与执行顺序；
- 自定义错误类，以及多个 `catch` 的顺序；
- `throw problem` 重新抛出，给错误加上下文；
- `@std/nulls` 的 `require` 把“本该有值却是 `null`”变成错误；
- 哪些失败不属于 `Error`，为什么它们应该被修掉而不是接住。

## 14.1 失败写进类型：throws

有些操作会失败：数字解析不了、文件不存在、网络断了。Sprig 不用隐藏的异常，它把失败放在**签名**里：`-> Int throws Error` 读作“返回 `Int`，或者以 `Error` 失败”。

<<< @/snippets/book/ch09_errors.spr

```text
42
failed: not a number: abc
done
```

错误沿着调用链向上传，像这样：

```text
顶层 try:                       ← 在这里被 catch 接住
  total(["12", "abc"])          ← 没有处理，自己的签名也写 throws Error
    parse_amount("abc")         ← throw Error("not a number: abc")
```

逐行看：

- `func parse_amount(text: String) -> Int throws Error:` 签名说明它可能抛出 `Error`。
- `text.toIntOrNull()` 返回 `Int?`；`if value == null: throw Error("not a number: " + text)` 抛出一个带消息的错误。`throw` 和 `return` 一样立刻结束当前路径，**它也是第 13 章讲的收窄**：`throw` 之后 `value` 就被当作有值，可以直接 `return value`。
- `total` 调用 `parse_amount`，自己没处理这个错误，所以它的签名也写 `throws Error`。错误就这样显式地一层层向上传，中间任何一层都能选择处理或继续声明。
- `try:` 包住可能失败的调用；`catch problem: Error:` 接住它，`problem.message` 是错误消息；`finally:` 无论成功、失败还是提前返回都执行，可以省略。

跟踪三段输出：

| 发生的事 | 输出 |
|---|---|
| `total(["12", "30"])` 正常返回 42 | `42` |
| `total(["12", "abc"])` 在 `parse_amount` 里抛出错误，向上传到顶层 | （还没有输出） |
| `catch problem: Error:` 接住，打印消息 | `failed: not a number: abc` |
| `finally:` 执行 | `done` |

**顶层语句是特殊的**：顶层可以直接调用会抛出的函数，不用声明。没接住的错误会让程序以 `SPR-RUNTIME-ERROR` 结束，14.6 会看到。

`finally` 里写 `return` 是允许的（它会覆盖 `try` 里的返回值），但很容易让人困惑，不建议这样写。

## 14.2 故意写错：既不处理也不声明

<<< @/snippets/book/ch09_unhandled.spr

```text
SPR-FLOW-THROWS [FLOW] main.spr:8:11: Call may throw Error; declare 'throws Error' or handle it with try/catch
  hint: Declare it on 'show' by changing its header to 'func show(text: String) -> Unit throws Error:', and its callers then handle or declare it too (top-level statements need neither), or handle it where it happens: 'try:' around the call, then 'catch problem: Error:' with what to do instead. Sprig keeps recoverable errors explicit; there is no implicit propagation.
```

读法：

- 位置是 `show` 里调用 `parse_amount(text)` 的地方；
- 消息给出两个选择：给 `show` 的签名加 `throws Error`，或者在这里 `try`/`catch`；
- 提示把两种改法的完整代码都写出来了，还提醒你：声明了之后，`show` 的调用者也要处理或声明（顶层语句除外）；
- 最后一句是设计意图：**没有隐式传播**。读一个函数的签名，就知道它会不会失败；读一段调用代码，就知道失败在哪里被处理。

## 14.3 自定义错误类

只有一种 `Error` 时，调用者只能靠消息文字区分失败原因。需要区分时，把错误定义成类：

<<< @/snippets/book/ch09_error_classes.spr

```text
took 2
unknown sku ZZ
problem: only 5 left
```

- 一个错误类就是一个普通的 `class`，有一个**消息字段**（这里是 `let message: String`），还可以带别的字段（`sku`、`missing`）。
- `conform NotFound to Error(message)` 声明它是一种 `Error`，括号里指出哪个字段是消息。`conform` 的细节第 16 章讲，这里先照写。
- `throws NotFound, OutOfStock` 列出可能抛出的每一种；`throw NotFound(message=..., sku=...)` 和构造普通类一样写字段名。
- `catch` 可以写多个，**从上到下第一个匹配的生效**。所以具体的 `NotFound` 写在通用的 `Error` 前面；反过来，`catch problem: Error:` 会先接住一切，后面的 `catch problem: NotFound:` 永远轮不到，编译器会报 `SPR-FLOW-THROWS`，消息是 `Catch of NotFound is unreachable: Error already catches it`。
- `catch problem: NotFound:` 里 `problem` 是 `NotFound`，能读 `problem.sku`；`"problem: " + problem` 把错误拼进字符串时，用的是它的消息。

在底层函数里用具体错误类型，在边界上用 `Error` 一次接住其余所有，是常见的分工。

## 14.4 消息字段可以叫别的名字

消息字段不需要叫 `message`。`conform` 的括号里写哪个字段，哪个字段就是消息：

<<< @/snippets/book/ch14_error_field.spr

```text
found A1
no item ZZ / ZZ
joined: no item ZZ
```

- `conform NotFound to Error(text)` 让 `text` 成为消息字段，于是 `problem.text` 能读，`"joined: " + problem` 拼出来的也是 `text` 的值。
- 消息字段必须是 `String`：

::: details 故意写错：消息字段不是 String
<<< @/snippets/book/ch14_bad_message.spr

```text
SPR-CONFORM-TARGET [TYPE] main.spr:3:1: sprig.runtime.SprigError has no public or protected constructor taking (long) (expected (java.lang.String) | (java.lang.String, java.lang.Throwable), actual (long))
  hint: Name fields whose JVM shapes match one constructor exactly, in its parameter order.
```

`code` 是 `Int`，编译器找不到接受它的错误构造，报 `SPR-CONFORM-TARGET`。读这条报错不需要理解每个词：位置指向 `conform` 那一行，消息说没有匹配的构造，提示让你用类型对得上的字段。把字段改成 `String` 就通过了。
:::

## 14.5 重新抛出：给错误加上下文

底层函数抛出的消息有时对调用者不够用。`catch` 里可以再 `throw` 一个新的错误，把原来的消息包进去：

<<< @/snippets/book/ch14_rethrow.spr

```text
12
failed: cannot load amount: not a number: abc
done
```

- `load` 调用 `parse`，在 `catch problem: Error:` 里用 `problem.message` 组成新消息 `"cannot load amount: " + ...`，再 `throw Error(...)`。
- 原来的错误被替换成了带上下文的新错误；`load` 的签名依然是 `throws Error`。
- 第一次调用返回 12；第二次在顶层被接住，打印新消息；`finally` 最后输出 `done`。

`throw` 的参数不一定是新构造的：`throw problem` 直接把接住的错误原样继续抛出去，也合法（签名不变）。包装一层是为了让消息更接近调用者关心的事。

## 14.6 null 与 Error 之间的桥：require

`@std/nulls` 的 `require(value, message)`：有值就返回它，为 `null` 就抛出带这条消息的 `Error`。它把“本应有值却是 `null`”的意外变成明确的失败：

<<< @/snippets/book/ch08_nulls.spr

```text
12
0
8080
port must be a number: eighty
```

- `nulls.or_else(stock["tea"], 0)` 和上一章一样：有值 12 用 12，`"milk"` 没有就用 0。
- `port` 的签名是 `-> Int throws Error`，因为它调用了 `require`，`require` 可能抛错。
- `port("8080")` 返回 8080；`port("eighty")` 里 `toIntOrNull()` 是 `null`，`require` 抛出 `Error("port must be a number: eighty")`。
- 顶层 `catch problem: Error:` 接住它，打印 `problem.message`，所以最后一行是 `port must be a number: eighty`。没有 `try`/`catch` 的话，程序会以 `SPR-RUNTIME-ERROR` 结束并打印同样的消息。

选 `T?` 还是 `throw`？一条简单的分界：**结果不存在是正常情况**（查找、映射取值、可选配置）返回 `T?`；**调用者给了不该给的输入，或者外部世界出了问题**（解析失败、文件不存在）就 `throw`。

## 14.7 不是所有失败都是 Error

整数溢出、列表越界这些是程序的 bug，运行时报的是 `SPR-RUNTIME-EXCEPTION`（注意开头是 `[RUNTIME]`，不是 `[TYPE]`）。它们**不属于** `Error`，也不能在签名里用 `throws Error` 表示，而且 `catch problem: Error:` 接不住它们：

```sprig
try:
    let big = 9223372036854775807 + 1
    print(big)
catch problem: Error:
    print("caught")
```

```sh
$ sprig run main.spr
```

```text
SPR-RUNTIME-EXCEPTION [RUNTIME] main.spr:2:5: Numeric error: Int addition overflow
  hint: The exact result does not fit in the integer type. Check before the operation, for example 'if value > 0 and total > 9223372036854775807 - value:' for an Int addition, or catch it: import java.lang.ArithmeticException as ArithmeticException, then put the operation in 'try:' with 'catch problem: ArithmeticException:'. See `sprig help numerics`. Run with --stacktrace to see the JVM stack.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

`catch problem: Error:` 没有打印 `caught`，程序直接以这条诊断结束。列表越界也一样：

```sprig
let items: List[Int] = [1, 2, 3]
print(items[5])
```

```text
SPR-RUNTIME-EXCEPTION [RUNTIME] main.spr:2:1: List index 5 is out of bounds; size is 3
  hint: Check the list length before indexing. Run with --stacktrace to see the JVM stack.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

对这类失败，正确的做法是**修代码**：加法前先判断不会越界，取列表元素前先检查长度。提示里给出的两条路分别是“先检查”和“导入 Java 异常来捕获”，后者属于第 21 章。

没接住的 `Error` 走的是另一条路，诊断是 `SPR-RUNTIME-ERROR`：

```sprig
func parse(text: String) -> Int throws Error:
    throw Error("boom: " + text)

print(parse("x"))
```

```text
SPR-RUNTIME-ERROR [RUNTIME] main.spr:2:5: Uncaught Error: boom: x
  hint: Catch it with try/catch or declare throws in the calling function. Run with --stacktrace to see the JVM stack.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

两条诊断都提示你：加上 `--stacktrace` 会多打印一段 JVM 调用栈，底部能看到从哪个 Sprig 函数开始。语法是 `sprig run --stacktrace 文件.spr`。

::: tip 学过其他语言？
- 没有隐藏的抛出路径：函数签名写着 `throws Error`，调用者就必须处理或声明，编译器不会让你忘。
- 这与 Java 的“受检异常”形似，但 Sprig 只有一种 `Error` 家族，没有 `finally` 之外的资源清理语法（没有 `try-with-resources`、没有 `using`）。
- Java 的运行时异常（如 `ArithmeticException`）在 Sprig 里不是 `Error`。你可以在导入 Java 类之后 `throws ArithmeticException`、`catch problem: ArithmeticException:`，但不会有人强制你这么做，初学者也不必这么做。
:::

## 本章小结

- `throws` 写在签名上，调用者要么 `try`/`catch`，要么自己的签名也声明；顶层语句可以直接调用，没有隐式传播。
- `throw` 结束当前路径，也触发收窄；`finally` 无论成败都执行。
- 错误类是有 `message`（或别的 `String` 字段）+ `conform X to Error(字段)` 的 `class`；多个 `catch` 从上到下，具体的写前面。
- `catch` 里可以 `throw` 新错误或 `throw problem` 重新抛出；`nulls.require` 把 `null` 变成错误。
- 运行时异常和 `SPR-RUNTIME-ERROR` 不是 `Error`：修代码，而不是接住；`--stacktrace` 看调用栈。

## 动手练习

**练习 1（parse 与 catch）。** 写 `parse(text: String) -> Int throws Error`，解析失败时 `throw Error("bad number: " + text)`。调用 `parse("7")` 和 `parse("seven")`，用 `try`/`catch` 打印消息。

::: details 参考答案
<<< @/snippets/book/ch14_ex1.spr

```text
7
error: bad number: seven
```
:::

**练习 2（除零是错误，不是异常）。** 写 `divide(a: Int, b: Int) -> Int throws Error`：`b` 为 0 时 `throw Error("division by zero")`，否则返回 `a.divTrunc(b)`。验证 6/2 得 3，6/0 被接住。

::: details 参考答案
<<< @/snippets/book/ch14_ex2.spr

```text
3
cannot divide: division by zero
```
:::

**练习 3（错误类）。** 定义 `Empty`：`message` 和 `name` 两个 `String` 字段，`conform Empty to Error(message)`。`greeting(name)` 遇到空名字就 `throw Empty(...)`；调用方 `catch problem: Empty:` 打印消息和 `problem.name`。

::: details 参考答案
<<< @/snippets/book/ch14_ex3.spr

```text
hello Ada
name is empty []
```
:::

**练习 4（重新抛出）。** `read_count` 的失败消息是 `not a number: x`。写一层 `load`，用 `throw Error("config: " + problem.message)` 把它包成更适合调用者的消息，再在顶层接住打印。

::: details 参考答案
<<< @/snippets/book/ch14_ex4.spr

```text
3
config: not a number: x
```
:::

**练习 5（finally 的顺序）。** 写 `report()`：`try` 里先打印 `working` 再 `throw Error("boom")`；`catch problem: Error:` 打印 `caught: boom`；`finally:` 打印 `cleanup`。先猜三行的顺序，再运行。

::: details 参考答案
<<< @/snippets/book/ch14_ex5.spr

```text
working
caught: boom
cleanup
```
:::

下一章：[函数作为值](/tutorial/ch15-functions-as-values)。
