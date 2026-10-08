# 15. 函数作为值

这一章你会学到：

- 把一小段"要做什么"写成**函数值**（lambda），存进变量、当参数传、当返回值；
- 给函数值写类型，比如 `fn(Int) -> Int`；
- 具名函数、模块函数、对象方法怎么当值用，`print` 为什么只能在特定位置当值；
- lambda 的**捕获**：能读外面的 `let`，不能读函数里的 `var`，顶层 `var` 是模块状态；
- 会失败、可能为空的函数值：`throws Error`、`rethrows`、`(fn(Int) -> Int)?`；
- 用 `@std/lists` 里收函数值的工具：`sort_by`、`fold`、`any`、`all`、`find`、`count`。

## 15.1 为什么需要函数值

第 8 章的 `lists.sort` 只会按自然顺序排；到目前为止，函数的"行为"都是写死的。想让调用者决定"怎么排、留下哪些"，就得把一段行为本身当作值传进去。这种"值"就是**函数值**，用 `fn` 写出来，通常叫 **lambda**。

一个最小的例子：把"乘以 2"这段行为起个名字。

<<< @/snippets/book/ch15_lambda.spr

```text
42
```

逐行看：

- `fn(n: Int) => n * 2` 是一个函数值。`fn` 后面是参数表，参数要写类型；`=>` 后面是**一个表达式**，它的值就是函数的返回值。没有多行函数体：逻辑复杂就写具名函数，或者把 if 表达式绑到 `let`（15.4 会看到）。
- 它被赋给 `double`。从此 `double` 和普通变量一样，可以存、可以传。
- `double(21)` 像调用普通函数一样调用它，打印 `42`。

参数个数可以是 0 到 3 个：

```sprig
let greet = fn() => "hi"
let add = fn(a: Int, b: Int) => a + b
```

函数值的类型写成 `fn(参数类型) -> 返回类型`。上面两个分别是 `fn() -> String` 和 `fn(Int, Int) -> Int`。参数类型不能省略，编译器不会去猜：

### 故意写错：lambda 的参数不写类型

<<< @/snippets/book/ch15_untyped_param.spr

```text
SPR-SYNTAX-ERROR [SYNTAX] main.spr:1:15: mismatched input ')' expecting ':' (unexpected ')')
```

读这条报错：它甚至没到类型检查，词法/语法阶段就失败了——`fn(n)` 里的 `n` 后面必须有 `:` 和类型。`main.spr:1:15` 指向的就是那个不完整的参数。修法是 `fn(n: Int) => n + 1`。

## 15.2 把函数值当参数和返回值

函数值最有用的地方是交给别人。下面 `apply_twice` 接收一个 `fn(Int) -> Int`，把它对同一个值做两遍。

<<< @/snippets/book/ch15_fn_type.spr

```text
7
45
```

逐行看：

- `func apply_twice(value: Int, step: fn(Int) -> Int) -> Int`：第二个参数 `step` 的类型是"一个接收 `Int`、返回 `Int` 的函数"。`value` 是普通参数。
- `step(step(value))` 先算里面的：`step(5)` 是 `6`，再算 `step(6)` 是 `7`。
- 重复调用时：

  | 调用 | 第一步 | 第二步 | 打印 |
  |---|---|---|---|
  | `apply_twice(5, fn(n: Int) => n + 1)` | `5 + 1` = `6` | `6 + 1` = `7` | `7` |
  | `apply_twice(5, triple)` | `5 * 3` = `15` | `15 * 3` = `45` | `45` |

- 最后一行把具名函数 `triple` 直接传了进去，没有加括号。**一个具名函数的名字，不加括号，就是一个函数值**，和 `fn(n: Int) => triple(n)` 完全一样。加括号 `triple(5)` 则是"现在调用它，取结果"。

函数值也可以从函数里返回。下面的 `multiplier` 每次调用都生成一个新函数，把它出生时看到的值"记住"：

<<< @/snippets/book/ch15_return_fn.spr

```text
21
70
```

- `multiplier(3)` 返回的 `scale` 用到了参数 `factor`；参数从来不会变，所以 `scale` 可以安全地把它记在身体里。
- `triple(7)` 算 `7 * 3`，`tenfold(7)` 算 `7 * 10`。两个函数各自记住一个值，互不影响。这种"带着出生环境"的函数值，别的语言里叫**闭包**（closure）。

函数值不能用 `==` 比较：同一个名字的两次引用是两个不同的值，比较只会让人困惑。编译器会让你比较"函数算出来的东西"，或者比较一个选择函数的 enum/variant（第 12 章讲过）。

### 故意写错：比较两个函数值

<<< @/snippets/book/ch15_fn_compare.spr

```text
SPR-TYPE-OPERAND [TYPE] main.spr:3:7: Function values cannot be compared with '==' (expected comparable values, actual fn(Int) -> Int and fn(Int) -> Int)
  hint: Compare what the functions compute, or compare a name, enum or variant that chooses the function; a nullable function value may still be compared with null.
```

`a` 和 `b` 恰好会算出相同的结果，但它们是两个独立的值，Sprig 不比较它们。注意提示的最后一句话：可空的函数值可以写 `maybe == null`（15.7 会用到）。

## 15.3 具名函数、方法、模块函数都是值

不只 lambda 是值。具名函数、`@std/lists` 里的模块函数、对象的方法，名字后面不加括号，都是函数值：

<<< @/snippets/function_references.spr

```text
[PEAR!, FIG!]
6
2
5
5
pear
fig
[fig, pear]
```

逐行看：

- `words.map(shout)`：`shout` 是具名函数，`map` 要一个 `fn(String) -> String`，直接把名字给它。
- `let total: fn(List[Int]) -> Int = lists.sum`：模块函数 `lists.sum` 赋给一个带函数类型的 `let`。变量声明上的类型这时很有用：它告诉编译器"我要的是一个函数值"。
- `let bump = counter.bump`：**方法引用**。把方法从对象上"摘下来"时，接收者 `counter` 就被固定住了；之后每次 `bump(...)` 都作用在同一个 `counter` 上，所以 `count` 最后是 `5`。
- `words.forEach(print)`：`print` 能接收任何类型的值，所以它自己没有一个固定的类型；只有放到参数类型已知的位置（这里要 `fn(String) -> Unit`）才能当值。
- `lists.reversed[String]`：`reversed` 是泛型函数，要写出类型参数。为什么必须写、`[String]` 是什么意思，下一章讲。

### 故意写错：把 `print` 单独存起来

<<< @/snippets/book/ch15_print_value.spr

```text
SPR-TYPE-NOT-CALLABLE [TYPE] main.spr:1:9: print as a value needs the parameter type of the place it goes to
  hint: Pass print where a fn(T) -> Unit is expected, such as items.forEach(print), or write the lambda: fn(value: String) => print(value).
```

`let p = print` 这一行的右边没有"要去的地方"，编译器不知道 `T` 是什么，所以拒绝。提示给了两条路：要么在 `items.forEach(print)` 这种位置直接用，要么自己写 `fn(value: String) => print(value)`。

另外两条相关限制，见到不用惊讶：Java 方法和内置方法（`names.add`、`text.length`）也不能这样摘下来，写 lambda；报错是 `SPR-TYPE-NOT-CALLABLE`，提示里写着 `Built-in method 'length' is not a value`。

::: tip 学过其他语言？
`fn(n: Int) => n * 2` 对应 Python 的 `lambda n: n * 2`、JS 的 `n => n * 2`，但 Sprig 要求参数写类型；`...` 对应的"多行 lambda"不存在，编译器叫它 block lambda，明确不支持。函数类型 `fn(Int) -> Int` 类似 Java 的 `Function<Integer, Integer>`，但最多 3 个参数，而且没有装箱烦恼。
:::

## 15.4 lambda 体里的 if 表达式

`=>` 后面是一个表达式。第 5 章的 if 表达式就是一个表达式，可以放在那里，但每个分支要单独占一行，所以先把它绑到 `let`：

<<< @/snippets/book/ch11_if_lambda.spr

```text
[small, big, big]
[odd, even, odd]
```

- 第一个 `size_label` 是普通具名函数，用 `return if ...` 返回一个 if 表达式的结果。
- `parity` 把 if 表达式绑到 `let`，再把 `parity` 传给 `map`。`n % 2 == 0` 为真给 `"even"`，否则 `"odd"`。

### 故意写错：把多行 if 直接塞进 `map(...)`

<<< @/snippets/book/ch11_if_in_call.spr

```text
SPR-SYNTAX-ERROR [SYNTAX] main.spr:1:42: An if expression cannot be written inside parentheses, brackets or braces
  hint: Line breaks are ignored there, so the branches cannot go on their own lines. Bind the if expression, or the lambda that holds it, to a let first, then use the name.
```

括号里的换行会被忽略，而 if 表达式的分支必须各占一行——两者冲突，所以语法直接拒绝。照提示做：先把 lambda 整个绑到 `let`，再用名字（上面 `parity` 的例子），或者写一个具名函数。

## 15.5 捕获：lambda 能看到什么

lambda 除了自己的参数，还能读取外面作用域里已经有的名字。这叫**捕获**（capture）。捕获一个 `let` 是最常见的用法：

<<< @/snippets/book/ch15_capture_let.spr

```text
15
```

`add_base` 里没有 `base` 参数，它读到的是外面的 `let base = 10`；`add_base(5)` 就是 `5 + 10`。

**函数里的 `var` 不能被捕获**，因为 lambda 可能在以后、甚至在别的线程上被调用，那时这个会变的局部变量是什么值说不清：

### 故意写错：捕获函数的局部 `var`

<<< @/snippets/book/ch11_capture.spr

```text
SPR-TYPE-CAPTURE [TYPE] main.spr:3:16: Lambda captures mutable local 'counter'
  hint: Copy it into a 'let' binding before the lambda, or use a class field.
```

`counter` 是 `count_up` 的局部 `var`。两种修法都在提示里：如果调用时值已经定下来，先 `let fixed = counter` 再捕获 `fixed`；如果状态真的要跨调用保留，把它放进类的字段（第 11 章）。

顶层 `var` 是另一回事：它不是函数的局部变量，而是**模块级状态**。lambda 读到的是调用那一刻的值：

<<< @/snippets/book/ch15_capture_top.spr

```text
1
6
```

| 时刻 | `hits` | `peek()` 返回 | 打印 |
|---|---|---|---|
| 绑定 `peek` 时 | `0` | —— | —— |
| 第一次 `peek()` | `0` | `0 + 1` | `1` |
| `hits = 5` 之后 | `5` | `5 + 1` | `6` |

注意第二次不是 `2`：顶层 `var` 是共享的，`peek` 每次都读当前值。并发执行时多个任务会同时读写它，那时需要锁或专用计数器——第 22 章讲。

## 15.6 会失败的函数值

lambda 里调用了 `throws Error` 的函数，它自己的类型就带上 `throws Error`。接收这种函数值的地方要在类型里写出来，而 `rethrows` 让抛不抛由调用者决定：

<<< @/snippets/book/ch15_throws.spr

```text
21
caught: not a number: two
18
second: not a number: x
```

逐段看：

- `parse` 是第 14 章那种会失败的函数：`toIntOrNull()` 给出 `Int?`，不是数字就 `throw`。
- `let parser = fn(text: String) => parse(text)`：lambda 调用了会抛出的函数，所以 `parser` 的类型是 `fn(String) -> Int throws Error`。
- `twice` 的 `step` 参数写明了 `throws Error`；返回值后面写 `rethrows`，意思是"`twice` 抛不抛，取决于传进来的 `step`"。所以：
  - `twice(fn(n: Int) => n * 3, 2)` 传的是不会失败的 lambda，整行不用 `try`，打印 `18`（先 `2 * 3 = 6`，再 `6 * 3 = 18`）；
  - 传 `failing` 时 `parse("x")` 抛出，`try` 接住，打印 `second: not a number: x`。
- 第一个 `try` 里，`parser("21")` 先正常打印 `21`，`parser("two")` 抛出后被 `catch problem: Error` 接住。

反过来不成立：会抛出的函数值不能当作"不会抛出"来用。

### 故意写错：把会抛出的 lambda 传给不写 throws 的位置

<<< @/snippets/book/ch15_throws_mismatch.spr

```text
SPR-TYPE-CALLABLE-THROWS [TYPE] main.spr:10:13: A function value that may throw Error cannot be used as fn(Int) -> Int (argument 1 of twice) (expected fn(Int) -> Int, actual fn(Int) -> Int throws Error)
  hint: Declare the target as fn(Int) -> Int throws Error and make the receiving function rethrows or throws Error, or handle the error inside a named function.
```

读这条报错：`expected fn(Int) -> Int` 是 `twice` 要的，`actual fn(Int) -> Int throws Error` 是你给的——多出来的 `throws Error` 就是问题。根据提示改 `twice` 的签名（加上 `throws Error` 和 `rethrows`），或者把错误在 lambda 里处理掉。

::: tip 学过其他语言？
Java 的受检异常逼着每个调用者写 `throws`；Sprig 用函数类型里的 `throws Error` 表示同一件事，并且把它变成类型的一部分：`fn(A) -> R throws Error` 能传给 `fn(A) -> R`，反过来不行。`rethrows` 类似 Kotlin 的 `@Throws` 传播，但由编译器自动推断。目前只有 `Error` 能穿过函数值，Java 的受检异常留在具名函数里（第 21 章）。
:::

## 15.7 可空的函数值

函数值也可以为空，类型写 `(fn(Int) -> Int)?`（第 13 章的 `?` 用在这里）。用之前先判空：

<<< @/snippets/book/ch15_nullable_fn.spr

```text
3
true
```

- `maybe` 有值（这里就是 `inc`），`if maybe != null:` 之后编译器把它收窄成 `fn(Int) -> Int`，`maybe(2)` 合法，打印 `3`。
- `missing` 是 `null`，`missing == null` 是 `true`。可空函数值用 `==` 和 `null` 比较是允许的——这正是 15.2 里提示说的例外。

### 故意写错：不判空直接调用

<<< @/snippets/book/ch15_nullable_call.spr

```text
SPR-TYPE-NULLABLE [TYPE] main.spr:2:7: Cannot invoke a nullable function value; check for null first (expected fn(Int) -> Int, actual (fn(Int) -> Int)?)
```

`actual (fn(Int) -> Int)?` 里的问号就是问题。按提示先 `if missing != null:`，或者给它一个默认值（比如 `let f = if ...`）。这和可空数字、可空字符串是同一条规则。

## 15.8 用 lambda 武装 `@std/lists`

第 8 章已经用过 `map`、`filter`、`forEach`。`@std/lists` 里还有一批接收函数值的工具，现在都能读懂了：

<<< @/snippets/book/ch15_lists.spr

```text
[fig, pear, apple]
10
true
false
8
2
```

- `sort_by(items, key)`：按 `key` 函数算出的键排序，键相等时保持输入顺序（稳定排序）。三个词的长度是 `4, 3, 5`，所以 `fig` 在前。
- `fold(items, initial, step)`：从左到右合并。`0 + 1 + 2 + 3 + 4` 得 `10`；空列表会得到 `initial`。
- `any`：只要有一个满足就是 `true`（`5` 是奇数）；`all`：全部满足才是 `true`（`5` 不是偶数，所以 `false`）。
- `find`：第一个满足的值，找不到返回 `null`；这里打印 `8`。
- `count`：满足的个数，偶数有 `2` 和 `4`，共 `2`。

这些函数的 key/step/accept 参数的类型都写着 `throws Error`：如果传进去的 lambda 会失败，编译器会要求你在调用处处理或声明。这也解释了为什么 `find` 的返回类型是 `T?`——找不到时它没有值可给。

## 15.9 本章小结

- `fn(参数: 类型) => 表达式` 造一个函数值；类型写成 `fn(T) -> R`，参数 0 到 3 个。
- 函数值能存进 `let`、当参数、当返回值；具名函数、模块函数和方法不加括号就是值；`print` 只在参数类型已知的位置是值；函数值不能用 `==` 比较。
- lambda 的 `=>` 后面是一个表达式；多行 if 表达式要先绑到 `let`。
- 捕获：能读外面的 `let` 和参数，不能读函数里的 `var`；顶层 `var` 是模块状态，读到的是当前值。
- `throws Error` 是函数类型的一部分；`rethrows` 把"会不会抛"传给调用者。
- 可空函数值 `(fn(T) -> R)?` 先判空再调用。

## 15.10 动手练习

**练习 1（易）** 用 `filter` 和 lambda 从 `[1, 2, 3, 4, 5, 6]` 里挑出偶数。

提示：判断写成 `fn(n: Int) => n % 2 == 0`。

::: details 参考答案
<<< @/snippets/book/ch15_ex1.spr

```text
[2, 4, 6]
```
:::

**练习 2** 写一个具名函数 `half(n: Int) -> Int` 返回 `n.divTrunc(2)`，再用 15.2 的 `apply_twice` 把它作用两次到 `20` 上。

提示：具名函数不加括号就能当值传。

::: details 参考答案
<<< @/snippets/book/ch15_ex2.spr

```text
5
```
:::

**练习 3** 用 `lists.sort_by` 按长度排序 `["bb", "aa", "c", "dd"]`，注意长度相同的词保持原来的先后。

提示：键是 `fn(w: String) => w.length()`；稳定排序会留下 `bb, aa, dd` 的相对顺序。

::: details 参考答案
<<< @/snippets/book/ch15_ex3.spr

```text
[c, bb, aa, dd]
```
:::

**练习 4（难）** 写一个 `parse`（不是数字就抛出 `"bad: " + 文本`）和一个接收 `fn(Int) -> Int throws Error` 的 `twice`（用 `rethrows`）。分别用不会失败的 lambda 和会失败的 lambda 调用它，后者放在 `try` 里打印 `caught: ...`。

提示：不会失败的 lambda 不需要 `try`；会失败的那个直接在 lambda 体里调用 `parse("x")`。

::: details 参考答案
<<< @/snippets/book/ch15_ex4.spr

```text
21
caught: bad: x
```
:::

下一章：[泛型与契约类](/tutorial/ch16-generics-contracts)——让同一段代码服务多种类型，以及"只要求方法、不要求身份"的契约。
