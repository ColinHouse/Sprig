# 9. 错误处理

可能失败的操作，在 Sprig 里是签名的一部分。这一章讲 `throws`、`try`/`catch`，以及自定义错误类型。

## 9.1 throws、throw、try

<<< @/snippets/book/ch09_errors.spr

```text
42
failed: not a number: abc
done
```

- `-> Int throws Error` 告诉调用者：这个函数可能以 `Error` 失败。
- `throw Error("...")` 抛出一个带消息的错误；之后的代码不执行。
- `total` 调用了 `parse_amount`，它没有处理这个错误，所以自己的签名也要写 `throws Error`。错误就这样一层层显式地传上去。
- `try:` 包住可能失败的调用，`catch problem: Error:` 接住它，`problem.message` 是消息。`finally:` 无论成败都执行，可以省略。
- 顶层语句可以直接调用会抛出的函数，不用声明；没接住的错误会让程序以 `SPR-RUNTIME-ERROR` 结束，并指出抛出的位置。

注意 `if value == null: throw ...` 之后，`value` 就被当作有值了。`throw` 和 `return` 一样结束当前路径，所以它也是上一章说的"提前返回"收窄。

### 故意写错：既不处理也不声明

<<< @/snippets/book/ch09_unhandled.spr

```text
SPR-FLOW-THROWS [FLOW] main.spr:8:11: Call may throw Error; declare 'throws Error' or handle it with try/catch
  hint: Declare it on 'show' by changing its header to 'func show(text: String) -> Unit throws Error:', and its callers then handle or declare it too (top-level statements need neither), or handle it where it happens: 'try:' around the call, then 'catch problem: Error:' with what to do instead. Sprig keeps recoverable errors explicit; there is no implicit propagation.
```

最后一句是这个设计的全部：**没有隐式传播**。读一个函数的签名，就知道它会不会失败；读一段调用代码，就知道失败会在哪里被处理。

## 9.2 什么该是错误，什么该是 null

两者都表示"事情没按预期发生"，怎么选？

- **结果不存在是正常情况**（查找、映射取值、可选配置）：返回 `T?`。
- **调用者给了不该给的输入，或者外部世界出了问题**（解析失败、文件不存在、网络断了）：`throw`。
- 别用错误码整数或 `-1`，也别返回一个看不出问题的默认值。

`@std/nulls.spr` 里的 `require` 是两者之间的桥：把"本应有值却为 `null`"变成一个带说明的 `Error`。

## 9.3 自定义错误类型

只有一种 `Error` 时，调用者只能靠消息文字区分失败原因。需要区分时，把错误定义成类：

<<< @/snippets/book/ch09_error_classes.spr

```text
took 2
unknown sku ZZ
problem: only 5 left
```

- 一个错误类就是一个普通的 `class`，必须有 `let message: String` 字段，还可以带别的字段（`sku`、`missing`）。
- `conform NotFound to Error(message)` 声明它是一种 `Error`，括号里指出哪个字段是消息。
- `throws NotFound, OutOfStock` 列出可能抛出的每一种；`throw NotFound(message=..., sku=...)` 和构造普通类一样写字段名。
- `catch` 可以写多个，从上到下第一个匹配的生效。`catch problem: NotFound:` 里 `problem` 是 `NotFound`，能读 `problem.sku`；`catch problem: Error:` 接住剩下的所有错误。所以具体的要写在前面。
- `"problem: " + problem` 把错误拼进字符串时，用的是它的消息。

一个函数声明 `throws Error` 就覆盖了所有错误类，调用者用 `catch problem: Error:` 一次接住。在底层函数里用具体类型，在边界上用 `Error`，是常见的分工。

## 9.4 不是所有失败都是 Error

整数溢出、列表越界、`toIntExact()` 遇到小数，这些在运行时报的是 `SPR-RUNTIME-EXCEPTION`，不属于 `Error`，也不需要（不能）在签名里声明。它们是程序的 bug，应当修代码，而不是 `catch`。确实要接住的话，可以导入对应的 Java 异常类（比如 `java.lang.ArithmeticException`）来 `catch`，报错的提示里会写出具体做法。

## 小结

- `throws` 写在签名上，调用者要么 `try`/`catch`，要么也 `throws`。没有隐式传播。
- `throw` 结束当前路径，也触发收窄。
- 错误类：有 `message` 字段的 `class`，加 `conform X to Error(message)`；多个 `catch` 时具体的放前面。
- "没有"用 `T?`，"失败"用 `throw`。

下一章：[泛型](/tutorial/ch10-generics)。
