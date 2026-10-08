# 5. 做决定：if

到第 4 章为止，程序都只有一条路：从上往下，每一行都执行。真实世界里的程序要会挑路：冷了就穿外套，分数及格就升级，会员就打折。这一章讲 Sprig 怎么选。

这一章你会学到：

- 用 `if`、`elif`、`else` 让程序在几条路里选一条；
- 代码块用缩进表示，而且只能用空格，不能用 Tab；
- `else if` 为什么会被编译器专门拒绝；
- 把 `if` 当成一个能产生值的表达式。

## 5.1 如果……就做

最简单的选择：条件成立，就执行一块代码。

<<< @/snippets/book/ch05_if_basic.spr

```text
wear a t-shirt
go outside
```

逐行看：

- `let temperature = 30` 把 30 绑定到名字 `temperature` 上（第 3 章讲过）。
- `if temperature > 25:` 是「如果」。`temperature > 25` 是一个 `Bool` 表达式，叫**条件**；行尾的 `:` 表示「下面缩进的那一整块都属于我」。
- `print("wear a t-shirt")` 缩进了 4 个空格，它在 `if` 的**块**里。条件为 `true`（30 确实大于 25），所以这行执行了。
- `print("go outside")` 没有缩进，不在块里，所以它总会执行。把 30 改成 10，第一行不再打印，第二行照旧。

缩进就是结构。大括号 `{}` 和行尾的分号在这里都不存在，也别写。

## 5.2 两条路：else

条件不成立时想走另一条路，就加 `else`。

<<< @/snippets/book/ch05_if_else.spr

```text
not yet
```

`age` 是 15，`age >= 18` 为 `false`，所以 `if` 的块被跳过，`else` 的块执行，打印 `not yet`。`else` 自己不带条件，它和 `if` 对齐在同一列写。这两块永远只会执行一块。

## 5.3 多条路：elif

三条以上的路用 `elif`（else-if 的缩写）接下去。

<<< @/snippets/book/ch05_if_elif.spr

```text
freezing
cool
warm
```

程序把同一个判断跑了三遍，每次先换一下 `celsius` 的值。因为要改这个值，第 1 行用的是 `var` 而不是 `let`。

- `celsius = -5`：`-5 < 0` 为真，打印 `freezing`，后面的分支全部跳过。
- `celsius = 12`：第一个条件为假，继续看 `12 < 20`，为真，打印 `cool`。
- `celsius = 30`：前两个条件都为假，落到 `else`，打印 `warm`。

从上往下检查，命中一个就离开整条链，所以最多只有一个分支执行。`else` 可有可无，但一定放在最后。三遍几乎一样的代码只是数据不同——第 6 章的循环会把这种重复消掉。

## 5.4 缩进的规矩：空格，不是 Tab

块用缩进表示，缩进的选择很严格：**只能用空格**。按 Tab 键，哪怕看起来和空格一样宽，编译器也会拒绝。下面这段把第 3 行的缩进打成了 Tab：

<<< @/snippets/book/ch05_tab.spr

```text
SPR-LEX-TAB [LEX] main.spr:3:1: Tabs are not allowed for indentation or inline whitespace
  hint: Sprig code blocks use spaces only; replace the tab with spaces.
SPR-SYNTAX-ERROR [SYNTAX] main.spr:3:2: Expected an indented block after 'if ...:'
```

这样读这份报错：

- 第一行最重要。`main.spr:3:1` 是位置：文件 `main.spr` 的第 3 行第 1 列。`SPR-LEX-TAB` 是错误码，告诉你这是词法阶段的 Tab 错误；`sprig explain SPR-LEX-TAB` 有完整解释。
- 提示行 `hint:` 直接给了修法：把 Tab 换成空格。编辑器里设成「用空格代替 Tab」最省事。
- 第二行是第一个错误的后果：Tab 被拒绝后，解析器就没看见 `if` 下面的缩进块。先修第一个，这个通常跟着消失。

约定俗成每层缩进 4 个空格，本章的例子都这样写。同一个块里的每一行必须对齐；不同块用不同宽度编译器也接受（比如一个块 2 个空格、另一个块 4 个），但全书写法保持一致才好看。

## 5.5 故意写错：else if

从 Java、JavaScript 或 C 过来的人，第二行条件几乎一定会写成 `else if`。Sprig 的拼写是 `elif`：

<<< @/snippets/book/ch03_else_if.spr

```text
SPR-SYNTAX-ERROR [SYNTAX] main.spr:4:6: Sprig spells else-if as 'elif'
  hint: Write 'elif condition:' in place of 'else if condition:'.
```

`4:6` 指向写错的 `else` 的位置。编译器认得这个从别的语言带来的习惯，没有报泛泛的语法错误，而是直接告诉你正确拼写——照着提示把 `else if` 换成 `elif` 就好。

## 5.6 把 if 当成值

有时你要的不是「执行哪块代码」，而是「选出哪个值」，比如根据数量挑一个尺寸。`if` 也能放在 `=` 右边，直接把选中的值交给变量：

<<< @/snippets/book/ch05_if_expr.spr

```text
some
```

这里 `if` 是一个**表达式**：整个结构产生一个值，`size` 拿到的是 `"some"`。规则只有几条：

- 每个分支写**一个表达式**，单独一行缩进，不是一块语句；这个例子的分支分别是 `"many"`、`"some"`、`"none"`。
- `elif`、`else` 和 `if` 对齐；`else` **必须写**，因为表达式总得有个值。
- 所有分支产生的值类型要一样，这里都是 `String`。
- 它只能出现在**一个值该出现的位置**：`=` 右边、`return` 后面（第 7 章）、`throw` 后面（第 14 章）等。行首单独的 `if` 仍然是语句，它的 `else` 才是可选的。

很多语言用 `条件 ? a : b` 这个三目运算符；Sprig 没有，if 表达式就是它。

## 5.7 故意写错：把 if 表达式塞进调用里

既然 if 表达式是个值，很容易顺手写进 `print(...)`：

<<< @/snippets/book/ch05_if_expr_in_call.spr

```text
SPR-SYNTAX-ERROR [SYNTAX] main.spr:2:7: An if expression cannot be written inside parentheses, brackets or braces
  hint: Line breaks are ignored there, so the branches cannot go on their own lines. Bind the if expression, or the lambda that holds it, to a let first, then use the name.
```

原因就在提示里：括号、方括号、大括号里面换行会被忽略，而 if 表达式的每个分支必须占自己的一行。先把结果绑到名字上，再把名字传进去：

<<< @/snippets/book/ch05_if_expr_fix.spr

```text
yes
```

## 本章小结

- `if 条件:` 开一条路，条件必须是 `Bool`。
- `elif` 接着检查下一条路，`else` 兜底；最多执行一个分支。
- 块靠缩进（4 个空格），只能用空格，不能用 Tab。
- `else if` 要写成 `elif`。
- if 表达式放在 `=` 右边直接产生值：分支各占一行、类型一致、`else` 必须写。
- 行首单独的 `if` 是语句，`else` 可选；`if` 语句不能塞进括号当参数。

## 动手练习

### 练习 1：正、负还是零

`n` 是 `-7`。打印 `positive`、`negative` 或 `zero` 中正确的一个。

提示：用一条 `if` / `elif` / `else` 链判断 `n > 0`、`n < 0`。

::: details 参考答案

<<< @/snippets/book/ch05_ex_sign.spr

```text
negative
```

:::

### 练习 2：成绩等级

分数 `score` 是 85，按下面规则打印等级：90 及以上打印 `A`，80 及以上 `B`，60 及以上 `C`，其余 `F`。

提示：分支要从高到低检查；先问 `score >= 90`，再问 `score >= 80`。

::: details 参考答案

<<< @/snippets/book/ch05_ex_grade.spr

```text
B
```

:::

### 练习 3：背包尺寸

水瓶数量 `bottles` 是 5：超过 9 个打印 `large`，超过 3 个打印 `medium`，否则 `small`。这次用 if 表达式，一次写好 `let size = ...`，再 `print(size)`。

提示：分支里各放一个字符串，`else` 写上 `"small"`。

::: details 参考答案

<<< @/snippets/book/ch05_ex_if_expr.spr

```text
medium
```

:::

### 练习 4：会员折扣

`is_member` 是 `true`，消费总额 `total` 是 120。规则：不是会员打印 `no discount`；是会员且总额满 100 打印 `20% off`，是会员但不满 100 打印 `10% off`。

提示：外层 `if is_member:` 判断会员身份，内层再判断 `total >= 100`——块里面还可以有块，内层再多缩进 4 个空格。

::: details 参考答案

<<< @/snippets/book/ch05_ex_nested.spr

```text
20% off
```

:::

::: tip 学过其他语言？

- Python：冒号、缩进、`elif` 都和 Sprig 一样；但条件必须真的是 `Bool`，`if 0:` 或 `if "":` 不会通过编译。Python 的 `a if 条件 else b` 在 Sprig 里写成多行的 if 表达式。
- Java / JavaScript / C：没有 `else if`，用 `elif`；没有 `?:` 三目运算符；花括号和分号都不写，块靠缩进。
- 嵌套太深会难读。第 7 章的「提前 return」能把多层嵌套压平。

:::

下一章：[第 6 章：重复：循环](/tutorial/ch06-loops)。
