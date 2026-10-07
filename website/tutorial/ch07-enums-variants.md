# 7. 枚举、variant 和 match

一个值只能是固定几种之一，这在程序里太常见了：方向、类别、命令的种类、解析结果是成功还是失败。Sprig 用 `enum` 和 `variant` 描述这样的值，用 `match` 处理它们。

## 7.1 enum

<<< @/snippets/book/ch07_enum_match.spr

```text
East
North
```

- `enum` 列出几个不带数据的名字，用 `Direction.North` 引用。
- `match 值:` 下面每个 `case` 处理一种情况。这里 `match` 用作表达式，每个分支一行表达式，整个 `match` 的值被 `return` 出去。
- 作为语句用时，分支里可以写多行，像下一个例子那样。

## 7.2 variant：带数据的情况

<<< @/snippets/book/ch07_variant.spr

```text
3.14159
6.0
0.0
true
```

- `variant` 的每种情况可以带字段：`Circle(radius: Float)`。不带字段的情况（`Dot`）写法和 `enum` 一样。
- 构造时写字段名：`Shape.Circle(radius=1.0)`。
- `case Shape.Circle as c:` 在匹配成功时把值绑定到 `c`，分支里就能用 `c.radius`。不需要字段时可以省略 `as`。
- variant 的值按**内容**比较：两个 `Rect(width=2.0, height=3.0)` 相等。这和第 6 章的类正好相反。

一个 `variant` 的所有情况共享同一个类型 `Shape`，所以能放进 `List[Shape]`，能作为参数传递。

## 7.3 match 必须穷尽

### 故意写错：漏了一种情况

<<< @/snippets/book/ch07_nonexhaustive.spr

```text
SPR-MATCH-NONEXHAUSTIVE [FLOW] main.spr:7:5: Missing case: Shape.Dot
  hint: Add 'case Shape.Dot:' (there is no default case)
```

`match` 没有 `default`、`else` 或 `_` 这样的兜底分支，每一种情况都要写出来。这条规则的价值要到程序长大以后才显现：设想一个月后你给 `Shape` 加了一种 `Triangle`，程序里每一个忘了处理它的 `match` 都会在编译时被一个个指出来，而不是等到运行时才掉进某个兜底分支。

## 7.4 用 variant 代替继承

别的语言里，"形状有圆、矩形、点三种，各自算面积"会写成一个基类和三个子类。Sprig 的写法就是上面的 `Shape` 和 `area`：数据的几种形状在一处列全，每个操作是一个 `match`。

两种写法各有擅长。继承让"加一种形状"容易（新建一个子类即可），variant 让"加一个操作"容易（新写一个函数即可），并且加形状时编译器会告诉你每个需要补的地方。Sprig 选择了后者，也因此不提供继承。

## 7.5 match 的几个细节

- 可以 `match` 一个 `enum`、一个 `variant`。
- 表达式形式的 `match` 每个分支只有一行表达式；要多行就用语句形式，每个分支里自己 `return`。
- 分支体里绑定的名字（`as c`）是 `let`，不可改。

## 小结

- `enum` 列名字，`variant` 列带数据的情况，构造用字段名。
- `match` 逐个处理，`as` 绑定数据，没有兜底分支。
- variant 按内容比较；它是 Sprig 代替继承层次的工具。

下一章：[可空值](/tutorial/ch08-nullable)，Sprig 和大多数语言差别最大的一章。
