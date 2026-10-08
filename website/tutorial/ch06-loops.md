# 6. 重复：循环

上一章最后那个温度程序把同一段 `if` 抄了三遍，只为了换一个数字。计算机最擅长的就是重复劳动：告诉它规则，让它自己转。这一章讲两种转法：`while` 和 `for`。

这一章你会学到：

- 用 `while` 在条件成立时反复执行；
- 用一张变量追踪表跟着循环走一遍；
- 用 `for` 和 `range` 数着数重复；
- 用 `for` 逐个取出字符串和列表里的东西；
- 用 `break` 提前离开循环、用 `continue` 跳下一轮、用 `while true` 一直转、用 `pass` 占位；
- 用这些拼出 FizzBuzz。

## 6.1 while：条件为真就一直做

<<< @/snippets/book/ch06_while.spr

```text
3
2
1
liftoff
```

`while 条件:` 和 `if` 长得像，区别在于它不只看一次：

1. 检查 `count > 0`。`count` 是 3，为真，执行缩进的块。
2. 块里先打印 `3`，再把 `count` 减 1，变成 2。
3. 回到第 1 步，再检查一次。就这样打印 `2`、`1`，`count` 变成 0。
4. 再检查时 `0 > 0` 为假，离开循环。
5. `print("liftoff")` 没有缩进，不在循环里，循环结束后执行。

**循环能停下来，是因为块里有一行让条件最终变假**——这里是 `count -= 1`。忘了它，`count` 永远是 3，程序会一直打印下去，停不下来；在终端里按 `Ctrl+C` 可以强行结束。

## 6.2 变量追踪表

循环读代码容易眼花，画一张表一步一步跟着走最稳。下面这个程序把 1 到 5 加起来：

<<< @/snippets/book/ch06_while_sum.spr

```text
15
```

追踪表，每一行是一轮：

| 第几轮 | 检查条件时的 `n` | `n <= 5` | 这轮加完后 `total` | 这轮加完后 `n` |
|---|---|---|---|---|
| 循环开始前 | 1 | — | 0 | 1 |
| 1 | 1 | true | 1 | 2 |
| 2 | 2 | true | 3 | 3 |
| 3 | 3 | true | 6 | 4 |
| 4 | 4 | true | 10 | 5 |
| 5 | 5 | true | 15 | 6 |
| 结束后 | 6 | false | 15 | 6 |

最后 `total` 是 15：1+2+3+4+5。自己写循环卡住时，就照这样找一张纸，把「条件里的值、关键变量的值」每轮记一遍。

## 6.3 故意写错：while 的条件不是 Bool

<<< @/snippets/book/ch06_while_nonbool.spr

```text
SPR-TYPE-CONDITION [TYPE] main.spr:2:7: Condition must be Bool (no truthiness) (expected Bool, actual Int)
```

第 3 章说过：Sprig 没有「非零即真」。`while n:` 里 `n` 是 `Int`，编译器要求你写出真正的比较：`while n > 0:`。`if` 的条件也是同样的规则。

## 6.4 for 和 range：数着数重复

知道要点数、数到几，用 `for` 更省事：

<<< @/snippets/book/ch06_range.spr

```text
0
1
2
--
2
3
4
5
--
0
3
6
9
--
5
3
1
```

`for 变量 in 一串值:` 会把那串值一个一个取出来，每取一个执行一遍块。`range` 产生整数：

- `range(3)`：从 0 开始，到 3 之前停，得到 0、1、2。**不含终点**。
- `range(2, 6)`：从 2 到 6 之前，得到 2、3、4、5。
- `range(0, 10, 3)`：第三个参数是步长，每次加 3，得到 0、3、6、9。
- `range(5, 0, -2)`：步长可以是负数，从 5 往下，得到 5、3、1。

`range` 的参数个数可以不同：写一个就是「从 0 到这个数之前」，写两个是「起点、终点」，写三个再加上步长。

## 6.5 故意写错：循环变量不能改

循环变量每一轮会被换成下一个值，所以它像 `let` 一样不可改：

<<< @/snippets/book/ch06_for_assign.spr

```text
SPR-NAME-LET-ASSIGN [TYPE] main.spr:2:5: Cannot assign to immutable binding 'n'; declare it with var
```

想让一个值每轮从循环变量算出、再自己变化，就在块里声明一个 `var`：

<<< @/snippets/book/ch06_for_var.spr

```text
1
3
5
```

`n` 每轮拿到 0、1、2，`doubled` 每轮重新声明，算出 `1`、`3`、`5`。循环变量也不在循环外面生存。循环结束后再用 `print(n)` 是找不到 `n` 的，编译器会报 `SPR-NAME-UNRESOLVED`。

## 6.6 遍历字符串和列表

`for` 不止能数数，它右边的「一串值」可以是任何能逐个取的东西。第 4 章见过的字符串就是一串字符：

<<< @/snippets/book/ch06_for_string.spr

```text
S
p
r
i
g
```

每一轮 `ch` 拿到一个字符。它是只含一个字符的 `String`（按码点算，第 4 章讲过），同样在循环体里不能改。

方括号包起来的是列表字面量，第 8 章细讲；`for` 对它的用法和字符串一样：

<<< @/snippets/book/ch06_for_list.spr

```text
tea
milk
rice
```

## 6.7 break 和 continue

- `continue`：这一轮不做了，直接开始下一轮。
- `break`：整个循环到此为止，后面的轮次都不做了。

<<< @/snippets/book/ch06_break_continue.spr

```text
1
3
5
7
```

一轮一轮看：

| `n` | 发生什么 |
|---|---|
| 1 | 奇数；`1 > 7` 为假；打印 `1` |
| 2 | 偶数，`continue`，回到循环头 |
| 3 | 打印 `3` |
| 4 | `continue` |
| 5 | 打印 `5` |
| 6 | `continue` |
| 7 | 打印 `7` |
| 8 | 偶数，先 `continue` 了，不会走到 `break` |
| 9 | 奇数；`9 > 7` 为真，`break`，整个循环结束 |

注意顺序：第 8 轮在检查 `break` 之前就被 `continue` 截住了。循环里两个出口都别写得太绕。

## 6.8 故意写错：break 写在了循环外面

<<< @/snippets/book/ch06_break_outside.spr

```text
SPR-FLOW-BREAK [FLOW] main.spr:1:1: break outside a loop
```

`break` 和 `continue` 只能待在循环体里。编译器做流程分析（错误类别是 `FLOW`），一眼看出这行没有循环可以离开。`continue` 在循环外会报 `SPR-FLOW-CONTINUE`，规则一样。

## 6.9 while true 和 pass

有些循环的结束条件不适合写在开头，比如「找到第一个能被 7 整除的数」：先让 `while true:` 一直转，在里面判断找到了就 `break`。

`pass` 是「什么都不做」的语句。分支的块里必须至少有一行，实在没事可做时就写 `pass` 占位：

<<< @/snippets/book/ch06_while_true_pass.spr

```text
7
0
1
3
4
```

上半段：`answer` 从 0 每次加 1，加到 7 时 `7 % 7 == 0`，`break`，打印 `7`。`while true` 的写法一定要确保里面有 `break`，不然就真的永远转下去。

下半段：`range(5)` 取出 0 到 4。`n == 2` 时块里没有事要做，但块不能是空的，所以用 `pass` 占位；其他值由 `else` 的块打印，所以 2 不在输出里。

## 6.10 合起来：FizzBuzz

把 `for`、`if`、`elif`、`%` 拼成一个经典小题目：从 1 数到 15，能被 3 整除打印 `Fizz`，能被 5 整除打印 `Buzz`，同时被两者整除打印 `FizzBuzz`，否则打印数字本身。

<<< @/snippets/book/ch06_fizzbuzz.spr

```text
1
2
Fizz
4
Buzz
Fizz
7
8
Fizz
Buzz
11
Fizz
13
14
FizzBuzz
```

能同时被 3 和 5 整除的数也能分别被它们整除，所以 `n % 15 == 0` 这条**必须放在最前面**，否则 15 会先被 `n % 3 == 0` 抓住，打印成 `Fizz`。分支从上往下检查，先到先得——这是上一章就定下的规矩。

## 本章小结

- `while 条件:` 每轮开始前检查一次；块里要有让条件最终变假的东西。
- 用追踪表读循环：把每轮的条件和关键变量列出来。
- 条件只能是 `Bool`，没有「非零即真」。
- `for x in 一串值:` 逐个取值；`range` 的三种写法都不含终点，步长可以是负数。
- 循环变量像 `let`，不能改，出了循环也不存在。
- `continue` 跳下一轮，`break` 结束整个循环，两者只能写在循环里。
- `while true` 配 `break`；`pass` 是空语句。

## 动手练习

### 练习 1：平方表

用 `for` 和 `range` 打印 1 到 10 的平方，每行一个。

提示：`for n in range(1, 11):`，块里打印 `n * n`。

::: details 参考答案

<<< @/snippets/book/ch06_ex_squares.spr

```text
1
4
9
16
25
36
49
64
81
100
```

:::

### 练习 2：用 while 算阶乘

用 `while` 算 5 的阶乘（5×4×3×2×1），打印结果。

提示：准备 `var n = 5` 和 `var result = 1`；每轮 `result *= n`，然后 `n -= 1`，直到 `n > 1` 不成立。

::: details 参考答案

<<< @/snippets/book/ch06_ex_factorial.spr

```text
120
```

:::

### 练习 3：倒计时

用 `range` 从 5 数到 1，每行一个，最后打印 `liftoff`。

提示：步长写 `-1`，起点 5、终点写 0（因为不含终点）。

::: details 参考答案

<<< @/snippets/book/ch06_ex_countdown.spr

```text
5
4
3
2
1
liftoff
```

:::

### 练习 4：数元音

`"banana"` 里有几个元音（`a`、`e`、`i`、`o`、`u`）？打印个数。

提示：`var vowels = 0`，`for ch in "banana":`，用 `or` 把所有元音串成条件。

::: details 参考答案

<<< @/snippets/book/ch06_ex_vowels.spr

```text
3
```

:::

### 练习 5：第一个平方超过 200 的数

从 1 往上找，哪个数的平方第一次超过 200？用 `break` 找到就停，打印这个数。

提示：`var found = -1` 存结果；循环里判断 `n * n > 200`，成立就把 `n` 存进 `found` 再 `break`。

::: details 参考答案

<<< @/snippets/book/ch06_ex_first_square.spr

```text
15
```

:::

::: tip 学过其他语言？

- Python：`while`、`for ... in`、`range`、`break`、`continue` 的写法和含义几乎一样，`range` 也同样不含终点。
- Java / JavaScript / C：没有 `i++` 和 `++i`，写 `i += 1`；没有 C 式的 `for (i = 0; i < n; i++)`，数数就用 `range`；没有 `do ... while`；没有 `for(;;)`，写 `while true`。
- 循环变量出了循环就不可用，这点和 Python 不同：在 Sprig 里它是每轮新建的。

:::

下一章：[第 7 章：函数](/tutorial/ch07-functions)。
