# 19. 测试

改一行代码，手动跑一遍程序，用眼睛看输出对不对——三轮以后人就会烦，烦躁就会漏看。测试把"我期望它这样"写成程序，让机器每次替你核对。第 18 章见过 `tests/` 目录，这一章把它讲全。

这一章你会学到：

- `@std/test` 的四件工具：`check`、`check_error`、`equal_*` 和 `finish`；
- 一个测试就是普通程序，正常结束就算通过；
- `sprig test` 一次运行 `tests/` 下的所有测试，`--filter` 只挑名字匹配的；
- `tests/compile_fail/` 给"编译器应该拒绝这种写法"写测试；
- 为什么测试导入做事的模块，永远不要导入 `main.spr`。

## 19.1 一个测试就是一个程序

先看最小的测试文件，用 `sprig run` 直接运行就能看到每项检查的结果：

<<< @/snippets/book/ch19_checks.spr

```text
ok parses a number
ok rejects
ok rejects text
3 passed, 0 failed
```

逐行读：

- `import "@std/test.spr" as testing`：测试工具在标准库模块 `@std/test.spr` 里，和平常一样起别名导入。
- `testing.check("parses a number", fn() => ...)`：注册并立即运行一项检查。第一个参数是这项检查的名字；第二个参数是一个函数，里面的代码正常返回就算通过。
- `testing.equal_int(parse_count("42"), 42, "parse")`：比较两个整数，不相等就抛出 `Error`，消息里写明两边是什么。`check` 接住这个错误，打印失败原因。
- `testing.check_error("rejects", fn() => parse_count("abc"))`：反过来断言"这段代码**应该**失败"。`parse_count("abc")` 抛了 `Error`，所以这条检查通过。
- `testing.finish()`：打印 `N passed, N failed`，有失败时以退出码 1 结束程序，让 `sprig test` 知道你失败了。
- 输出里每条通过的检查打印一行 `ok 名字`，最后是总数。

里面的 `parse_count` 是"被测代码"：把文本转成整数，转不了就 `throw`。`toIntOrNull()` 第 4 章讲过：返回 `Int?`，`null` 表示这段文本不是整数。

### 失败长什么样

`equal` 失败时抛出的 `Error` 带着两个值。可以像普通错误一样接住，看看它到底写了什么：

<<< @/snippets/book/ch19_failure.spr

```text
greeting: expected "Hi, Ada!", got "Hello, Ada!"
sum: expected 4, got 3
```

消息的格式是 `说明: expected 期望值, got 实际值`。`equal_text` 会把两边都加上引号、把换行和制表符转义，所以行尾多了个空格、Windows 的 `\r\n` 和 Unix 的 `\n` 这类看不见的差别也能一眼认出来。`equal_int`、`equal_bool`、`equal_text` 和 `equal`（任何支持 `==` 的值，包括列表和映射）是一个系列，用法相同。

不接住的话，失败会打印出来并让程序退出：

```text
$ sprig test --filter failing
FAIL failing.spr
ok two plus two
FAIL greeting: greeting: expected "Hi, Ada!", got "Hello, Ada!"
1 passed, 1 failed
  Program exited with status 1

0 passed, 1 failed
```

这段输出来自一个测试文件，它的第一条检查通过，第二条把 `"Hello, Ada!"` 和 `"Hi, Ada!"` 比反了。读法：

- `FAIL failing.spr`：这个测试文件没有通过；
- `ok two plus two`：第一条检查通过；
- `FAIL greeting: ...`：第二条检查失败，`greeting` 是传给 `equal_text` 的说明，后面是两个值；
- `1 passed, 1 failed`：测试文件自己的统计，来自 `finish()`；
- `Program exited with status 1`：文件以失败状态退出；
- 最后一行 `0 passed, 1 failed` 是 `sprig test` 对所有测试文件的统计：一个文件，零个通过。

`check` 失败不会中断后面的检查——这两条检查都跑了。这正是 `check` 的意义：一次运行里把你写下的检查全试一遍，而不是遇到第一个错误就停。

## 19.2 项目里的测试：sprig test

真实的测试放在项目的 `tests/` 目录里。布局和三个文件：

```text
greeter/
├── sprig.toml
├── src/
│   ├── main.spr
│   └── app.spr
└── tests/
    ├── app_test.spr
    └── compile_fail/
        └── not_int.spr
```

`app.spr` 是真正做事的模块，只有函数：

<<< @/snippets/book/ch19_testing/src/app.spr

`main.spr` 只处理参数，把工作交给 `app.run`：

<<< @/snippets/book/ch19_testing/src/main.spr

`tests/app_test.spr` 导入 `app.spr`，直接调用它的函数：

<<< @/snippets/book/ch19_testing/tests/app_test.spr

```text
ok greets
usage: greet NAME
ok needs a name
2 passed, 0 failed
```

中间那行 `usage: greet NAME` 不是测试框架打印的，是 `app.run([])` 自己打印的——传了空参数列表，它当然抱怨。测试文件用 `sprig run` 直接跑时，你能看到每项检查；交给 `sprig test` 时，它只报告整个文件通过与否：

```text
$ sprig test
PASS app_test.spr
PASS compile_fail/not_int.spr
PASS tempdir.spr

3 passed, 0 failed
```

- `sprig test` 运行 `tests/` 下所有 `.spr` 文件（包括子目录）；每个文件在自己的 JVM 里跑；
- `PASS 文件名` 表示这个文件正常结束；
- `sprig test --filter app` 只跑路径里包含 `app` 的文件：

```text
$ sprig test --filter app
PASS app_test.spr

1 passed, 0 failed
```

`--filter` 匹配的是**文件名或路径**，不是检查名：`--filter greets` 不会因为检查名叫 `greets` 而跑任何文件。`sprig test --json` 输出机器可读的结果，给以后要用的工具；`sprig test` 和项目里的其他命令一样，需要一份最新的 `sprig.lock`（先 `sprig resolve`）。

### 每个测试文件的临时目录

测试经常要写文件，又不该弄脏你的目录。`testing.temp_dir()` 返回 `sprig test` 为这个测试文件准备的临时目录，文件跑完就被删除。这里的一个测试先写文件再读回来：

<<< @/snippets/book/ch19_testing/tests/tempdir.spr

它出现在上面 `sprig test` 输出的第三行 `PASS tempdir.spr`。注意 `temp_dir()` 只在 `sprig test` 里能用：直接 `sprig run` 这个文件会因为拿不到测试环境而报错。

## 19.3 故意写错：测试导入 main.spr

一个测试文件如果这样开头：

```sprig
import "../src/main.spr" as main
```

会发生什么？回忆 18.1：导入一个模块会执行它的顶层语句。`main.spr` 的顶层语句就是整个程序——它读参数、跑 `app.run`、调用 `process.exit`。于是测试还没开始，程序先执行了一遍：

```text
$ sprig test --filter wrong_way
FAIL wrong_way.spr
usage: greet NAME
  Program exited with status 2

0 passed, 1 failed
```

读法：`main.spr` 拿到空的参数列表，打印 `usage: greet NAME`，返回 2；`process.exit(2)` 直接把进程结束掉。测试文件里写的检查一项都没跑（连 `ok never runs` 都没有），`sprig test` 只看到进程以状态 2 退出，算这个文件失败。如果程序恰好以 0 退出，`sprig test` 甚至可能显示通过——检查其实从未运行。

规矩很简单：`main.spr` 只留着参数处理那几行，真正的工作放进另一个模块（这里是 `app.spr`），`main.spr` 和测试都导入它。

## 19.4 给"应该编译不过"写测试

有些保证不是关于运行时行为，而是关于编译器：比如"把 `null` 赋给 `Int` 必须被拒绝"。这类文件放在 `tests/compile_fail/`，每个配一个同名的 `.expect.toml`，列出预期的错误码。

`tests/compile_fail/not_int.spr`：

<<< @/snippets/book/ch19_testing/tests/compile_fail/not_int.spr

```text
SPR-TYPE-NULL [TYPE] main.spr:2:14: null is not assignable to Int (initializer); use Int? (expected Int, actual null)
```

`tests/compile_fail/not_int.expect.toml`：

```toml
codes = ["SPR-TYPE-NULL"]
```

`expect.toml` 不需要写全整段报错，只要错误码。检查时编译器真的编译这个文件，看它是否按预期失败、错误码是否对得上。这个文件在项目测试里就是刚才 `sprig test` 输出第二行的 `PASS compile_fail/not_int.spr`：它"通过"的含义是"确实编译失败了，而且失败得和期望一致"。

如果哪天有人放松了编译器，这行检查会变成 `FAIL`——这就是这类测试的价值。

## 本章小结

- 测试是普通程序：`check`/`check_error` 注册检查，`equal_int`、`equal_bool`、`equal_text`、`equal` 做比较，`finish()` 打总数并决定退出码。
- 通过时 `sprig run` 打印 `ok 名字` 和总计；`sprig test` 只打印每个文件的 `PASS`/`FAIL`。
- `--filter` 按文件路径筛选；测试需要最新的 `sprig.lock`。
- `testing.temp_dir()` 是测试专用的临时目录，只在 `sprig test` 里可用。
- 测试导入做事的模块，不导入 `main.spr`。
- `tests/compile_fail/` 加 `.expect.toml`：把"编译器应该拒绝"也变成测试。

## 动手练习

### 练习 1：写第一条通过的检查

写一个测试文件：函数 `double(x)` 返回 `x * 2`，用 `check` 和 `equal_int` 验证 `double(3)` 是 `6`，最后 `finish()`。

提示：三点都要有——导入、检查、`finish()`；不写 `finish()` 就看不到统计。

::: details 参考答案

<<< @/snippets/book/ch19_ex1.spr

```text
ok doubles 3
1 passed, 0 failed
```

:::

### 练习 2：断言"这种情况必须失败"

给函数 `parse_count(text)` 写一条 `check_error`：输入空字符串时它必须抛错。

提示：`check_error` 的函数体返回什么都可以，只要抛出 `Error` 就算通过。

::: details 参考答案

<<< @/snippets/book/ch19_ex2.spr

```text
ok rejects empty
1 passed, 0 failed
```

:::

### 练习 3：读懂失败消息

调用 `testing.equal_text(greet("Ada"), "Hi, Ada!", "greeting")`，其中 `greet` 返回 `"Hello, Ada!"`。用 `try`/`catch` 接住它，打印 `problem.message`，看看消息里怎么区分期望值和实际值。

提示：消息格式是 `说明: expected 期望, got 实际`。

::: details 参考答案

<<< @/snippets/book/ch19_ex3.spr

```text
greeting: expected "Hi, Ada!", got "Hello, Ada!"
```

:::

下一章：[标准库实用篇](/tutorial/ch20-stdlib)。
