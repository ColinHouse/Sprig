# 2. 第一个程序与读懂报错

这一章你会学到：

- 怎么写出第一个 Sprig 程序，怎么运行它；
- `print` 做什么，注释是什么；
- `sprig run` 和 `sprig check` 的区别；
- 一条报错由哪几部分组成，怎么读；
- 三种最常见的错误，以及 `sprig explain` 和 `sprig fmt` 怎么帮忙。

## 2.1 第一个程序

写程序就是往一个文件里写一段交给计算机做的话。新建一个文件，名字叫 `hello.spr`（扩展名 `.spr` 是 Sprig 程序的标志），内容如下：

<<< @/snippets/book/ch02_hello.spr

```text
Hello, Sprig
One file, two lines.
```

在终端里进入这个文件所在的目录，然后运行：

```bash
sprig run hello.spr
```

屏幕上就会打印上面那两行。逐行看一遍：

- `# 以 # 开头的整行都是注释，编译器会忽略它。`：**注释**是写给人看的话，编译器不看。`#` 后面的内容随你写，用中文也行。
- `print("Hello, Sprig")`：调用 `print`，把括号里那段文字打印出来，然后换行。`print` 是 Sprig 内置的函数。
- `"Hello, Sprig"`：一对双引号包起来的文字叫**字符串**（string），双引号本身不会被打印。
- 第二行 `print(...)` 打印下一行。

还有一件事：**没有 `main`**。文件里的顶层语句就是程序，从上到下按顺序执行。你没有写“开始”和“结束”，Sprig 从第一行跑到最后一行。

`print` 一次只接受一个参数。想要一行里打印两个东西，得先用 `+` 把它们拼成文本；写成 `print(1, 2)` 会被编译器拒绝，报错码是 `SPR-CALL-ARITY`（“参数个数不对”）。`+` 拼接的细节第 4 章讲。

### 注释

注释用来解释代码，给未来的你看。它有两种写法：

- 整行注释：`#` 开头，这一行只有注释。
- 行尾注释：写在代码后面，`#` 到行尾都是注释，例如 `print("hi")  # 打个招呼`。

注意 `#` 只有在字符串外面才是注释：`print("# 不是注释")` 打印的是 `# 不是注释` 整个字符串。

### 保存文件，运行文件

`sprig` 要能读到你的文件。在终端里用 `cd` 进入文件所在目录，再运行 `sprig run hello.spr`。第一次运行会慢一点，因为要先把程序编译成 Java；没改过的程序再次运行时不用重新编译，会快不少。

## 2.2 只检查，不运行

另一条命令你以后会天天用：

```bash
sprig check hello.spr
```

它只检查代码，不运行。没问题时它**什么都不打印**；有错时它一次列出所有错误，而且不会执行你的程序。写代码时先 `sprig check`，改到没有错再 `sprig run`。

## 2.3 报错由什么组成

现在故意写一个错。把 `hello.spr` 的内容换成下面这一行：

<<< @/snippets/book/ch02_type_error.spr

```text
SPR-NUM-MIXED [TYPE] main.spr:1:7: Operator '+' has no implicit conversion between Int and Float (expected matching numeric families, actual Int and Float)
  hint: Convert the Int side: 1.toFloatExact().
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

这里把文件叫 `main.spr` 来展示报错的样子；你自己的文件叫什么，报错里就显示什么名字。一条报错从左到右是这几部分：

| 部分 | 上面这一行里的内容 | 意思 |
|---|---|---|
| 错误码 | `SPR-NUM-MIXED` | 错误的固定编号，方便查资料；每一种错误码都有解释 |
| 类别 | `[TYPE]` | 出错的阶段：词法（LEX）、语法（SYNTAX）、类型（TYPE）等 |
| 位置 | `main.spr:1:7` | 文件、第几行、第几列（都从 1 开始数） |
| 说明 | `Operator '+' has no implicit conversion...` | 一句话说清哪里不对 |
| 提示 | `hint: Convert the Int side: 1.toFloatExact().` | 怎么改；不是每条报错都有 |

最后一行是总结：一共几个错误，到哪里查错误码。这本书里出现 `SPR-...` 的地方，你都可以在终端里运行 `sprig explain 错误码` 看完整解释。

这条报错说的是一件很“Sprig”的事：`1` 是整数，`2.0` 是小数，Sprig 不替你猜你想用哪一种，要求你先明确转换。为什么这样设计、怎么改，第 3 章讲。

### 故意写错：名字拼错了

<<< @/snippets/book/ch02_typo.spr

```text
SPR-NAME-UNRESOLVED [NAME] main.spr:1:1: Unresolved name 'pritn'
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

`pritn` 这个名字没有定义过。位置 `1:1` 指向这一行的开头，说明问题就出在这个名字上。这条没有 `hint:`，怎么办？把名字和你想用的那个比一比：`print` 的五笔顺序是 p-r-i-n-t。改过来就好了。

### 故意写错：少了一个右括号

<<< @/snippets/book/ch02_paren.spr

```text
SPR-LEX-UNCLOSED [LEX] main.spr:1:6: Unclosed grouping delimiter at end of file
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

`[LEX]` 说明错误发生在读字符的阶段：看到一个左括号 `(`，到文件结尾都没等到配对的右括号。`1:6` 是那个没被关上的括号的位置。补上 `)` 即可。

## 2.4 用 `sprig explain` 查错误码

遇到没见过的错误码，把它原样交给 `sprig explain`：

```bash
sprig explain SPR-NAME-UNRESOLVED
```

```text
SPR-NAME-UNRESOLVED: A name has no declaration in the current scope chain.
Why it matters: Every name is declared before use; there is no implicit global or dynamic lookup.
Common causes:
  - A typo, a missing import, or a declaration placed after first use.
  - A spelling from another language, such as readLine, input, len, str, True, None, self or the types int and str; the hint names the Sprig spelling.
  - A Java class such as Math or Scanner used without 'import java.lang.Math as Math'.
Safe fixes:
  - Check the spelling, add the import, or move the declaration before use.
  - Read standard input with @std/process.spr (read_lines, read_line, read_all).
```

它用一段话说清“为什么会发生”和“怎么修”。错误码不认识时先查这里，比搜索快。

## 2.5 用 `sprig fmt` 统一格式

缩进和空格歪掉不影响运行，但会让代码难读。`sprig fmt` 按统一风格重新排版一个文件，并且保留你的注释。比如文件里是：

```sprig
print(  "Hello, Sprig"  )
let   name="Ada"
print( name )
```

运行 `sprig fmt hello.spr`，命令会就地改写文件并打印一行 `Formatted ...`（`...` 是文件的完整路径）。再看文件，已经变成：

```sprig
print("Hello, Sprig")
let name = "Ada"
print(name)
```

被格式化过的程序输出不变。复习一下：`fmt` 改格式，`check` 找错误，`run` 运行。

::: tip 学过其他语言？
你可能习惯了“保存即运行”和一运行就报错。Sprig 是静态类型语言：`sprig check` 在运行前就能一次报出全部静态错误，而且不执行你的代码。报错格式固定为“错误码 + 阶段 + 位置 + 说明 + 提示”，错误码可以在 `sprig explain` 里查。写程序时把 `check` 当编译器用，把 `run` 当运行器用。
:::

## 本章小结

- 顶层语句就是程序，从上到下执行；没有 `main`。
- `print` 打印一个值并换行；字符串用双引号括起来。
- `#` 开头的整行是注释；行尾也可以写注释。
- `sprig run 文件.spr` 检查并运行；`sprig check 文件.spr` 只检查，成功时没有输出。
- 报错 = 错误码 + 类别 + `文件:行:列` + 说明 +（可能有）提示。
- `sprig explain 错误码` 看完整解释；`sprig fmt 文件` 统一格式。

## 动手练习

1. 写一个程序，打印你的名字和你的城市，一人一行。
   提示：两行文字用两次 `print`。

::: details 参考答案
<<< @/snippets/book/ch02_ex_lines.spr

```text
Ada
Amsterdam
```
:::

2. 下面这段程序有一个拼写错误，把它修好：

```sprig
prnit("hi")
```

提示：和本章用过的 `print` 一个字母一个字母地比。

::: details 参考答案
把 `prnit` 改成 `print`：

<<< @/snippets/book/ch02_ex_typo.spr

```text
hi
```
:::

3. 不运行程序，先猜一猜 `print("Oops)` 会报什么错，然后建一个文件运行 `sprig check` 验证。
   提示：数一数引号和括号，是不是都有一个“另一半”。

::: details 参考答案
字符串没有关上，括号也没有关上，所以有两条报错：

<<< @/snippets/book/ch02_ex_unclosed.spr

```text
SPR-LEX-STRING [LEX] main.spr:1:7: Unterminated string literal
SPR-LEX-UNCLOSED [LEX] main.spr:1:6: Unclosed grouping delimiter at end of file
2 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

修法：写成 `print("Oops")`。
:::

下一章开始玩数字，也回答这一章留下的问题：[第 3 章：值、变量和算术](/tutorial/ch03-values)。
