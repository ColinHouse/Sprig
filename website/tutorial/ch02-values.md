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

Sprig 有两个常用的数值类型：`Int` 是 64 位有符号整数，`Float` 是 IEEE 双精度浮点数。另外还有 `Int32`（32 位整数，主要用于和 Java 打交道）。

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
- **Int 和 Float 不混算。** 要先用 `toFloat()` 把整数转过去。
- **整数运算检查溢出。** `9000000000 * 1000` 在 64 位里放得下，所以正常打印；超出范围的运算在运行时报 `SPR-RUNTIME-EXCEPTION`，而不是悄悄绕回负数。
- 浮点数转整数也要说清楚：`toIntExact()` 要求它正好是整数，否则运行时报错；`toIntTrunc()` 截掉小数部分。

### 故意写错：整数直接相除

<<< @/snippets/book/ch02_division.spr

```text
SPR-NUM-DIVISION [TYPE] main.spr:3:7: Integer / would truncate; use a.divTrunc(b), or convert both operands explicitly (expected explicit division, actual Int / Int)
  hint: Write a.divTrunc(b) to drop the remainder on purpose, or a.toFloat() / b.toFloat() for a Float result. text.fixed(value, decimals) from @std/text.spr prints a Float with that many decimals.
```

`7 / 2` 是 3 还是 3.5？Python 2 和 Python 3 的答案不同，Java 和 JavaScript 的答案也不同。Sprig 不替你选：想要 3 就写 `divTrunc`，想要 3.5 就两边都转成 `Float`。

### 故意写错：Int 乘 Float

<<< @/snippets/book/ch02_mixed.spr

```text
SPR-NUM-MIXED [TYPE] main.spr:3:7: Operator '*' has no implicit conversion between Float and Int (expected matching numeric families, actual Float and Int)
  hint: Convert the Int side: count.toFloat().
```

注意提示直接写出了要改的那个名字：`count.toFloat()`。Sprig 的报错尽量做到"照着改就行"。

::: tip 为什么这么严格
记账程序里 `12.50 * 3` 算出 `37.5` 没问题，但 `0.1 + 0.2` 在任何语言的双精度浮点里都等于 `0.30000000000000004`。Sprig 让每一次 Int 与 Float 之间的转换都看得见，是为了让你在该用整数（比如"分"）的地方自觉地用整数。第 16 章的记账工具就是这么做的。
:::

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
| `Bool` | 布尔 | `true`、`false` |
| `String` | 不可变的 Unicode 文本 | `"tea"` |
| `Unit` | "没有值"，用作不返回东西的函数的返回类型 | 无 |

列表、映射这些集合类型在第 5 章；带问号的可空类型在第 8 章。

## 小结

- `let` 不可改，`var` 可改；默认用 `let`。
- 类型可以推断，签名上的类型要写（下两章会看到）。
- 没有隐式数值转换：整数除法写 `divTrunc`，Int 和 Float 之间用 `toFloat()` 等方法明确转换。
- 字符串用 `+` 拼接，没有插值；长度按字符算。
- 条件必须是 `Bool`，没有"非零即真"。

下一章：[控制流](/tutorial/ch03-control-flow)。
