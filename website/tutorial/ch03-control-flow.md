# 3. 控制流

if、循环，以及 Sprig 特有的一样东西：作为值的 if。

## 3.1 if / elif / else

<<< @/snippets/book/ch03_if.spr

```text
freezing
cool
warm
```

- 分支以冒号结尾，分支体缩进四个空格。缩进就是块结构，没有花括号。
- "否则如果"拼作 `elif`。
- 条件必须是 `Bool`（上一章讲过）。

### 故意写错：else if

<<< @/snippets/book/ch03_else_if.spr

```text
SPR-SYNTAX-ERROR [SYNTAX] main.spr:4:6: Sprig spells else-if as 'elif'
  hint: Write 'elif condition:' in place of 'else if condition:'.
```

这是从别的语言过来时最常犯的错，所以编译器专门认出了它，而不是报一个泛泛的语法错误。

## 3.2 作为值的 if

`if` 除了当语句，还可以当表达式，直接产生一个值：

<<< @/snippets/book/ch03_if_expression.spr

```text
some
0
```

规则和语句形式的差别只有两条：

- 每个分支是**一行表达式**，不是语句块。
- `else` 必须写。既然 `if` 要产生一个值，就不能有"没有值"的情况。

所有分支的类型要一致，`size` 的三个分支都是 `String`。这种写法替代了很多语言里的三目运算符 `? :`，Sprig 没有后者。

把 `let size = if ...` 写成 `var size = ""` 再在每个分支里赋值也行，但前者让 `size` 可以是 `let`。第 8 章会解释为什么 `let` 更划算。

## 3.3 循环

<<< @/snippets/book/ch03_loops.spr

```text
10
-2
tea
rice
8
```

- `for x in 集合:` 遍历列表、映射的键、或者 `range` 产生的整数序列。`range(1, 5)` 是 1 到 4（不含 5），`range(100)` 是 0 到 99。
- `while 条件:` 在条件为真时反复执行。
- `break` 跳出循环，`continue` 跳到下一轮。
- 循环变量（上面的 `n`、`word`）在循环体里是不可改的，就像 `let`。

没有 `do ... while`，也没有 C 风格的 `for (i = 0; i < n; i++)`：数数就用 `range`，条件循环就用 `while`。

## 3.4 match

`match` 按值的形状分情况处理，主要和第 7 章的枚举、variant 搭配使用，所以留到那里细讲。这里先记住一句话：`match` 必须覆盖每一种情况，没有"其他"兜底分支。

## 小结

- `if` / `elif` / `else`，缩进成块，条件是 `Bool`。
- `if` 也可以是表达式：每个分支一行，必须有 `else`。
- `for ... in` 配合 `range`、`while`、`break`、`continue`。

下一章：[函数](/tutorial/ch04-functions)。
