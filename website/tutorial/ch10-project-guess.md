# 10. 小项目：猜数字

这一章你会学到：

- 把一个小问题拆成几个函数；
- 用 `@std/random` 生成可复现的随机数；
- 用固定的一份输入清单测试游戏逻辑；
- 用 `@std/process` 从键盘逐行读输入；
- 命令行参数、标准错误和退出状态。

## 10.1 游戏规则

程序想好一个 1 到 100 之间的秘密数字。你每次猜一个数，它回答：

- 猜小了：`too low`
- 猜大了：`too high`
- 猜中：`correct`，并告诉你用了几次

这个项目不大，但把前面九章的东西都用上了：变量、循环、函数、可空值、列表。我们分四步搭起来，每步都能单独运行。

## 10.2 第一步：秘密数字

随机数在 `@std/random.spr` 里。`seeded(seed)` 用一个种子创建随机源：同一个种子，每次运行给出同一串数字，所以书里的输出可以有一个确定的结果。想要每次不同，用 `fresh()`。

<<< @/snippets/book/ch10_secret.spr

```text
1
5
5
21
```

逐行读：

- `let dice = random.seeded(7)` 创建种子为 7 的随机源。
- `dice.next_int(6)` 返回 `0` 到 `5`（含两端）中的一个整数；`+ 1` 之后得到 1 到 6，像骰子。
- 第一次是 `1`，第二次是 `5`：同一个随机源连续取数，序列往前走。
- 第三个 `dice.next_int(6) + 1` 还是 `5`，说明它接着前面的序列。
- `random.seeded(7).next_int(100) + 1` 新建一个同种子的随机源，序列从头开始，得到 `21`——这就是我们游戏里秘密数字取 1..100 的写法（`next_int(100)` 是 0..99，加 1 就是 1..100）。

`next_int` 的参数必须为正，否则会以 `Error` 失败；那是第 14 章错误处理的内容，这里只写合法的正整数。

## 10.3 第二步：判断一次猜测

把"猜一次"的判断写成一个函数。它接收秘密数字和猜测，返回该回答的话：

<<< @/snippets/book/ch10_hint.spr

```text
too low
too high
correct
```

- `func hint(secret: Int, guess: Int) -> String:` 声明两个 `Int` 参数，返回 `String`。
- `guess < secret` 走第一条 `return "too low"`。
- 否则看 `guess > secret`，返回 `"too high"`。
- 两个 `if` 都没走到，说明猜中了，最后一行 `return "correct"` 收尾。每个分支都返回，编译器检查得到（第 7 章）。

## 10.4 第三步：玩完一整局

键盘输入没法在书里自动验证，所以我们先把"每次猜什么"当成一份清单传进来。函数返回用掉的次数：

<<< @/snippets/book/ch10_guess_fixed.spr

```text
secret is 21
50: too high
10: too low
skipped: oops
21: correct
finished after 3 guesses
```

逐行读 `play` 的循环，并跟着追踪表：

| 第几步 | `text` | `guess` | `tries` | 输出 |
|---|---|---|---|---|
| 开始 | — | — | 0 | — |
| 1 | `"50"` | 50 | 1 | `50: too high` |
| 2 | `"10"` | 10 | 2 | `10: too low` |
| 3 | `"oops"` | null | 2 | `skipped: oops` |
| 4 | `"21"` | 21 | 3 | `21: correct`，`return 3` |

- `text.toIntOrNull()` 把字符串转成 `Int`；转不了时得到 `null`，类型是 `Int?`。
- `if guess == null:` 分支打印 `skipped: oops`，`continue` 直接进入下一次循环，`tries` 不加。
- `hint(secret, guess)` 复用了 10.3 的函数；这里 `guess` 已经收窄成 `Int`。
- 猜中时 `return tries` 立刻结束函数，后面的清单不再看——这就是提前返回。
- 如果清单走完都没猜中，打印 `out of guesses; the number was 21`，并返回已经用掉的次数。
- 顶层先算出 `secret`，再调用 `play`，最后打印总次数。`"finished after " + used + " guesses"` 说明 `Int` 拼进字符串时可以直接用 `+`。

## 10.5 第四步：从键盘读输入

真实游戏用 `@std/process.spr` 的 `process.read_line()`：读下一行，去掉行尾换行；到输入末尾（EOF）时返回 `null`。这个程序和 10.4 的逻辑一样，只是把"清单"换成"一次读一行"：

<<< @/snippets/book/ch10_guess_interactive.spr

和 10.4 的版本逐点对照：

- `while true:` 一直循环；`read_line()` 返回 `null`（输入结束）时打印结果并 `return`，否则循环不会停。
- 读到的 `line` 是 `String?`。`toIntOrNull()` 之后照旧判断 `null`；输入 `oops` 时打印 `not a number: oops`，`continue`。
- 猜中时打印 `correct in 3 tries`；`tries` 只数有效的猜测。
- 秘密数字仍用 `seeded(7)`，所以书里每次运行结果一致；真正发布时改成 `fresh()`，每次都不一样。

`play_interactive` 的签名末尾多了 `throws Error`：`read_line` 可能失败，在函数里调用它的函数必须在签名里声明。完整规则在第 14 章，现在照写即可。

把程序存成 `guess.spr`，在终端里运行并喂给它输入：

```bash
printf '50\n10\noops\n21\n' | sprig run guess.spr
```

```text
I am thinking of a number between 1 and 100.
too high
too low
not a number: oops
correct in 3 tries
```

直接运行 `sprig run guess.spr` 也可以，程序会等你输入：每行一个猜测，最后用 Ctrl-D（macOS、Linux）或 Ctrl-Z 回车（Windows）表示"没有更多输入"。

::: tip 学过其他语言？
Python 的 `input()` 在输入结束时抛 `EOFError`，要写 `try` 接住；Sprig 把输入结束变成 `null`，用 `if line == null:` 判断。随机数和 Python 的 `random.seed(s)` 一样可复现，区别是 Sprig 把随机源拿在手里（`let dice = random.seeded(7)`），要接着用同一个；再调一次 `random.seeded(7)` 会从头开始。命令行参数不是全局的 `sys.argv`，而是 `process.arguments()`，并且只有 `--` 后面的词才会传进来。
:::

### 故意写错：忘了 `throws Error`

把函数头里的 `throws Error` 删掉，只留下最小的一段：

<<< @/snippets/book/ch10_throws_missing.spr

```text
SPR-FLOW-THROWS [FLOW] main.spr:4:16: Call may throw Error; declare 'throws Error' or handle it with try/catch
  hint: Declare it on 'ask' by changing its header to 'func ask() -> Unit throws Error:', and its callers then handle or declare it too (top-level statements need neither), or handle it where it happens: 'try:' around the call, then 'catch problem: Error:' with what to do instead. Sprig keeps recoverable errors explicit; there is no implicit propagation.
```

错误码 `SPR-FLOW-THROWS` 在说：`read_line()` 可能抛 `Error`，而 `ask` 既不处理（没有 `try`/`catch`）也不声明，编译器不让它悄悄过去。`hint` 直接给出改法：把函数头改成 `func ask() -> Unit throws Error:`。第 14 章专门讲错误处理，到时你会明白 `try`/`catch` 的两条路怎么选。

## 10.6 参数、标准错误和退出状态

**命令行参数。** `process.arguments()` 返回参数列表（`List[String]`）。运行程序时，`--` 后面的词会原样传进去：

<<< @/snippets/book/ch10_args.spr

```text
no arguments; run me as: sprig run main.spr -- Ada Bob
```

不带参数运行时 `args` 是空的，打印提示。带上参数：

```bash
sprig run main.spr -- Ada Bob
```

```text
hello Ada
hello Bob
```

**标准错误。** `process.print_error(text)` 写到标准错误，用来放"不是程序正常输出"的信息，比如提示和警告。程序正常的 `print` 写到标准输出：

<<< @/snippets/book/ch10_print_error.spr

```text
this line goes to standard output
```

只把标准输出收进管道时，上面这行是全部内容；在终端直接运行，你还会看到 `this line goes to standard error`。把两者混在一起（比如 `2>&1`）时，先后顺序不保证，别依赖它。

**退出状态。** `process.exit(status)` 立刻结束程序，`0` 表示成功，非 0 表示失败。通常不用自己调；需要在命令行脚本里报告失败时才用：

<<< @/snippets/book/ch10_exit.spr

```text
before exit
```

`print("this never prints")` 不会执行；`exit(0)` 之后的代码在运行时到不了。

## 本章小结

- `random.seeded(seed)` 可复现，`random.fresh()` 每次都不同；`next_int(n)` 给出 `0..n-1`。
- 先把输入写成参数（一份猜的清单），游戏逻辑就能自动测试；再换成 `process.read_line()` 读键盘。
- `read_line()` 返回 `String?`：`null` 表示输入结束。`toIntOrNull()` 同样用 `null` 表示"不是数字"。
- 函数里调用可能失败的东西，签名要声明 `throws Error`，否则是 `SPR-FLOW-THROWS`。
- `process.arguments()` 读 `--` 后面的参数；`process.print_error` 写标准错误；`process.exit(0)` 正常结束，非 0 是失败。

## 动手练习

**练习 1（热身）** 把秘密数字的种子改成 `42`，范围改成 1..10，打印出来。

提示：把 `seeded(7)` 和 `next_int(100)` 里的两个数改掉，`+ 1` 保留。

::: details 参考答案
<<< @/snippets/book/ch10_ex1_answer.spr

```text
secret is 2
```
:::

**练习 2** 输入清单用光时，除了秘密数字，也报告已经猜了几次。

提示：把 10.4 里最后那行 `out of guesses...` 改成带上 `tries`。

::: details 参考答案
<<< @/snippets/book/ch10_ex2_answer.spr

```text
too low
too high
out of guesses after 2 tries; the number was 21
```
:::

**练习 3** 拒绝 1..100 之外的猜测：打印提示，也不计入次数。

提示：在 `toIntOrNull` 之后、`tries += 1` 之前加一个 `if guess < 1 or guess > 100:` 分支。

::: details 参考答案
<<< @/snippets/book/ch10_ex3_answer.spr

```text
out of range: 0
out of range: 200
too low
correct in 2 tries
```
:::

**练习 4** 连续"开三局"：用种子 1、2、3 各生成一个 1..100 的秘密数字并打印。

提示：`for seed in [1, 2, 3]:`，循环里再 `seeded(seed)`。

::: details 参考答案
<<< @/snippets/book/ch10_ex4_answer.spr

```text
round 1: secret 97
round 2: secret 89
round 3: secret 77
```
:::

下一章开始"用类型描述世界"这部分：怎样定义自己的类：[类和对象](/tutorial/ch11-classes)。
