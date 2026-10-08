# 7. 函数

上一章的循环消灭了重复的动作；这一章消灭重复的**代码段**。把一段做某件事的代码打包、起个名字，之后写名字就能用它——这就是函数。

这一章你会学到：

- 定义和调用函数；
- 参数、实参、返回值和 `-> Unit`；
- 提前 `return`，以及为什么每条路径都要 `return`；
- 作用域：什么名字在什么地方看得见；
- 顶层代码的先后顺序；
- `main` 为什么不是特殊的；
- 递归和调用追踪；
- 命名约定。

## 7.1 定义和调用

<<< @/snippets/book/ch07_define.spr

```text
8
20
```

先看定义：

- `func double(n: Int) -> Int:` 定义一个函数，`func` 是关键字，`double` 是名字。
- 括号里的 `n: Int` 是**参数**：名字加类型，函数内部靠它拿到外面传进来的值。
- `-> Int` 是**返回类型**：这个函数会还你一个 `Int`。
- 缩进的函数体里，`return n * 2` 把 `n * 2` 算出来，还给调用它的地方，函数到此结束。

再看调用：

- `double(4)` 是「调用」：写名字，把**实参** 4 放进括号。程序跳进函数体，`n` 就是 4，算出 8 还回来。
- `double(4)` 本身就是一个 `Int` 表达式，可以放进 `print(...)`，也可以放进 `let x = ...`。
- 同一个函数调用几次都行，每次从头执行；`double(10)` 里 `n` 是 10，得到 20。

用函数之后，上一章那种「复制三遍 `if`」的代码只需要写一遍分支，换不同的实参调用三次。

## 7.2 签名必须写全

函数的**签名**（参数和返回类型）里的类型一个都不能省。局部变量可以靠推断，比如 `let x = 3`，但签名是给调用者看的契约，必须写清楚。

### 故意写错：漏掉返回类型

<<< @/snippets/book/ch07_no_result_type.spr

```text
SPR-SYNTAX-ERROR [SYNTAX] main.spr:1:20: 'func double(n: Int)' has no result type
  hint: Write 'func double(n: Int) -> Unit:' when it returns nothing, or put the result type after '->', for example 'func double(n: Int) -> Int:'.
```

`1:20` 指向行尾冒号的位置：函数头到那里就结束了，`->` 和结果类型没写。提示给了两种可能：有结果就写 `-> Int`，没有结果就写 `-> Unit`（下一节讲）。参数类型漏了也一样不行，同样要在定义处补上。

## 7.3 不返回东西：Unit

不是所有函数都要还一个值。只做一件事（比如打印）的函数，返回类型写 `-> Unit`：

<<< @/snippets/book/ch07_unit.spr

```text
Hello, Ada!
Hello, Grace!
```

`Unit` 读作「没有值」：这个函数做完了事就结束，不会还回一个结果。调用它时不需要用结果，直接一行调用即可。

### 故意写错：把 Unit 结果存进变量

既然没有值，就不能把返回值绑定到名字上：

<<< @/snippets/book/ch07_unit_bind.spr

```text
SPR-TYPE-UNIT [TYPE] main.spr:4:1: Cannot bind a Unit result to a variable
```

编译器在这里拦下你，是因为「把打印函数的返回值存起来」几乎总是写错了代码。想让函数还一个值，就把返回类型改成真正的类型，并在 `return` 后面写上要还的东西。

## 7.4 提前 return

`return` 一执行，函数立刻结束，后面的代码都不再走。所以可以用它来做「不满足条件就早点退出」，这比层层嵌套的 `if` 更好读：

<<< @/snippets/book/ch07_return.spr

```text
invalid
minor
adult
negative, skipping
value 5
```

- `label(-1)`：第一个 `if` 条件成立，`return "invalid"`，函数立刻结束，后面两个 `if` 不再检查。`label(10)` 走到第二个 `return`，`label(30)` 两条 `if` 都不成立，落到最后的 `return "adult"`。
- `show(-3)`：`n < 0` 成立，打印 `negative, skipping`，然后一个**不带值**的 `return` 结束函数。
- `show(5)`：条件不成立，打印 `value 5`。`-> Unit` 的函数不需要在末尾写 `return`；想提前走时，写一个光秃秃的 `return` 就行。

注意 `print("value " + n)`：`+` 的一边是 `String`，`Int` 会自动变成文字拼上去。

## 7.5 故意写错：漏了一条路径

如果一个函数的返回类型不是 `Unit`，那么每条执行路径都必须以 `return` 结束：

<<< @/snippets/book/ch04_missing_return.spr

```text
SPR-FLOW-MISSING-RETURN [FLOW] main.spr:1:1: Function 'sign' must return String on every path
```

`n > 0` 和 `n < 0` 都有 `return`，但 `n == 0` 时两个分支都不进，函数会走到末尾而没有值可还。编译器做**流程分析**（错误类别 `FLOW`），把所有可能路径都走一遍才发现这个问题。修法：补一个 `else` 分支，或者在末尾再加一条 `return`。

## 7.6 作用域：名字在哪里看得见

每个函数是一个小世界。参数和函数体里声明的名字只在这个函数里有意义：

### 故意写错：函数里的局部名字跑到了外面

<<< @/snippets/book/ch07_scope.spr

```text
SPR-NAME-UNRESOLVED [NAME] main.spr:6:7: Unresolved name 'hidden'
```

`hidden` 只存在于 `setup` 的体内。函数一结束，这个名字就没了，所以外面的 `print(hidden)` 找不到它。反过来，函数体可以看见顶层的名字：

<<< @/snippets/book/ch07_global.spr

```text
15
```

`triple` 里用到了顶层的 `rate`。函数定义只是「记下这段代码」，真正执行是调用的时候，那时 `rate` 已经存在了。

## 7.7 顶层顺序：函数可以先调用后定义，变量不行

函数定义不按执行顺序走。下面这样把调用写在定义前面是可以的：

<<< @/snippets/book/ch07_call_before.spr

```text
6
```

顶层语句（不在任何函数里的代码）则严格按出现顺序执行一次。用还没声明的顶层变量是编译错误：

<<< @/snippets/book/ch07_forward.spr

```text
SPR-NAME-FORWARD-REFERENCE [NAME] main.spr:1:7: Top-level 'n' is used before its declaration; top-level statements run once, in source order
  hint: Move the declaration of 'n' above this statement.
  Declared here at 3:1
```

注意最后一行 `Declared here at 3:1`：编译器把 `n` 的声明位置也标了出来，方便对照。

还有一种更隐蔽的情况：函数定义的位置无所谓，但函数**运行时**用到的顶层变量必须已经初始化过。下面这段能通过编译，运行到 `value()` 时才失败：

```sprig
func value() -> Int:
    return n

print(value())

let n = 7
```

```text
SPR-RUNTIME-EXCEPTION [RUNTIME] main.spr:2:5: Top-level 'n' was used before its initializer ran
  hint: Top-level statements run in source order; declare the binding above the first top-level statement that calls code using it. Run with --stacktrace to see the JVM stack.
```

`SPR-RUNTIME-EXCEPTION` 开头的 `RUNTIME` 说明它发生在运行时，不是编译期。把 `let n = 7` 移到调用上面即可。

## 7.8 main 不是特殊的

有些语言规定程序从 `main` 函数开始。Sprig 没有这个规矩：**顶层语句就是程序**，从上到下执行；`sprig init` 生成的模板只是习惯上写了一个 `main` 再调用它：

<<< @/snippets/book/ch04_main.spr

```text
from main
```

`main` 在这里只是普通函数名。删掉 `main()` 那行调用，函数定义还在，但什么都不会打印——定义不等于执行；`sprig run` 这时会额外提醒你「没有顶层语句调用 main」。

## 7.9 递归：函数调用自己

函数体里可以调用函数自己，这叫**递归**。用 4 的阶乘（4×3×2×1）做例子：

<<< @/snippets/book/ch07_recursion.spr

```text
24
```

`factorial(4)` 想要 4 乘以 `factorial(3)`，`factorial(3)` 又想要 3 乘以 `factorial(2)`……一直问到 `factorial(1)`。`n <= 1` 这一支直接 `return 1`，不再往下问，这叫**基准情形**。之后一层层把结果还回去：

| 步骤 | 正在算 | 结果 |
|---|---|---|
| 1 | `factorial(4)` | 4 × `factorial(3)`，等它 |
| 2 | `factorial(3)` | 3 × `factorial(2)`，等它 |
| 3 | `factorial(2)` | 2 × `factorial(1)`，等它 |
| 4 | `factorial(1)` | 基准情形，是 1 |
| 5 | `factorial(2)` 收结果 | 2 × 1 = 2 |
| 6 | `factorial(3)` 收结果 | 3 × 2 = 6 |
| 7 | `factorial(4)` 收结果 | 4 × 6 = 24 |

递归函数必须有能停下来的分支。少了它，调用会一直往下堆，最后在运行时以 `StackOverflowError` 结束。

## 7.10 名字怎么起

Sprig 的命名规则（`sprig help language` 里有完整说明）：

- 你自己声明的名字——函数、方法、参数、变量、字段、顶层 `let`——用 `lower_snake_case`：`read_utf8`、`max_by`。
- 类型——类、契约、枚举、variant、它们的 case 和类型参数——用 `UpperCamelCase`：`Light`、`Expense`。
- 内置方法（`toString`、`divTrunc`）和 Java API 保持 `camelCase`。

编译器不会因为 `myFunc` 这样的名字报错，但全书的代码和标准库都遵守这套约定，跟着写能少踩坑。

## 本章小结

- `func 名字(参数: 类型, ...) -> 返回类型:`，函数体缩进。
- 签名上的类型必须写；不返回值的写 `-> Unit`。
- 实参按位置传入；调用就是名字加括号。
- `return` 立即结束函数；不是 `Unit` 的函数每条路径都要 `return`。
- 参数和局部变量只活在函数里；顶层名字在函数里可见。
- 函数可以先调用后定义；顶层语句按顺序执行，顶层变量必须先用先声明。
- 没有特殊的 `main`：顶层语句就是程序。
- 递归要有基准情形；变量追踪表在函数调用上同样管用。
- 声明用 `lower_snake_case`，类型用 `UpperCamelCase`。

## 动手练习

### 练习 1：平方函数

写一个 `square(n: Int) -> Int`，返回 `n * n`，并打印 `square(6)`。

提示：函数体只有一行 `return n * n`。

::: details 参考答案

<<< @/snippets/book/ch07_ex_square.spr

```text
36
```

:::

### 练习 2：三个数里最大的

写一个 `max3(a: Int, b: Int, c: Int) -> Int`，返回三个数中最大的那个，并打印 `max3(3, 9, 5)`。

提示：在函数里用 `var best` 先放 `a`，再用两个 `if` 分别挑战 `b` 和 `c`，最后 `return best`。

::: details 参考答案

<<< @/snippets/book/ch07_ex_max3.spr

```text
9
```

:::

### 练习 3：是不是偶数

写一个 `is_even(n: Int) -> Bool`，`n` 是偶数返回 `true`，否则 `false`；分别打印 `is_even(10)` 和 `is_even(7)`。

提示：返回一个 `Bool` 表达式：`n % 2 == 0`。

::: details 参考答案

<<< @/snippets/book/ch07_ex_even.spr

```text
true
false
```

:::

### 练习 4：倒计时函数

写一个 `countdown(start: Int) -> Unit`，从 `start` 打印到 1，最后打印 `liftoff`；调用 `countdown(3)`。

提示：参数不能改（和 `let` 一样），在函数里先 `var n = start`，再用上一章的 `while`。

::: details 参考答案

<<< @/snippets/book/ch07_ex_countdown.spr

```text
3
2
1
liftoff
```

:::

### 练习 5：递归求和

用递归写 `sum_to(n: Int) -> Int`，返回 1+2+…+`n`；打印 `sum_to(5)`。

提示：基准情形是 `n <= 0` 时返回 0；否则返回 `n + sum_to(n - 1)`。

::: details 参考答案

<<< @/snippets/book/ch07_ex_sum_to.spr

```text
15
```

:::

::: tip 学过其他语言？

- Python：`def` 换成 `func`，缩进和冒号一样；但是类型必须写在签名上，没有默认参数、没有 `*args`，也没有嵌套函数。
- Java / JavaScript：没有 `function` 关键字；函数不能重名（没有重载）；没有「最后一行自动作为返回值」，每条路径都要显式 `return`。
- 参数和 `let` 一样不能重新赋值；想要可变的副本，在函数里自己声明 `var`。
- 需要「局部的小函数」时用第 15 章的 lambda。

:::

下一章：[第 8 章：列表](/tutorial/ch08-lists)。
