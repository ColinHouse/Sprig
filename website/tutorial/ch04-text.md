# 4. 文字

这一章你会学到：

- 字符串是什么，怎么拼接；
- 转义字符：`\n`、`\t`、`\"`、`\\`；
- 常用的字符串方法；
- “字符”按什么单位数（码点）；
- 文字和数字之间怎么互相转换；
- 第一次 `import`：标准库 `@std/text.spr`。

## 4.1 字符串

一对双引号包起来的文字叫**字符串**（string），类型是 `String`。字符串里可以是字母、数字、空格、中文、表情，任意 Unicode 文本。看一组例子：

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

逐行解释：

- `item + " x " + count`：`+` 的任意一边是 `String` 时就是**拼接**，另一边的值会自动变成文字。所以 `3` 不用手动转换，直接进字符串。
- `"length " + item.length()`：`length()` 是字符串的方法，返回长度 3，再拼进文字。
- `item.toUpperCase()` 把 `tea` 变成 `TEA`；`item.substring(0, 1)` 从下标 0 开始取到下标 1（**不含 1**），得到 `"t"`。
- `"a,b,c".split(",")` 按逗号拆开，打印成 `[a, b, c]`。这是一个**列表**（List），第 8 章专门讲；现在只要知道字符串能拆成多个部分。
- `"  padded  ".trim()` 去掉两头的空格。
- `"é😀".length()` 是 2：é 和 😀 各算一个“字符”。`[1]` 取出第二个，也就是 😀。数的单位下一节细说。

`+` 拼接是从左到右算的，混合数字时会有一个小陷阱，4.3 节讲。

## 4.2 转义：把特殊字符写进字符串

有些字符没法直接敲进字符串里：换行、Tab、双引号自己、反斜杠。用**反斜杠加一个字母**表示它们：

<<< @/snippets/book/ch04_escapes.spr

```text
first line
second line
tab	between
a "quote" inside
a backslash: \
```

| 写法 | 含义 |
|---|---|
| `\n` | 换行（newline） |
| `\t` | 制表符（Tab） |
| `\"` | 一个双引号（不然会提前结束字符串） |
| `\\` | 一个反斜杠 |

注意第一行 `print("first line\nsecond line")` 只调用了一次 `print`，但因为字符串里有 `\n`，输出占了两行。

Sprig 的转义就这么几个：**没有 `\u` 这种 Unicode 转义**，也**不能写跨行的字符串**。字符串必须在同一行里用双引号关上。想要多行文字，就用 `\n` 拼起来。

## 4.3 拼接和它的陷阱

拼接本身很简单，看看这个：

<<< @/snippets/book/ch04_concat.spr

```text
Hello, Ada
cups: 3
3 then 12
```

- `"Hello, " + name`：文字加变量，得到 `Hello, Ada`。
- `"cups: " + cups`：数字 `3` 直接拼进去，变成文字 `cups: 3`。这就是前面说的“另一边的值自动变成文字”。
- `1 + 2 + " then " + 1 + 2` 打印的是 `3 then 12`，不是 `1 2 then 1 2`，也不是 `3 then 3`。为什么？`+` 从左往右算：

| 步骤 | 表达式 | 结果 |
|---|---|---|
| 1 | `1 + 2` | `3`（两个 Int，先做加法） |
| 2 | `3 + " then "` | `"3 then "`（遇到 String，改成拼接） |
| 3 | `"3 then " + 1` | `"3 then 1"` |
| 4 | `"3 then 1" + 2` | `"3 then 12"`（还是拼接） |

想要 `3 then 3`，就得把后两个数字先用括号加起来：`1 + 2 + " then " + (1 + 2)`。**没有字符串插值语法**：没有 `f"..."`，也没有 `${...}`，拼接就是唯一的写法。

## 4.4 常用方法

下面这张“全家福”把常用的字符串方法排了一遍：

<<< @/snippets/book/ch04_methods.spr

```text
false
true
e
101
7
10
true
true
Hello, worldHello, world
Hello, Sprig
43
5.0
7!
```

逐个对：

| 表达式 | 结果 | 说明 |
|---|---|---|
| `s.isEmpty()` | `false` | 是不是空字符串；`"".isEmpty()` 是 `true` |
| `s.charAt(1)` | `e` | 下标 1 处的一个字符（返回的还是 `String`） |
| `s.codeAt(1)` | `101` | 下标 1 处字符的码点（`e` 是 101） |
| `s.indexOf("world")` | `7` | 子串第一次出现的下标；没有就是 -1 |
| `s.lastIndexOf("l")` | `10` | 最后一次出现的下标 |
| `s.endsWith("world")` | `true` | 是不是以它结尾 |
| `s.contains("lo, w")` | `true` | 是不是包含它 |
| `s.repeat(2)` | `Hello, worldHello, world` | 重复两遍 |
| `s.replace("world", "Sprig")` | `Hello, Sprig` | 替换 |
| `"  42  ".trim().toInt() + 1` | `43` | 去空格，转成整数，再加 1 |
| `"2.5".toFloat() * 2.0` | `5.0` | 转成小数，再乘 |
| `7.toString() + "!"` | `7!` | 任何值都能 `toString()` 变成文字（数字拼进字符串时其实自动做了这件事） |

还有两个**静态**写法，不是“变量的方法”，而是直接写在类型 `String` 后面：

- `String.join(["a", "b", "c"], "-")` 得到 `a-b-c`：用分隔符拼接一组文字。
- `String.fromCode(65)` 得到 `A`：把码点变成字符。

### 故意写错：方法名写错了

<<< @/snippets/book/ch04_method_typo.spr

```text
SPR-NAME-UNRESOLVED [NAME] main.spr:1:7: Type String has no method 'toUppercase'
  hint: String methods: length, isEmpty, charAt, codeAt, substring, indexOf, contains, startsWith, endsWith, compareTo, toUpperCase, toLowerCase, trim, split, replace, repeat, lastIndexOf, toInt, toIntOrNull, toFloat, toString.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

错在大小写：是 `toUpperCase()`，U 和 C 都要大写。这条报错的提示干脆把所有字符串方法列了出来，照着表找正确拼写就行。大小写敏感是 Sprig 名字的通用规则。

::: tip 学过其他语言？
**方法名用驼峰**（`toUpperCase`、`lastIndexOf`），这是 Sprig 里唯一用驼峰的地方：你自己声明的函数、变量用下划线（`read_line`），类型和枚举值用大驼峰（`String`、`Decimal`），只有内置方法和 Java 成员用驼峰。另外，Sprig 没有 `Char` 类型：`charAt`、下标、迭代产生的“字符”都是长度为 1 的 `String`。
:::

## 4.5 “字符”是码点

`"é😀".length()` 是 2 而不是 3，因为 Sprig 的 `length`、下标、`charAt`、`codeAt`、`substring`、`indexOf` 都按 **Unicode 码点**（code point）数，不按字节，也不按 Java 的 UTF-16 单元。一个码点是一个“字符”的编号，比如 `A` 是 65，😀 是 128512。

<<< @/snippets/book/ch04_codepoints.spr

```text
3
😀
😀
128512
😀
2
A
```

- `"A😀東".length()` 是 3：A、😀、東 各一个码点。
- `[1]`、`charAt(1)` 都取到 😀。`substring(1, 2)` 取下标 1 到 2（不含 2），还是 😀。
- `codeAt(1)` 得到 128512，是 😀 的码点。
- `indexOf("東")` 是 2：按码点数，東 在第三个位置（下标 2）。
- `String.fromCode(65)` 把码点 65 变回 `A`。

这不等于“数人类的字”。比如带重音的组合字符、某些 emoji 家族（👨‍👩‍👧）会由多个码点组成，Sprig 不按“视觉上的一个字”切分。

## 4.6 在文字里找东西：`in`

`in` 是一个运算符，判断“一段文字在不在另一段文字里”，结果还是 `Bool`：

<<< @/snippets/book/ch04_contains.spr

```text
true
false
```

`"ell" in "hello"` 是 `true`，`"z" in "hello"` 是 `false`。和 `s.contains("ell")` 一个意思，两种写法随你挑（`in` 以后也会用在列表和映射上）。

## 4.7 文字和数字互转

字符串里的数字和真正的数字是两回事。想计算就得先转换：

<<< @/snippets/book/ch04_convert.spr

```text
50
12
null
3.0
```

- `"42".toInt()` 得到整数 42，加 8 得 50。`toInt()` 要求整段文字**就是**一个整数；像 `"abc"` 这样的文本会让程序在运行时停下，报 `SPR-RUNTIME-ERROR`，消息是 `Uncaught Error: not an integer: "abc"`。
- `"12".toIntOrNull()` 得到 12；`"abc".toIntOrNull()` 得到 `null`（没有值）。它不报错，把“转不了”的情况用 `null` 表示。它的类型是 `Int?`——可能没有值的 `Int`，第 13 章专门讲。
- `"2.5".toFloat()` 得到 2.5，加 0.5 得 3.0。

`"abc".toIntOrNull()` 打印出 `null`，这是唯一一个你暂时不用懂的类型谜题；先记住 `toIntOrNull` 是“转得成就给我值，转不成给 null”。

## 4.8 第一次 `import`：标准库

Sprig 自带一批标准模块，用 `import` 引进当前文件，起个小名，再用小名调用：

<<< @/snippets/book/ch02_text.spr

```text
0.67
007
ab..|
a-b-c
```

- `import "@std/text.spr" as text`：把标准库的 `text` 模块引进来，别名是 `text`。`import` 要写在文件的最上面，所有声明之前。
- `text.fixed(2.0 / 3.0, 2)`：把 `Float` 按指定小数位数四舍五入成文字，得到 `0.67`。
- `text.pad_left("7", 3, "0")`：在左边用 `"0"` 补到宽度 3，得 `007`。
- `text.pad_right("ab", 4, ".")`：右边补点，得 `ab..`，再拼一个 `|` 看清位置。
- `text.join(["a", "b", "c"], "-")`：用 `-` 拼一组文字，得 `a-b-c`。

`@std/text.spr` 里还有 `lines`、`starts_with`、`ends_with`、`strip_prefix`、`strip_suffix`、`is_ascii_digit`、`is_ascii_letter`、`escape_html`、`trim`、`split` 等。想知道全部，可以运行：

```bash
sprig api @std/text.spr
```

它会列出每个函数的签名和一句说明。别着急，常用的那几个用熟了再来看。

## 本章小结

- 字符串是双引号包起来的 Unicode 文字；`+` 遇到字符串就拼接，其他值自动变文字。
- 拼接从左往右算：`1 + 2 + " then " + 1 + 2` 是 `3 then 12`；没有字符串插值。
- 转义只有 `\n`、`\t`、`\"`、`\\` 这些；不能写跨行字符串。
- 常用方法：`length`、`isEmpty`、`charAt`、`codeAt`、`substring`（结尾不含）、`indexOf`、`lastIndexOf`、`contains`、`startsWith`、`endsWith`、`toUpperCase`、`toLowerCase`、`trim`、`split`、`replace`、`repeat`、`toString`。
- 静态写法 `String.join(list, sep)`、`String.fromCode(code)`。
- 长度和下标按 Unicode 码点数；“字符”是长度为 1 的 `String`，没有 `Char` 类型。
- `"a" in "abc"` 判断子串；文字转数字用 `toInt` / `toIntOrNull` / `toFloat`。
- `import "@std/text.spr" as text` 引进标准库。

## 动手练习

1. 用变量存一个名字，打印一句打招呼的话，再用 `\n` 输出第二行。
   提示：`"Hello, " + name + "!"`；`\n` 写在字符串里面。

::: details 参考答案
<<< @/snippets/book/ch04_ex_greeting.spr

```text
Hello, Ada!
Line one
Line two
```
:::

2. 把 `"red,green,blue"` 按逗号拆开并打印。
   提示：用 `.split(",")`。

::: details 参考答案
<<< @/snippets/book/ch04_ex_split.spr

```text
[red, green, blue]
```

`[...]` 是一个列表，第 8 章讲。
:::

3. 把文字 `"101"` 变成整数，加 1 后打印；再把 `"nope"` 转成整数试试不报错的写法。
   提示：会报错的那个是 `toInt()`，不报错的是 `toIntOrNull()`。

::: details 参考答案
<<< @/snippets/book/ch04_ex_convert.spr

```text
102
null
```
:::

4. 判断 `"cat"` 和 `"dog"` 分别在不在 `"concatenate"` 里。
   提示：用 `in` 运算符。

::: details 参考答案
<<< @/snippets/book/ch04_ex_in.spr

```text
true
false
```
:::

5. 从 `"I love Sprig!"` 里取出 `"Sprig"`（用 `substring`，记住结尾不含）。
   提示：下标从 0 开始数，`"I love "` 占 7 个码点。

::: details 参考答案
<<< @/snippets/book/ch04_ex_substring.spr

```text
Sprig
```

`I love ` 是 7 个字符，所以从下标 7 取到 12。
:::

下一章开始做决定：[第 5 章：做决定：if](/tutorial/ch05-if)。
