# 17. 数字进阶

第 3 章你已经认识了 `Int` 和 `Float`：64 位整数、双精度浮点、整数不能直接 `/`、Int 和 Float 不混算。剩下的四种数字类型都在这一章，另外看清它们之间怎么转换。

这一章你会学到：

- `Int32`、`Float32`、`Decimal`、`BigInt` 分别是什么、什么时候用；
- 窄类型怎么变宽（自动）、宽类型怎么变窄（必须写方法）；
- 转换方法名字里的 `Exact`、`Lossy`、`Trunc` 各是什么意思；
- `Decimal` 的精确算钱和 `divide(scale, mode)` 的舍入模式名；
- `Float` 的 `NaN`、`Infinity` 和 `sqrt` / `floor` / `ceil` / `abs`、`isNaN`、`approxEqual`；
- `@std/math` 里的 `abs`、`min`、`max`、`sign`、`clamp`、`floor_div`、`isqrt`。

## 17.1 六个数字类型

| 类型 | 是什么 | 怎么造 |
|---|---|---|
| `Int` | 64 位有符号整数，运算检查溢出 | 字面量 `42` |
| `Int32` | 32 位有符号整数，和 Java 的 `int` 对齐 | 写类型的字面量：`let n: Int32 = 7` |
| `BigInt` | 任意大的整数 | `BigInt.parse("...")`、`BigInt.fromInt(7)` |
| `Float` | IEEE 754 双精度浮点（binary64） | 字面量 `2.5`、`1.0` |
| `Float32` | IEEE 754 单精度浮点（binary32），和 Java 的 `float` 对齐 | 写类型的字面量：`let f: Float32 = 0.5` |
| `Decimal` | 十进制任意精度数 | `Decimal.parse("19.99")`、`7.toDecimal()` |

平时写程序用 `Int` 和 `Float` 就够了。`Int32` 和 `Float32` 主要是为了和 Java 打交道（第 21 章）；`Decimal` 用来算钱；`BigInt` 用来处理会超出 64 位的整数。它们都遵守第 3 章的总规则：**不同数值家族之间没有隐式转换**。

## 17.2 Int32 和 Float32：和 Java 对齐的窄类型

<<< @/snippets/book/ch02_int32_float32.spr

```text
2000000001
4000000000
0.1
0.10000000149011612
1.0
```

逐行看：

- `let small: Int32 = 2000000000`：默认的整数字面量是 `Int`，要 `Int32` 就在声明上写类型（`0.5` 之于 `Float32` 同理）。字面量必须放得下，放不下在编译期报 `SPR-NUM-RANGE`（本节末尾有例子）。
- `let wide: Int = small`：**窄的可以无损地变宽**。`Int32` 的值放进 `Int` 的位置不需要任何方法；`Float32` 放进 `Float` 也一样。反过来（`Int` → `Int32`、`Float` → `Float32`）必须写转换方法。
- `wide + 1` 是 `Int` 运算，打印 `2000000001`。
- `small + wide`：`Int32` 和 `Int` 混算，结果类型是较宽的 `Int`，打印 `4000000000`。
- `let narrow: Float32 = 0.1`：`0.1` 在二进制里除不尽，32 位只能存最接近它的那个数。打印 `narrow` 时是 `0.1`——这是"能还原回同一个 32 位值的最短十进制写法"。
- `narrow.toFloat()`：把它无损地变成 64 位 `Float`，这时打印出它真实的二进制值 `0.10000000149011612`。

  追踪一下这个值的两次"变身"：

  | 步骤 | 内存里的值 | 打印 |
  |---|---|---|
  | `let narrow: Float32 = 0.1` | 最接近 0.1 的 32 位浮点 | `0.1`（按 32 位的最短写法） |
  | `narrow.toFloat()` | 同一个值，用 64 位表示 | `0.10000000149011612`（64 位的最短写法） |

- `let whole: Float = 1`：整数字面量可以给浮点变量，前提是**能精确表示**。输出 `1.0`。

那么 `Float32` 的算术结果是什么类型？字面量会"跟着另一边走"：

<<< @/snippets/book/ch17_f32_literal.spr

```text
1.1
1.1000000014901161
```

- `a + 1.0`：`a` 是 `Float32`，没有类型的 `1.0` 字面量就当作 `Float32`，运算是 32 位的，打印 `1.1`。
- `b + c`：`c` 是 `Float` 变量，运算整条升级到 64 位；`b` 里的值本来就不是精确的 `0.1`，加出来打印 `1.1000000014901161`。

窄类型混算也有一条红线——`Float32` 和 `Int` 不能直接算：

### 故意写错：`Float32` 加 `Int`

<<< @/snippets/book/ch17_mixed32.spr

```text
SPR-NUM-MIXED [TYPE] main.spr:3:7: Operator '+' has no implicit conversion between Float32 and Int (expected matching numeric families, actual Float32 and Int)
  hint: Convert the Int side: b.toFloatExact().
```

`expected matching numeric families` 说明两个操作数不在同一个家族。按提示把 `b` 转成浮点（`b.toFloatExact()`，见下一节），或者把 `a` 换成 `Int`。

### 故意写错：字面量放不下

<<< @/snippets/book/ch02_literal_range.spr

```text
SPR-NUM-RANGE [TYPE] main.spr:1:16: Integer literal 9007199254740993 is not exactly representable as Float (expected Float, actual 9007199254740993)
```

2^53 + 1 超过了双精度能精确表示的整数范围，Sprig 不会悄悄把它变成 2^53。`Int32` 的字面量超出 32 位范围是同一个错误码。

编译期查不出的溢出在运行时报错。把下面两行存成 `overflow.spr`，运行 `sprig run overflow.spr`：

```sprig
let a: Int32 = 2000000000
print(a + a)
```

```text
SPR-RUNTIME-EXCEPTION [RUNTIME] overflow.spr:2:1: Numeric error: Int32 addition overflow
  hint: The exact result does not fit in the integer type. Check before the operation, for example 'if value > 0 and total > 9223372036854775807 - value:' for an Int addition, or catch it: import java.lang.ArithmeticException as ArithmeticException, then put the operation in 'try:' with 'catch problem: ArithmeticException:'. See `sprig help numerics`. Run with --stacktrace to see the JVM stack.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

`2000000000 + 2000000000` 得 `4000000000`，装不进 `Int32`。像 `Int` 一样，Sprig 报错而不是绕回负数。提示里"捕获 `ArithmeticException`"的写法要用 Java 的异常类型，第 21 章再看。

## 17.3 转换方法的命名法

所有数字之间的转换都遵守一条命名规则：**方法名说明会不会丢信息**。

<<< @/snippets/book/ch02_conversions.spr

```text
300
300.0
2
-1
true
```

- `n.toInt32Exact()`：`300` 在 `Int32` 范围内，成功，打印 `300`。
- `n.toFloatExact()`：打印 `300.0`。如果这个 `Int` 大到 Float 存不下精确值，`Exact` 会在**运行时**报错，而不是悄悄舍入。
- `f.toIntTrunc()`：`2.75` 截掉小数部分得 `2`。`Trunc` 明说了"小数直接扔掉"。
- `a.compareTo(5)`：每个数值类型都有 `compareTo`，参数必须是接收者自己的类型，返回 `Int32`：`3` 比 `5` 小，得 `-1`。
- `"pear".compareTo("apple") > 0`：字符串也有，按字典序，`true`。

规则表：

| 名字 | 含义 | 例子 |
|---|---|---|
| 无后缀 | 一定成功 | `Int32.toInt()`、`Int32.toFloat()`、`Float32.toFloat()`、`value.toDecimal()` |
| `Exact` | 会丢信息就报错 | `Int.toFloatExact()`、`Float.toIntExact()`、`Int.toInt32Exact()` |
| `Lossy` | 允许舍入 | `Int.toFloatLossy()`、`Float.toFloat32Lossy()` |
| `Trunc` | 丢掉小数部分 | `Float.toIntTrunc()` |

常用的转换对照：

| 从 → 到 | 怎么写 |
|---|---|
| `Int32` → `Int`、`Float32` → `Float` | 直接赋值（自动加宽），或 `.toInt()` / `.toFloat()` |
| `Int` → `Int32` | `.toInt32Exact()`，超出范围报错 |
| `Int` → `Float` | `.toFloatExact()`（丢精度报错）或 `.toFloatLossy()`（舍入） |
| `Int` → `Decimal` | `.toDecimal()` |
| `Float` → `Int` | `.toIntExact()`（有小数报错）或 `.toIntTrunc()`（截断） |
| `Float` → `Float32` | `.toFloat32Exact()` 或 `.toFloat32Lossy()` |
| `BigInt` → `Int` / `Float` / `Decimal` | `.toIntExact()` / `.toFloatExact()`、`.toFloatLossy()` / `.toDecimal()` |
| `Decimal` → `Int` / `Float` | `.toIntExact()` / `.toFloatExact()`、`.toFloatLossy()` |

如果名字写错了，编译器会把正确的拼写告诉你：

### 故意写错：`Int` 没有 `toFloat()`

<<< @/snippets/book/ch17_no_tofloat.spr

```text
SPR-NAME-UNRESOLVED [NAME] main.spr:2:7: Type Int has no method 'toFloat'
  hint: An Int beyond 2^53 has no exact Float: write value.toFloatExact(), which fails instead of rounding, or value.toFloatLossy(), which rounds.
```

`Int.toFloat()` 和 `Float.toInt()` 故意不存在：它们没法告诉你"会丢信息"。提示直接把两个候选和区别写了出来——要安全写 `Exact`，要舍入写 `Lossy`。同样，`Decimal` 没有静态的 `fromInt`（写 `value.toDecimal()`），`BigInt` 用 `BigInt.fromInt(value)`。

## 17.4 Decimal：算钱的那位

`0.1 + 0.2` 在 `Float` 里是 `0.30000000000000004`，因为二进制浮点表示不了大多数十进制小数。`Decimal` 是十进制的任意精度数，加减乘都是精确的：

<<< @/snippets/book/ch02_decimal.spr

```text
59.97
0.3333
123456789012345678901234567891
1
```

- `Decimal.parse("19.99")` 从文本构造（`Decimal` 和 `BigInt` 都没有字面量）。`price * 3.toDecimal()` 精确得 `59.97`。
- `1.toDecimal().divide(3.toDecimal(), 4, "HALF_EVEN")`：除法**必须**写清楚保留几位小数和舍入模式，这里得 `0.3333`。
- `BigInt.parse("123456789012345678901234567890")` 随便多长；`+ BigInt.fromInt(1)` 得 `...891`，不会溢出。
- `huge.compareTo(BigInt.fromInt(1))` 返回 `1`，说明 `huge` 更大。

除法第三个参数是**舍入模式名**，必须是 Java `RoundingMode` 里的名字，常用的有 `"HALF_UP"`（四舍五入）、`"HALF_EVEN"`（银行家舍入，.5 进偶数）、`"DOWN"`（向零截断）：

<<< @/snippets/book/ch17_decimal_modes.spr

```text
3
2
true
0
0.3333
59.97
```

- `2.5` 保留 0 位：`HALF_UP` 得 `3`，`HALF_EVEN` 得 `2`（进到偶数 2）。
- `Decimal` 的 `==` 是数值比较，不是文本比较：`0.30 == 0.3` 为 `true`，`compareTo` 返回 `0`。
- `"DOWN"` 截断到 4 位得 `0.3333`。
- 乘法和上面一样精确。

### 故意写错：`Decimal` 直接用 `/`

<<< @/snippets/book/ch17_decimal_div.spr

```text
SPR-NUM-DIVISION [TYPE] main.spr:3:7: Decimal division needs an explicit scale and rounding mode
  hint: Use a.divide(b, scale, "HALF_EVEN") or another rounding mode.
```

`1/3` 没有有限的十进制表示，Sprig 不替你选精度。按提示写 `a.divide(b, scale, mode)`。

如果模式名写错了，编译期查不出来，运行时报错。把下面两行存成 `modes.spr`，运行：

```sprig
let third = 1.toDecimal().divide(3.toDecimal(), 4, "NEAREST")
print(third)
```

```text
SPR-RUNTIME-EXCEPTION [RUNTIME] modes.spr:1:1: Numeric error: Decimal division failed: No enum constant java.math.RoundingMode.NEAREST
  hint: Guard the checked arithmetic or use an explicit conversion; see `sprig help numerics`. Run with --stacktrace to see the JVM stack.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

`NEAREST` 不是合法的名字，报错把完整的 Java 枚举名也念了出来。合法的名字只有 Java `RoundingMode` 列出的那几个（`HALF_EVEN`、`HALF_UP`、`DOWN` 等都是）。

::: tip 学过其他语言？
`Decimal` 就是 Java 的 `BigDecimal`，`BigInt` 是 `BigInteger`。别的语言里这类适配常常藏在库里；Sprig 把它们做成原生类型，但坚持"名字说清代价"：没有 `Float` 和 `Decimal` 的隐式互转，也没有 `Decimal.fromInt`。Java 互操作那一章会看到 `toJava()` / `fromJava()` 怎么在原类型和 Java 包装之间搬运。
:::

## 17.5 BigInt：要多大有多大

<<< @/snippets/book/ch17_bigint.spr

```text
9999999999999999999800000000000000000001
14285714285714285714
1
1.0E20
```

- `big * big`：20 位的数乘自己得 40 位的数，精确，没有溢出。`+`、`-`、`*` 都精确。
- `big.divTrunc(BigInt.fromInt(7))` 和 `big % BigInt.fromInt(7)`：整数除法和取余用 `divTrunc` 和 `%`，除数为零会报错。
- `big.toFloatLossy()`：转 `Float` 允许舍入，打印 `1.0E20`（科学计数法）。

`BigInt` 转回 `Int` 用 `.toIntExact()`；数太大时运行时报 `Numeric error: BigInt outside Int range`（和所有 `Exact` 转换一样，宁可报错也不悄悄截断）。`toDecimal()` 不会失败。

## 17.6 Float 的特殊值和工具方法

浮点有普通数字之外的特殊值，也有几个静态工具方法（写在 `Float` 类型名上）：

<<< @/snippets/book/ch17_float_helpers.spr

```text
1.4142135623730951
-2.0
-1.0
2.5
NaN
0.30000000000000004
Infinity
NaN
true
true
true
true
true
```

逐行看：

- `Float.sqrt(2.0)`：平方根，`1.4142135623730951` 是双精度能给的最接近值。
- `Float.floor(-1.5)` 向下取整得 `-2.0`，`Float.ceil(-1.5)` 向上得 `-1.0`。注意它们返回的还是 `Float`（带 `.0`）；要整数就用 `toIntExact()`（前提是值已经是整数）。
- `Float.abs(-2.5)` 是 `2.5`。
- `Float.sqrt(-1.0)` 是 `NaN`（Not a Number），不报错——IEEE 754 的规则。
- `0.1 + 0.2` 是 `0.30000000000000004`：二进制浮点的经典误差，Sprig 如实打印，不隐藏。
- `1.0 / 0.0` 是 `Infinity`，`0.0 / 0.0` 是 `NaN`；浮点除零不报错（整数除零才报错）。
- `-0.0 == 0.0` 是 `true`：正负零相等，但 `compareTo` 会区分它们。
- `(0.1 + 0.2).approxEqual(0.3, 1e-15)` 是 `true`：`approxEqual(值, 绝对误差)` 做显式的近似比较，比 `==` 温和，但也不是"数学证明"。
- `(0.0 / 0.0).isNaN()`、`(1.0 / 0.0).isInfinite()`、`1.0.isFinite()` 分别判断三类特殊值，都是 `true`。

`NaN` 和任何值比都 `false`，包括它自己；所以永远不要用 `==` 找 `NaN`，用 `isNaN()`。

## 17.7 `@std/math`

整数的小工具在 `@std/math` 里（第 4 章学过怎么 `import`）：

<<< @/snippets/book/ch17_math.spr

```text
5
3
7
-1
10
-4
4
```

- `math.abs(-5)` 得 `5`；`math.min(3, 7)`、`math.max(3, 7)` 得 `3`、`7`。
- `math.sign(-5)` 得 `-1`（负数），零得 `0`，正数得 `1`。
- `math.clamp(12, 0, 10)` 把 `12` 限进 `0..10`，得 `10`（上下界都包含）。
- `math.floor_div(-7, 2)` 得 `-4`：向负无穷取整。这和 `divTrunc`（向零取整，`-3`）不同，负数的整除要选清楚——这也是第 3 章强调"整数除法要说清楚"的原因。
- `math.isqrt(17)` 得 `4`：平方根的整数下取整。

`@std/math` 里会失败的函数用第 14 章的 `throws Error`，可以直接 `try`：

<<< @/snippets/book/ch17_math_errors.spr

```text
caught: isqrt input must not be negative
caught: clamp lower bound must not exceed upper bound
```

- `math.isqrt(-1)` 抛 `Error`，消息是 `isqrt input must not be negative`。
- `math.clamp(5, 10, 0)` 的下界比上界大，抛 `clamp lower bound must not exceed upper bound`。
- 两个都被 `catch problem: Error` 接住——标准库的错误和你的错误走同一条 `Error` 通道（Java 的异常不在此列，第 21 章）。

## 17.8 本章小结

- 六个数字类型：`Int`、`Int32`、`BigInt`、`Float`、`Float32`、`Decimal`；不同家族不隐式转换。
- 窄变宽自动（`Int32` → `Int`，`Float32` → `Float`），宽变窄写方法。
- 转换命名法：无后缀一定成功，`Exact` 会丢信息就报错，`Lossy` 舍入，`Trunc` 截断。
- `Decimal` 加减乘精确，除法写 `divide(scale, mode)`，mode 是 Java `RoundingMode` 的名字；`BigInt` 任意大，转回窄类型要 `Exact`。
- 浮点有 `NaN` / `Infinity` / `-0.0`，用 `isNaN` / `isInfinite` / `isFinite` / `approxEqual` 处理，不要用 `==`。
- `@std/math` 提供 `abs`、`min`、`max`、`sign`、`clamp`、`floor_div`、`isqrt`，失败的功能抛 `Error`。

## 17.9 动手练习

**练习 1（易）** 分别打印 `0.1 + 0.2`（`Float`）和 `Decimal.parse("0.1") + Decimal.parse("0.2")`，比较两条输出。

提示：一个会带着二进制误差，一个是精确的十进制。

::: details 参考答案
<<< @/snippets/book/ch17_ex1.spr

```text
0.30000000000000004
0.3
```
:::

**练习 2** 打印 `(-7).divTrunc(2)` 和 `math.floor_div(-7, 2)`，说出为什么不同。

提示：`divTrunc` 向零取整，`floor_div` 向负无穷取整。

::: details 参考答案
<<< @/snippets/book/ch17_ex2.spr

```text
-3
-4
```
:::

**练习 3** 用 `math.isqrt` 求 `50` 的整数平方根，再用 `math.clamp` 把 `12` 限进 `0..10`。

提示：`isqrt(50)` 是 7（7×7=49），`clamp` 的上界 10 会把 12 压成 10。

::: details 参考答案
<<< @/snippets/book/ch17_ex3.spr

```text
7
10
```
:::

**练习 4（难）** 把 `12345` 转成 `Int32` 和 `Float`（允许舍入），再把 `2.5` 截断成 `Int`。

提示：`Int` → `Int32` 用 `toInt32Exact()`；`Int` → `Float` 会丢信息，用 `toFloatLossy()`；`Float` → `Int` 有小数，用 `toIntTrunc()`。

::: details 参考答案
<<< @/snippets/book/ch17_ex4.spr

```text
12345
12345.0
2
```
:::

下一章：[模块、项目和依赖](/tutorial/ch18-modules-projects)——把代码分进文件，把别人的库拿过来用。
