# 2. 值与类型

这一章讲最基本的东西：怎么给值起名字，Sprig 有哪些基本类型，以及它在数字和字符串上做了哪些和别的语言不一样的决定。

## 2.1 let 和 var

<<< @/snippets/book/ch02_bindings.spr

```text
Ada
2
true
```

- `let` 把一个名字绑定到一个值，之后不能再赋值。`var` 可以。
- 类型通常不用写，Sprig 从右边的值推断：`name` 是 `String`，`visits` 是 `Int`，`tall` 是 `Bool`。想写也可以，像 `height: Float` 那样放在名字后面。
- `+=`、`-=` 这类复合赋值只能用在 `var` 上。

默认用 `let`，确实需要改再换成 `var`。这不只是风格：第 8 章会看到，编译器对 `let` 绑定能做更多推理（比如判过 `!= null` 之后就当它有值），对 `var` 则不能。

### 故意写错：给 let 赋值

<<< @/snippets/book/ch02_let_assign.spr

```text
SPR-NAME-LET-ASSIGN [TYPE] main.spr:2:1: Cannot assign to immutable binding 'name'; declare it with var
```

报错已经把修法说了：这个名字如果真要改，就声明成 `var`。

## 2.2 数字

Sprig 有两个常用的数值类型：`Int` 是 64 位有符号整数，`Float` 是 IEEE 双精度浮点数。另外还有四个不常用的：`Int32` 和 `Float32`（32 位，主要用于和 Java 打交道）、`Decimal`（十进制的任意精度数）和 `BigInt`（任意大的整数），本节后半讲。

<<< @/snippets/book/ch02_numbers.spr

```text
3
1
3.5
10.0
9000000000000
2000000000
```

几条规则：

- **整数相除要说清楚。** `a.divTrunc(b)` 是截断除法，`%` 取余。整数之间不能直接写 `/`，下面会看到为什么。
- **Int 和 Float 不混算。** 要先用 `toFloatExact()` 把整数转过去。
- **整数运算检查溢出。** `9000000000 * 1000` 在 64 位里放得下，所以正常打印；超出范围的运算在运行时报 `SPR-RUNTIME-EXCEPTION`，而不是悄悄绕回负数。
- 浮点数转整数也要说清楚：`toIntExact()` 要求它正好是整数，否则运行时报错；`toIntTrunc()` 截掉小数部分。
- **数字之间的转换方法，名字说明会不会丢信息。** 名字不带后缀的（`Int32` 的 `toInt()`、`toDecimal()` 等）一定成功；可能丢信息的，名字里写明丢了怎么办：`Exact` 报错，`Lossy` 四舍五入，`Trunc` 截掉小数。所以 `Int` 没有 `toFloat()`，`Float` 也没有 `toInt()`。

### 故意写错：整数直接相除

<<< @/snippets/book/ch02_division.spr

```text
SPR-NUM-DIVISION [TYPE] main.spr:3:7: Integer / would truncate; use a.divTrunc(b), or convert both operands explicitly (expected explicit division, actual Int / Int)
  hint: Write a.divTrunc(b) to drop the remainder on purpose, or a.toFloatExact() / b.toFloatExact() for a Float result. text.fixed(value, decimals) from @std/text.spr prints a Float with that many decimals.
```

`7 / 2` 是 3 还是 3.5？Python 2 和 Python 3 的答案不同，Java 和 JavaScript 的答案也不同。Sprig 不替你选：想要 3 就写 `divTrunc`，想要 3.5 就两边都转成 `Float`。

### 故意写错：Int 乘 Float

<<< @/snippets/book/ch02_mixed.spr

```text
SPR-NUM-MIXED [TYPE] main.spr:3:7: Operator '*' has no implicit conversion between Float and Int (expected matching numeric families, actual Float and Int)
  hint: Convert the Int side: count.toFloatExact().
```

注意提示直接写出了要改的那个名字：`count.toFloatExact()`。Sprig 的报错尽量做到"照着改就行"。

::: tip 为什么这么严格
记账程序里 `12.50 * 3` 算出 `37.5` 没问题，但 `0.1 + 0.2` 在任何语言的双精度浮点里都等于 `0.30000000000000004`。Sprig 让每一次 Int 与 Float 之间的转换都看得见，是为了让你在该用整数（比如"分"）的地方自觉地用整数。第 16 章的记账工具就是这么做的。
:::

### 其他四个数值类型

<<< @/snippets/book/ch02_int32_float32.spr

```text
2000000001
4000000000
0.1
0.10000000149011612
1.0
```

- `Int32` 是 32 位整数，`Float32` 是单精度浮点。Java 的 `int` 和 `float` 就是它们，第 13 章会经常见到；自己的代码里一般用不着。
- **窄的可以无损地变宽**：`Int32` 的值可以直接放进 `Int` 的位置，`Float32` 可以放进 `Float`，`Int32` 和 `Int` 混算的结果是 `Int`。反过来都要显式转换。
- `narrow.toFloat()` 打印出 `0.10000000149011612`：`0.1` 在单精度里本来就是这个数，变宽只是把它如实写出来。
- **整数字面量可以直接当浮点数用，前提是能精确表示**：`let whole: Float = 1` 合法。放不下的字面量是编译错误，下面会看到。

<<< @/snippets/book/ch02_conversions.spr

```text
300
300.0
2
-1
true
```

转换方法的名字说明了它的态度：`Exact` 结尾的在不精确时报错，`Trunc` 截断，`Lossy` 允许舍入。

| 从 | 到 | 方法 |
|---|---|---|
| `Int` | `Int32` | `toInt32Exact()`，超出范围报错 |
| `Int` | `Float` | `toFloat()` / `toFloatExact()`，丢精度报错；`toFloatLossy()` 舍入 |
| `Float` | `Int` | `toIntExact()`，不是整数报错；`toIntTrunc()` 截掉小数 |
| `Float` | `Float32` | `toFloat32Exact()` 报错 / `toFloat32Lossy()` 舍入 |
| `Int32` | `Int` | `toInt()`，总是成功 |

没有内置的四舍五入：`import java.lang.Math as Math` 之后 `Math.round(x)` 返回 `Int`。

`a.compareTo(b)` 在每个数值类型和 `String` 上都有，参数必须是接收者自己的类型，返回 `Int32`：负数、零或正数。第 11 章和第 13 章会用它写排序的比较器。

<<< @/snippets/book/ch02_decimal.spr

```text
59.97
0.3333
123456789012345678901234567891
1
```

- `Decimal` 是十进制的任意精度数：`19.99 * 3` 正好是 `59.97`，没有二进制浮点的误差。加减乘是精确的；除法要写 `divide(除数, 小数位数, 舍入模式)`，因为 `1 / 3` 没有精确的十进制表示。算钱用整数的"分"或者 `Decimal`，不用 `Float`。
- `BigInt` 是任意大的整数，加减乘不会溢出。
- 两者都从文本构造（`Decimal.parse`、`BigInt.parse`）或从 `Int` 构造（`fromInt`），都不和 `Int`、`Float` 混算，转换方法的命名和上面一样（`toIntExact`、`toFloatLossy`）。

### 故意写错：浮点字面量放不下

<<< @/snippets/book/ch02_literal_range.spr

```text
SPR-NUM-RANGE [TYPE] main.spr:1:16: Integer literal 9007199254740993 is not exactly representable as Float (expected Float, actual 9007199254740993)
```

2^53 + 1 在双精度里表示不了，Sprig 不会悄悄把它变成 2^53。超出 `Int32` 范围的字面量也是同一个错误码。

### 运行时的溢出

编译器查不出来的溢出在运行时报错，程序停下，而不是绕回负数：

```sprig
let top = 9223372036854775807
print(top + 1)
```

```text
SPR-RUNTIME-EXCEPTION [RUNTIME] main.spr:2:1: Numeric error: Int addition overflow
  hint: The exact result does not fit in the integer type. Check before the operation, for example 'if value > 0 and total > 9223372036854775807 - value:' for an Int addition, or catch it: import java.lang.ArithmeticException as ArithmeticException, then put the operation in 'try:' with 'catch problem: ArithmeticException:'. See `sprig help numerics`. Run with --stacktrace to see the JVM stack.
```

提示给了两条路：算之前先判断，或者捕获 `ArithmeticException`（它是 Java 的异常，第 9 章的 `catch problem: Error` 接不住它）。浮点数不一样：`1.0 / 0.0` 是 `Infinity`，`0.0 / 0.0` 是 `NaN`，和 IEEE 754 一致，不报错。

## 2.3 字符串

<<< @/snippets/book/ch02_strings.spr

```text
tea x 3
length 3
TEA t
[a, b, c]
padded
2
😀
```

- `+` 的任意一侧是 `String` 时就是拼接，另一侧的值会自动变成文字。**没有字符串插值语法**（没有 `f"..."` 或 `${}`），拼接就是唯一的写法。
- 常用方法：`length`、`substring`、`toUpperCase`、`toLowerCase`、`trim`、`split`、`replace`、`contains`、`startsWith`、`indexOf`、`toIntOrNull`。写错方法名时，报错里会把完整列表给你。
- `length()` 和下标按**字符**（Unicode 码点）计，不按字节也不按 UTF-16 单元：`"é😀"` 的长度是 2，`[1]` 取到的是那个表情。

`"12".toIntOrNull()` 这种"可能失败的转换"返回的是 `Int?`，第 8 章讲它。

字符串之间可以用 `==` 比较内容，用 `compareTo` 比较顺序。更多的文本工具在标准库 `@std/text.spr` 里：

<<< @/snippets/book/ch02_text.spr

```text
0.67
007
ab..|
a-b-c
```

`fixed(value, decimals)` 把 `Float` 四舍五入到指定的小数位数，`pad_left`、`pad_right` 用填充字符补到指定宽度，`join` 用分隔符拼接列表。还有 `lines`、`strip_prefix`、`is_ascii_digit` 等，`sprig api @std/text.spr` 列出全部。

## 2.4 布尔值

<<< @/snippets/book/ch02_bool.spr

```text
true
true
```

逻辑运算用英文单词 `and`、`or`、`not`，而不是 `&&`、`||`、`!`。`and` 和 `or` 都是短路的：右边只在需要时求值。

### 故意写错："非零即真"

<<< @/snippets/book/ch02_truthy.spr

```text
SPR-TYPE-CONDITION [TYPE] main.spr:2:4: Condition must be Bool (no truthiness) (expected Bool, actual MutableList[Int])
```

`if`、`while` 的条件只接受 `Bool`。空列表、`0`、`""`、`null` 都不会被当成假，想判断列表是否为空就写 `items.size() > 0`。这条规则和前面的"不混算"是一回事：Sprig 不替你猜。

## 2.5 类型一览

到这里见过的基本类型：

| 类型 | 含义 | 字面量例子 |
|---|---|---|
| `Int` | 64 位整数，检查溢出 | `42`、`-7` |
| `Int32` | 32 位整数，主要用于 Java 互操作 | 需写类型：`let n: Int32 = 1` |
| `Float` | 双精度浮点 | `2.5`、`1.0` |
| `Float32` | 单精度浮点，主要用于 Java 互操作 | 需写类型：`let f: Float32 = 0.5` |
| `Decimal` | 十进制任意精度数 | `Decimal.parse("19.99")` |
| `BigInt` | 任意大的整数 | `BigInt.fromInt(7)`、`BigInt.parse("...")` |
| `Bool` | 布尔 | `true`、`false` |
| `String` | 不可变的 Unicode 文本 | `"tea"` |
| `Unit` | "没有值"，用作不返回东西的函数的返回类型 | 无 |

列表、映射这些集合类型在第 5 章；带问号的可空类型在第 8 章。

## 小结

- `let` 不可改，`var` 可改；默认用 `let`。
- 类型可以推断，签名上的类型要写（下两章会看到）。
- 没有隐式数值转换：整数除法写 `divTrunc`，Int 和 Float 之间用 `toFloatExact()` 等方法明确转换；只有窄变宽（`Int32` 到 `Int`、`Float32` 到 `Float`）是自动的。
- 整数溢出在运行时报错；算钱用整数的分或 `Decimal`。
- 字符串用 `+` 拼接，没有插值；长度按字符算。
- 条件必须是 `Bool`，没有"非零即真"。

下一章：[控制流](/tutorial/ch03-control-flow)。
