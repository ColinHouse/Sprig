# 12. 枚举、variant 和 match

这一章你会学到：

- 用 `enum` 表示“只能是固定几个名字之一”的值，比如方向、季节；
- 用 `variant` 给每种情况带上数据，比如“圆形有半径”；
- 用 `match` 一次处理所有情况，漏掉一个编译器会拦住；
- 表达式形式和语句形式的 `match` 有什么不同；
- 一个初学者常踩的陷阱：`var v = Shape.Dot` 的类型是 `Shape.Dot`，不是 `Shape`；
- 递归的 variant：自己包含自己，用来表示一棵树。

## 12.1 enum：一组固定的名字

一个值只能是固定几种之一，这在程序里太常见了：方向、星期、状态、命令的种类。用字符串表示会有拼写错误、大小写不一致、也没有人能列出全部取值。`enum` 把取值列出来：

<<< @/snippets/book/ch07_enum_match.spr

```text
East
North
```

- `enum Direction:` 声明一个枚举类型，下面缩进列出它的成员：`North`、`East`、`South`、`West`。用的时候写 `Direction.North`，永远不需要担心拼错——拼错名字编译器会报 `SPR-NAME-UNRESOLVED`。
- `turn_right(d: Direction)` 收一个方向，返回右转之后的方向。
- `return match d:` 是**match 表达式**：`match` 在需要值的位置（这里是 `return` 后面），每个 `case` 只有一行表达式，整个 `match` 的结果就是那个分支的值。
- 四次 `case Direction.North:` 这样的分支把所有可能都覆盖了，没有 `else`，也没有默认分支。
- 第一行输出 `East`：`Direction.North` 右转是 `East`。第二行输出 `North`：`Direction.West` 右转回到了 `North`。

`case` 分支里也能写多行语句，这时 `match` 是**语句**，值要靠各分支自己的 `return` 送出来。12.2 的 `area` 就是这样写的。

## 12.2 variant：带数据的情况

有些“几种之一”的每种还带着数据：一个形状要么是圆（带半径），要么是矩形（带宽高），要么是一个点。`variant` 的每种情况可以写字段：

<<< @/snippets/book/ch07_variant.spr

```text
3.14159
6.0
0.0
true
```

- `variant Shape:` 声明类型 `Shape`，三种情况：`Circle(radius: Float)`、`Rect(width: Float, height: Float)`、`Dot`。不带字段的情况（`Dot`）写法和 enum 成员一样。
- 构造带字段的情况要写字段名：`Shape.Circle(radius=1.0)`，和创建类对象一样。
- `case Shape.Circle as c:` 在匹配成功时把这一路的 `Shape` 值绑定到 `c`，分支里就能用 `c.radius`。不需要访问字段时可以省略 `as`，像 `case Shape.Dot:`。
- `as` 绑定的名字是只读的：在里面写 `c = ...` 会报 `SPR-NAME-LET-ASSIGN`，提示你用 `var` 声明（但这里通常不需要改，绑定只是给值起个名字）。
- `match shape:` 在函数体开头，是**语句**形式的 match；每个分支用 `return` 返回。三个分支分别算出 `3.14159`、`6.0`、`0.0`。
- 最后一行 `true`：variant 的值按**内容**比较。`Shape.Rect(width=2.0, height=3.0)` 和另一个同样字段的 `Rect` 相等。这和上一章的类正好相反——类比的是身份。
- 因为所有情况共享类型 `Shape`，它们能放进同一个列表 `List[Shape]`，也能作为参数传递。

::: tip 学过其他语言？
- `enum` 像其他语言的枚举；`variant` 像 Rust 的 enum、Swift 的关联值枚举、Kotlin 的 sealed class、TypeScript 的可辨识联合。
- 构造用字段名，和类的构造一致；Python 的 dataclass、Java record 也能表达其中一部分，但那个类型不是“封闭的几种之一”。
- 没有 `switch` 的穿透，没有 `default`；下一节会看到漏掉情况会怎样。
:::

## 12.3 match 必须穷尽

`match` 没有 `default`、`else` 或 `_` 这样的兜底分支：每一种情况都要写出来。漏掉一个，编译器报告缺哪一种：

<<< @/snippets/book/ch07_nonexhaustive.spr

```text
SPR-MATCH-NONEXHAUSTIVE [FLOW] main.spr:7:5: Missing case: Shape.Dot
  hint: Add 'case Shape.Dot:' (there is no default case)
```

- 错误码 `SPR-MATCH-NONEXHAUSTIVE`，位置是 `match` 语句那行；
- 消息直接点名缺的 `Shape.Dot`；
- 提示就是修法：加一行 `case Shape.Dot:`。

这条规则的价值在程序长大以后才显现：设想一个月后你给 `Shape` 加了一种 `Triangle`，程序里每一个忘了处理它的 `match` 都会在编译时被一个个指出来，而不是等到运行时。

也正因如此，`match` 只用于 enum 和 variant 这两类“你能列出全部情况”的类型，不能用来判断一个对象是不是某个类：Sprig 里没有类型判断。

## 12.4 故意写错：情况类型的陷阱

一个容易踩的坑：`Shape.Dot` 这个值本身的类型是 **`Shape.Dot`**（这一种情况），不是整个 `Shape`。编译器推断出的类型比你以为的更窄。

<<< @/snippets/book/ch12_case_trap.spr

```text
SPR-TYPE-ASSIGN [TYPE] main.spr:6:5: Type mismatch in assignment (expected Shape.Dot, actual Shape.Circle)
SPR-FLOW-UNREACHABLE [FLOW] main.spr:12:10: Case 'Shape.Dot' is impossible for statically known Shape.Circle
```

两个错误一起读：

- 第 6 行：`var v = Shape.Dot` 让 `v` 的类型成了 `Shape.Dot`，所以下一行赋值 `Shape.Circle(...)` 报“类型不匹配”，`expected Shape.Dot, actual Shape.Circle`。
- 第 12 行：`let s = Shape.Circle(radius=1.0)` 让 `s` 的类型成了 `Shape.Circle`。编译器知道 `s` 只可能是圆，于是 `case Shape.Dot:` 这条分支永远到不了，报 `SPR-FLOW-UNREACHABLE`。

修法很简单：**当变量以后可能要装别的情况时，把类型写成 `Shape`**。写上类型就像告诉编译器“我要的是整个 variant，不是这一种”：

<<< @/snippets/book/ch12_case_fixed.spr

```text
Circle(radius=1.0)
2.0
```

`var v: Shape = Shape.Dot` 之后，`v` 就能接着装 `Shape.Circle(...)`；`let s: Shape = Shape.Circle(radius=2.0)` 之后，`match` 写全两种分支也不再有“到不了”的抱怨。

没写类型时，让编译器推断本身没错——如果这个值确实永远是那一种，更精确的类型还更好。要留意的只是：**只构造一次、之后要换成别的情况的变量，写 `: Shape`**。

## 12.5 递归 variant：自己包含自己

variant 的字段类型可以是这个 variant 自己。这就能表示树、表达式、嵌套结构。下面是一个只有整数、加法、取负的表达式树：

<<< @/snippets/book/ch12_recursive.spr

```text
-1
7
```

- `Num(value: Int)` 是叶子；`Add(left: Expr, right: Expr)` 和 `Neg(value: Expr)` 的字段又可以是 `Expr`，所以树可以任意深。
- `eval(expr: Expr) -> Int` 把树算成一个整数。`case Expr.Add as a: return eval(a.left) + eval(a.right)` 对两棵子树递归，第 7 章的递归在这里再次出现。
- `let tree: Expr = ...` 手动搭了一棵树：2 + (-3)。
- 第一次 `print` 是 `-1`，第二次是 `7`。

跟踪 `eval(tree)` 的两层递归：

| 步骤 | 求值 | 结果 |
|---|---|---|
| 1 | `eval(Add(Num 2, Neg(Num 3)))` | `eval(Num 2) + eval(Neg(Num 3))` |
| 2 | `eval(Num 2)` | `2` |
| 3 | `eval(Neg(Num 3))` | `-eval(Num 3)` = `-3` |
| 4 | `2 + (-3)` | `-1` |

## 12.6 没有兜底，也没有守卫

两条你可能期待、但 Sprig 故意没有的写法：

**没有 `case _:`。** 通配分支会让“漏掉一种情况”重新变得可能：

<<< @/snippets/book/ch12_wildcard.spr

```text
SPR-MATCH-UNKNOWN-CASE [TYPE] main.spr:7:14: Match case must be written as Type.Case
  hint: match is for enums and variants; there are no type tests on class or contract values. A closed set of types is a variant (declare one with a case per type and match on it); an open set is a contract (call its methods instead of testing the type).
```

提示里出现了“契约类”，那是第 16 章的内容；现在只需要记住前半句：`case` 必须写成 `类型.情况`。

**没有 `if` 守卫。** 有些语言允许 `case Shape.Circle if c.radius > 1.0:`。Sprig 不允许，条件是写在分支体里面的普通 `if`：

<<< @/snippets/book/ch12_guard.spr

```text
SPR-SYNTAX-ERROR [SYNTAX] main.spr:7:30: A match case has no 'if' guard
  hint: Match the case, then put 'if condition:' inside the case's body.
```

也**没有多情况合并**：不能写 `case Shape.Circle, Shape.Dot:` 之类，每种情况各写一行。

## 本章小结

- `enum` 列名字，`variant` 列带字段的情况；构造用字段名，`as` 绑定只读。
- `match` 逐个处理，必须穷尽，没有兜底、没有守卫、没有类型判断。
- `match` 用在需要值的位置是表达式（每个分支一行）；当语句用时分支可以多行、自己 `return`。
- variant 按内容比较，并且能递归：字段类型可以是 variant 自己。
- 陷阱：单个情况的值类型是那一种情况，要装多种情况就写 `: Shape` 这样的类型。

## 动手练习

**练习 1（enum 与 match 表达式）。** 定义 `enum Season: Spring, Summer, Autumn, Winter`，写 `is_warm(s: Season) -> Bool`，用 match 表达式返回春夏为 `true`、秋冬为 `false`。

::: details 参考答案
<<< @/snippets/book/ch12_ex1.spr

```text
true
false
```
:::

**练习 2（带数据的 variant）。** 定义 `Temperature`，两种情况 `Celsius(degrees: Float)` 和 `Fahrenheit(degrees: Float)`，写 `to_celsius` 把两种都换算成摄氏度。验证 20°C 还是 20.0，212°F 是 100.0。

::: details 参考答案
<<< @/snippets/book/ch12_ex2.spr

```text
20.0
100.0
```
:::

**练习 3（补全 match）。** 下面的 `describe` 漏了一种情况，先看编译器报什么，再补上让程序打印 `dot`：`variant Shape` 有 `Circle(radius: Float)`、`Rect(width: Float, height: Float)`、`Dot`，`describe` 只处理了前两种。

::: details 参考答案
<<< @/snippets/book/ch12_ex3.spr

```text
dot
```
:::

**练习 4（递归 variant）。** 定义 `variant Tree: Leaf(value: Int), Node(left: Tree, right: Tree)`，写 `total(tree: Tree) -> Int` 求所有叶子的和。构造一棵三片叶子的树，和为 6。

::: details 参考答案
<<< @/snippets/book/ch12_ex4.spr

```text
6
```

`total` 对 `Node` 递归两棵子树，对 `Leaf` 返回 `value`；三片叶子 1 + 2 + 3。
:::

下一章：[可空值](/tutorial/ch13-nullable)。
