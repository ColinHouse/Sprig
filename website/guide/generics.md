# 泛型

Sprig 的泛型刻意做得很小：在 `generic` 块里声明类型参数，调用时编译器根据你传的实参算出类型参数，泛型没有协变和逆变，不同的泛型类型之间也不会自动转换。

先看一个完整的例子：

<<< @/snippets/generics.spr

```text
42
```

## 声明泛型

把类、variant 或函数放进 `generic` 块里：

```sprig
generic T:
    class Box:
        let value: T
```

- 一个块可以声明多个类型参数，比如 `generic K, V:`。参数名不能重复。
- 类型参数只在块里有效，在块外用 `T` 会报 `SPR-NAME-UNRESOLVED`。

## 类型参数

大多数时候不用写。调用会根据你传的实参得出类型参数：

```sprig
let box = Box(value=42)                    # Box[Int]
let value = identity("pear")               # identity[String]
let entry = Entry(key="age", value=18)     # Entry[String, Int]
let some = Option.Some(value=1)            # Option[Int].Some
let groups = lists.group_by(orders, fn(o: Order) => o.customer)   # group_by[Order, String]
```

你也随时可以自己写，写了就用你写的。要写就全写：写了一个，就得按声明的顺序把每个都写上。有些地方总是要写：

```sprig
let box = Box[Int](value=42)
let none = Option[Int].None                # 没有载荷的情况没有实参可看
let entries: List[Entry[String, Int]] = [] # 写在类型里的，总是要写
```

编译器是这样算的：

- 只看这次调用的实参。结果赋给什么类型、传给哪个参数，都不算数。
- 不带类型的数字只在别的实参都没说明类型时才算数。如果 `small` 是 `Int32`，`lists.sorted([small, 8])` 排的是 `Int32`，`8` 也跟着变成 `Int32`；单独写 `lists.sorted([1, 2])`，排的就是 `Int`。
- `null`、`[]` 和 `{}` 什么也说明不了。有元素的列表或 map 字面量按元素算。如果直接传给普通的 `T`，字面量就作为一个整体来算，而且只在别的实参都没说明 `T` 时才算数：单独写 `identity([1])` 得到 `MutableList[Int]`，和 `let xs = [1]` 一样；旁边有一个 `List[Int]` 的值时，这个字面量也会变成 `List[Int]`。
- lambda 的参数类型是写出来的，所以按写的算；它的函数体给出结果类型。
- 几个实参给出的类型不一样时，取其他类型都能放进去的那一个：`Int32` 和 `Int` 得到 `Int`，同一个 variant 的两种情况得到这个 variant。找不到这样的类型，调用就报错。
- Java 方法和 `ArrayList[String]` 这样的 Java 泛型类型，类型参数总是要写。

算出来以后，每个实参都会按算出的类型检查一遍，和你自己写出类型参数完全一样。实参说明不了的时候，编译器会停下来，请你写出来：

<<< @/snippets/guide/generics_missing_args.spr

```text
SPR-TYPE-GENERIC-ARGS-REQUIRED [TYPE] main.spr:3:22: Cannot infer type argument T of 'lists.first': argument 1 is an empty list, which says nothing about T
  hint: Write lists.first[Type](...) with T spelled out; type arguments are inferred only from a call's arguments, never from where its result goes.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

左边的 `String?` 帮不上忙，写成 `lists.first[String]([])` 就好。两个实参对不上的时候，这条错误会把两边都说出来，比如 `T is String from argument 1 but Bool from argument 2`。如果是实参的形状本身不对，比如该传函数的地方传了数字，报的就是普通的类型不匹配，因为这时写出类型参数也没用。

类型参数的个数写错了，会报 `SPR-TYPE-GENERIC-ARITY`。在类型的位置只写 `Box`、不带参数，或者给不是泛型的类型加了参数，都属于这种情况。

## 类型参数能做什么

在泛型声明里面，`T` 类型的值可以赋值、传参、返回，也可以放进兼容的泛型容器。但默认它没有运算符，也没有方法。`value + value` 会报 `SPR-TYPE-OPERAND`。

能额外开放的能力有两种，都写在函数体的最前面。第一种是相等比较：写上 `requires T: Equatable`，就可以对 `T` 用 `==` 和 `!=`，按值比较：

<<< @/snippets/guide/generics_equatable.spr

```text
true
false
```

第二种是比较大小：写上 `requires T: Comparable`，就可以对 `T` 用 `<`、`<=`、`>`、`>=`，也可以对 `MutableList[T]` 调用 `sort()`：

<<< @/snippets/guide/generics_comparable.spr

```text
9
pear
[0.5, 1.0, 2.5]
```

- 能比较大小的类型是 `Int`、`Int32`、`Float`、`Float32`、`Decimal`、`BigInt` 和 `String`，也就是本来就能用 `<` 的那些类型。另外，如果调用方自己的类型参数也写了 `requires X: Comparable`，也可以传进去。
- 比较的结果和直接比较具体类型完全一样，比如浮点数和 NaN 比较一律是 false。
- 每次调用都会检查，不管类型参数是你写的还是编译器算出来的。传了不能比较大小的类型，会在调用的地方报错：

<<< @/snippets/guide/generics_comparable_bool.spr

```text
SPR-GENERIC-CONSTRAINT [TYPE] main.spr:8:7: Type argument 'Bool' for T is not Comparable, which 'larger' requires (expected Comparable type, actual Bool)
  hint: Comparable types are Int, Int32, Float, Float32, Decimal, BigInt and String; for other types, pass an explicit comparison function.
```

两种能力互不包含：写了 `Comparable` 不代表能用 `==`，两样都要用就写两行 `requires`。除了这两种，写别的能力名会报 `SPR-GENERIC-CONSTRAINT`。

## 可空的类型参数

如果声明里直接存的是 `T`，那么 `Box[String?]` 没有问题。但如果声明自己已经写了 `T?`：

```sprig
generic T:
    class Box:
        let value: T?
```

再写 `Box[String?]` 就会报 `SPR-TYPE-GENERIC-NULLABLE`：这个位置可不可以为空，已经由声明决定了。写 `Box[String]` 就好。

编译器自己算类型参数时也守同样的规矩。`String?` 传给声明里写成 `T?` 的参数，`T` 就是 `String`，所以 `Box(value=maybe)` 是 `Box[String]`。传给普通的 `T`，`T` 就是 `String?`；但如果声明在别处写了 `T?`，`T` 就是 `String`，这个可能为空的实参会被报出来，和写明 `[String]` 时一样。

## 泛型 variant

variant 也可以是泛型的。有载荷的情况和构造函数一样，从载荷算出类型参数；没有载荷的情况，比如 `Option[Int].None`，要写出来。`match` 的分支里则只写情况的名字：

<<< @/snippets/guide/generics_option.spr

```text
some 1
none
```

`match` 照样要穷尽。以后给 `Option` 加了新的情况，所有漏掉它的 `match` 都会报 `SPR-MATCH-NONEXHAUSTIVE`。

## 方括号什么时候是下标

`values[index]` 仍然是下标访问，不会被当成类型参数。编译器看方括号前面的名字来判断：如果是一个泛型声明，方括号里就是类型参数，否则就是下标。另外，先取下标再调用（`handler[0](arg)`）目前不支持。

## 编译成 Java 以后

在生成的 Java 代码里，泛型会被擦除：类型参数变成 `Object`，泛型类在 JVM 上是原始类型，编译器会在需要的地方插入装箱和类型转换。具体规则见[泛型参考（英文）](/en/reference/language/generics)。

导入的 Java 类也能带具体的类型参数，比如 `ArrayList[String]`，见 [JVM 互操作](/guide/jvm-interop)。

## 还没有的

从期望的类型推断类型参数、协变和逆变，以及自定义的能力约束，目前都还没有实现。完整列表见[已知限制（英文）](/en/reference/language/known-limitations)。
