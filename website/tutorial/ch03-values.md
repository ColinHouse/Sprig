# 3. 值、变量和算术

这一章你会学到：

- 用 `let` 和 `var` 给值起名字；
- Sprig 的基本类型：`Int`、`Float`、`Bool`、`String`；
- 加减乘、取余、整数除法的规矩；
- 为什么 Int 和 Float 不能混着算；
- 布尔值和短路求值；
- 运算顺序；
- 整数溢出的处理方式。

## 3.1 值和名字

程序要记住数字和文字，才能拿它们做计算。给一个值起名字，这个名字就叫**变量**（variable）。看一个完整的例子：

<<< @/snippets/book/ch02_bindings.spr

```text
Ada
2
true
```

逐行读：

- `let name = "Ada"`：把名字 `name` 绑定到字符串 `"Ada"`。`let` 声明的绑定**不能再赋值**。类型不用写，Sprig 从右边的值推断出 `name` 是 `String`。
- `var visits = 0`：`var` 声明的变量**可以再赋值**。这里推断出 `visits` 是 `Int`。
- `visits += 1`：`+=` 是“加 1 再存回去”的简写，相当于 `visits = visits + 1`。现在 `visits` 是 1。
- `visits = visits + 1`：再改成 2。
- `let height: Float = 1.68`：这次把类型写出来了。写法是“名字、冒号、类型”。一般不用写，想强调或想限定类型时再写。
- `let tall = height > 1.6`：`>` 比较两个小数，结果是一个**布尔值**（Bool），不是真就是假；所以 `tall` 是 `true`。
- 三个 `print` 把 `name`、`visits`、`tall` 依次打印出来。

几个要记住的词：

| 词 | 意思 |
|---|---|
| 值（value） | 程序里的一份数据，比如 `"Ada"`、`2`、`true` |
| 变量（variable） | 给值起的名字 |
| 类型（type） | 这份值是什么种类，比如 `Int`、`Float`、`Bool`、`String` |
| 绑定（binding） | 名字和值之间的对应关系 |
| 推断（inference） | 不写类型，编译器从右边的值自己判断 |

**默认用 `let`**，只有确实需要改才写 `var`。这不只是风格：第 13 章会看到，编译器对 `let` 能做出更多推断（比如判过不是空之后，就把它当成有值），对 `var` 不能。

### 故意写错：给 `let` 绑定重新赋值

<<< @/snippets/book/ch02_let_assign.spr

```text
SPR-NAME-LET-ASSIGN [TYPE] main.spr:2:1: Cannot assign to immutable binding 'name'; declare it with var
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

位置 `2:1` 指向第二行开头的 `name`。说明写得直接：这个绑定不可改；确实要改，就声明成 `var`。注意编译器是在**运行前**就拒绝了这段程序，一行都不会执行。

## 3.2 算术

数字的加、减、乘分别是 `+`、`-`、`*`。整数除法是特殊的，取余用 `%`：

<<< @/snippets/book/ch03_arithmetic.spr

```text
9
5
14
3
1
-1
1
9000000000000
```

- `a + b`、`a - b`、`a * b`：9、5、14。
- `a.divTrunc(b)`：`divTrunc` 是“除法，去掉小数部分”。7 除以 2 是 3.5，去掉小数得 3。
- `a % b`：取余数。7 除以 2 商 3 余 **1**。
- `-7 % 3` 是 **-1**，`7 % -3` 是 **1**：余数的符号跟着**左边**那个数。
- `9000000000 * 1000` 是 9 万亿，Int 装得下，正常打印 `9000000000000`。

### 故意写错：整数直接相除

<<< @/snippets/book/ch02_division.spr

```text
SPR-NUM-DIVISION [TYPE] main.spr:3:7: Integer / would truncate; use a.divTrunc(b), or convert both operands explicitly (expected explicit division, actual Int / Int)
  hint: Write a.divTrunc(b) to drop the remainder on purpose, or a.toFloatExact() / b.toFloatExact() for a Float result. text.fixed(value, decimals) from @std/text.spr prints a Float with that many decimals.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

`7 / 2` 到底应该是 3 还是 3.5？不同的语言给不同答案。Sprig 不替你选：想要 3 就写 `a.divTrunc(b)`，想要 3.5 就把两边都转成 `Float`（下一节）。`hint` 把两种写法都给了。

## 3.3 改变变量的值：复合赋值

`var` 声明的变量可以用 `+=`、`-=`、`*=` 一次完成“算一下再存回去”。小数还可以用 `/=`：

<<< @/snippets/book/ch03_compound.spr

```text
3.0
9
```

跟着值走一遍：

| 语句 | `total` | `count` |
|---|---|---|
| `var total = 6.0` | 6.0 | — |
| `total *= 2.0` | 12.0 | — |
| `total /= 4.0` | 3.0 | — |
| `var count = 7` | 3.0 | 7 |
| `count += 3` | 3.0 | 10 |
| `count -= 1` | 3.0 | 9 |

`total *= 2.0` 就是 `total = total * 2.0`；`count -= 1` 就是 `count = count - 1`。没有 `%=`，也没有 `++` 和 `--`：写 `count++` 会被拒绝，报错直接告诉你改成 `count += 1`。

::: tip 学过其他语言？
Sprig 的 `let` / `var` 对应很多语言的 `const` / `let`（或 `final` / 普通变量）。没有 `++`/`--`，也没有 `%=`；`/=` 只在小数上存在，因为整数 `/` 本身就不允许。这些不是缺少功能，而是让“值的改变”都看得见。
:::

## 3.4 整数和小数不混算

Sprig 常用的两个数字类型：

- `Int`：64 位有符号整数，写出来像 `7`、`-3`、`9000000000`。
- `Float`：双精度小数（IEEE 754 binary64），写出来必须带小数点或指数，像 `2.5`、`1.0`、`10.0`。

两者**不会自动互相转换**。故意写错：

<<< @/snippets/book/ch02_mixed.spr

```text
SPR-NUM-MIXED [TYPE] main.spr:3:7: Operator '*' has no implicit conversion between Float and Int (expected matching numeric families, actual Float and Int)
  hint: Convert the Int side: count.toFloatExact().
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

`price` 是 `Float`，`count` 是 `Int`，`*` 不接受“一个 Float 乘一个 Int”。按提示显式转换：

<<< @/snippets/book/ch03_float.spr

```text
1.0
3.0
10.0
0.3333333333333333
```

- `let whole: Float = 1`：整数字面量可以直接当 `Float` 用，因为这个 1 能精确表示。
- `1.toFloatExact() + 2.0` 是 3.0：先转成 Float 再相加。名字里的 `Exact` 表示“如果转过去会丢精度，就报错而不是悄悄舍入”；想接受舍入就写 `toFloatLossy()`。
- `2.5 * 4.0` 是 10.0：两个 Float 相乘，结果也是 Float，打印出来一定是带小数点的形式（`10.0` 不是 `10`）。
- `1.0 / 3.0` 是 `0.3333333333333333`：小数除法可以用 `/`。二进制浮点表示不了 0.1、1/3 这类数，所以会有这种长尾；算钱不要用 `Float`，用整数“分”或者第 17 章的 `Decimal`。

反方向，`Float` 转 `Int` 也要说清楚：`2.7.toIntTrunc()` 是 2（截掉小数），`6.0.toIntExact()` 是 6（不是整数就报错）。规则一句话：**名字里写清楚会丢什么信息，你负责选一个**。

## 3.5 布尔值和短路

比较运算 `>`、`<`、`>=`、`<=`、`==`、`!=` 产生 `Bool`。逻辑运算用英文词 `and`、`or`、`not`，不是 `&&`、`||`、`!`：

<<< @/snippets/book/ch02_bool.spr

```text
true
true
```

- `count > 0 and count < 10`：两个比较都为真，`and` 的结果是 `true`。
- `not open or count == 3`：`not open` 是 `false`，`count == 3` 是 `true`，`or` 的结果是 `true`。
- `==` 比较相等（两个等号），`=` 是赋值（一个等号），不要写混。

`and` 和 `or` 是**短路**的：`and` 左边的结果为假时，右边根本不会求值；`or` 左边为真时也一样。这不是省电，是保证安全：

<<< @/snippets/book/ch03_short_circuit.spr

```text
false
true
```

`1.divTrunc(0)` 会除以零，真的执行会出错。但 `false and ...` 的右边没有执行，所以第一行安静地打印 `false`；`true or ...` 同理。如果 Sprig 不短路，这个程序早就崩了。

### 故意写错：没有“非零即真”

<<< @/snippets/book/ch03_bool_operand.spr

```text
SPR-TYPE-OPERAND [TYPE] main.spr:1:10: 'and' requires Bool operands (expected Bool, actual Int)
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

很多语言里 `0`、空字符串、空列表都算“假”。Sprig 没有这套：逻辑运算和以后要学的 `if`、`while` 只接受 `Bool`。想表达“数量大于零”就写 `count > 0`，想表达“列表不空”就写 `items.size() > 0`。

## 3.6 运算顺序

和数学课一样，`*`、`%` 先于 `+`、`-`；比较在算术之后；括号优先：

<<< @/snippets/book/ch03_precedence.spr

```text
14
20
true
true
```

- `2 + 3 * 4` 是 14，不是 20：先算 `3 * 4`。想先加就写 `(2 + 3) * 4`，得到 20。
- `1 + 1 == 2` 是 `true`：先算加法，再比较。
- `not false` 是 `true`。
- 比较不能连写。`1 < 2 < 3` 不是“1 小于 2 且 2 小于 3”，编译器会拒绝。想表达这个意思要写 `1 < 2 and 2 < 3`。

## 3.7 溢出：整数不会悄悄绕回

Int 的加减乘会检查结果是否超出 64 位范围。超出时程序**停下来报错**，而不是悄悄变成一个负数。自己试试这个两行的程序：

```sprig
let top = 9223372036854775807
print(top + 1)
```

运行 `sprig run overflow.spr`，屏幕上会出现：

```text
SPR-RUNTIME-EXCEPTION [RUNTIME] overflow.spr:2:1: Numeric error: Int addition overflow
  hint: The exact result does not fit in the integer type. Check before the operation, for example 'if value > 0 and total > 9223372036854775807 - value:' for an Int addition, or catch it: import java.lang.ArithmeticException as ArithmeticException, then put the operation in 'try:' with 'catch problem: ArithmeticException:'. See `sprig help numerics`. Run with --stacktrace to see the JVM stack.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

`hint` 建议在运算前先检查范围，或者捕获异常（`try`/`catch` 第 14 章讲）。这条报错属于**运行时错误**：程序通过了检查，跑到这一行才失败；加 `--stacktrace` 运行才能看到 JVM 调用栈。`Float` 不一样：`1.0 / 0.0` 得到 `Infinity`，`0.0 / 0.0` 得到 `NaN`，不报错，跟 IEEE 754 标准一致。

## 本章小结

- `let` 绑定一次，`var` 可以改；默认用 `let`。类型能推断，也可以像 `height: Float` 那样写出来。
- 基本类型：`Int`（64 位整数）、`Float`（双精度小数）、`Bool`、`String`。
- 整数除法写 `divTrunc`，取余写 `%`；`%` 的符号跟着左边的数。
- 复合赋值 `+= -= *=` 可以用在数字上，小数还有 `/=`；没有 `++`、`--`、`%=`。
- Int 和 Float 不混算；转换方法的名字说明会不会丢信息（`Exact` 报错、`Lossy` / `Trunc` 有损）。
- `and`、`or`、`not` 只接受 `Bool`；`and` / `or` 短路求值。
- `*` 先于 `+`，括号优先；比较不能连写。
- 整数溢出在运行时报 `SPR-RUNTIME-EXCEPTION`，不会绕回。

## 动手练习

1. 一天有 24 小时，一小时有 60 分钟，一分钟有 60 秒。用一行算术算出 2 小时 30 分钟是多少秒。
   提示：先算总分钟数，再乘 60；括号可以改变顺序。

::: details 参考答案
<<< @/snippets/book/ch03_ex_seconds.spr

```text
9000
```

`2 * 60 + 30` 先算乘法，再算加法。
:::

2. 不看答案，先写出 `17 % 5` 和 `-17 % 5` 的结果，再运行验证。
   提示：余数的符号跟着左边那个数。

::: details 参考答案
<<< @/snippets/book/ch03_ex_remainder.spr

```text
2
-2
```

余数的符号跟着左边（`-17`）。
:::

3. `7 / 2` 不会编译。把它改成得到 3.5 的写法。
   提示：`toFloatExact()` 把整数转成小数。

::: details 参考答案
<<< @/snippets/book/ch03_ex_divide.spr

```text
3.5
```

两边都转成 `Float` 再除；想要 3 就用 `7.divTrunc(2)`。
:::

4. 用 `var` 和 `+=` 写一个计数器，从 0 数到 3，每加一次就打印一次当前值。
   提示：`var counter = 0`，然后写三次 `counter += 1`，每次后面跟一个 `print`。

::: details 参考答案
<<< @/snippets/book/ch03_ex_counter.spr

```text
1
2
3
```
:::

5. 下面这行不能编译，为什么？改成能用的写法。

```sprig
let ok = 1 and true
```

提示：`and` 两边都要是 `Bool`；先写一个比较，比如 `1 > 0`。

::: details 参考答案
`and` 两边都必须是 `Bool`，`1` 是 `Int`。先比较，再 `and`：

<<< @/snippets/book/ch03_ex_and.spr

```text
true
```
:::

下一章玩文字：[第 4 章：文字](/tutorial/ch04-text)。
