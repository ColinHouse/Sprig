# 10. 泛型

`List[Int]` 和 `List[String]` 是同一个 `List` 对不同类型的实例。这一章讲怎么自己写这样的类和函数。

## 10.1 generic 块

<<< @/snippets/book/ch10_generics.spr

```text
42
x
3
none
9
pear
```

- `generic T:` 开一个块，块里的类或函数可以用类型参数 `T`。一个块可以有多个参数：`generic K, V:`。
- `Box(value=42)` 不写类型参数，`T` 从参数推断为 `Int`。写出来也行：`Box[String](value="x")`，写了就以写的为准，而且要么全写要么全不写。
- 推断**只看调用的参数**，不看赋值目标或期望的返回类型。`first_or([], "none")` 能推出 `String`，是因为第二个参数说了；只有 `[]` 的话编译器会要求你写出类型。

## 10.2 T 上能做什么

一个裸的 `T` 什么都不知道：不能比较大小，不能调方法，不能做算术。需要能力就得声明：

### 故意写错：对 T 用 >

<<< @/snippets/book/ch10_constraint.spr

```text
SPR-TYPE-OPERAND [TYPE] main.spr:5:16: Operator '>' is not available for generic type parameter T; this operation needs a supported leading capability
  hint: Use a concrete type, or begin the function with 'requires T: Comparable' for ordering.
```

修法在上面 `largest` 的正确版本里：函数体的**第一条语句**写 `requires T: Comparable`。目前有两种能力：

| requires | 允许的操作 | 满足的类型 |
|---|---|---|
| `T: Equatable` | `==`、`!=` | 所有类型 |
| `T: Comparable` | `<`、`<=`、`>`、`>=`、排序 | `Int`、`Int32`、`Float`、`String` 等数值和文本类型 |

`largest(["pear", "apple"])` 成立，因为 `String` 是 `Comparable`；传一个 `List[Expense]` 进去就会在调用处被拒绝。

## 10.3 几条规则

- 泛型类型是**不变的**：`Box[MutableList[Int]]` 不能当作 `Box[List[Int]]`。第 5 章说的"只有最外层能切到只读视角"是这条规则的体现。
- 类型参数只在 `generic` 块内可见。
- 泛型 variant 也可以：`Option.Some(value=1)` 这样的写法见 `sprig help generics`。
- Java 的泛型类（比如 `ArrayList[String]`）总是要写出类型参数，第 13 章再说。

泛型比大多数语言的要克制：没有高阶类型，没有自定义的能力（trait/interface），能力只有上面两种。这是有意的，等到有足够的真实需求再扩展。更完整的规则见[泛型指南](/guide/generics)。

## 小结

- `generic T:` 块里定义泛型类和函数，调用时类型参数通常由参数推断。
- 裸 `T` 没有任何操作；`requires T: Comparable` / `Equatable` 作为函数第一条语句声明需要的能力。
- 泛型类型不变。

下一章：[函数作为值](/tutorial/ch11-functions-as-values)。
