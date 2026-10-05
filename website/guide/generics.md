# 泛型

Sprig 的泛型只有一条规矩：**声明时写清楚，使用时也写清楚。** 编译器不推断类型参数，泛型没有协变和逆变，不同的泛型类型之间也不会自动转换。

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

## 使用时写出类型参数

```sprig
let box = Box[Int](value=42)          # 泛型类
let value = identity[Int](42)          # 泛型函数
let entry = Entry[String, Int](key="age", value=18)
let some: Option[Int] = Option[Int].Some(value=1)
let none: Option[Int] = Option[Int].None
```

忘了写类型参数，编译器不会替你猜：

<<< @/snippets/guide/generics_missing_args.spr

```text
SPR-TYPE-GENERIC-ARGS-REQUIRED [TYPE] main.spr:5:7: Function 'identity' is generic; a call requires explicit type arguments, e.g. identity[Type](...)
SPR-TYPE-MISMATCH [TYPE] main.spr:5:16: Type mismatch in argument 1 of identity (expected T, actual Int)
2 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

第二条错误是第一条连带出来的，改成 `identity[Int](42)` 以后两条都会消失。

类型参数的个数写错了，会报 `SPR-TYPE-GENERIC-ARITY`。在类型的位置只写 `Box`、不带参数，或者给不是泛型的类型加了参数，都属于这种情况。

## 类型参数能做什么

在泛型声明里面，`T` 类型的值可以赋值、传参、返回，也可以放进兼容的泛型容器。但它没有运算符，不能排序，也没有方法。`value + value` 会报 `SPR-TYPE-OPERAND`。

目前唯一能额外开放的能力是相等比较。在函数开头写上 `requires T: Equatable`，就可以对 `T` 用 `==` 和 `!=`，按值比较：

<<< @/snippets/guide/generics_equatable.spr

```text
true
false
```

`requires` 要写在函数体的最前面。`requires T: Comparable` 写出来不会有语法错误，但功能还没有实现，会报 `SPR-GENERIC-CONSTRAINT`。

## 可空的类型参数

如果声明里直接存的是 `T`，那么 `Box[String?]` 没有问题。但如果声明自己已经写了 `T?`：

```sprig
generic T:
    class Box:
        let value: T?
```

再写 `Box[String?]` 就会报 `SPR-TYPE-GENERIC-NULLABLE`：这个位置可不可以为空，已经由声明决定了。写 `Box[String]` 就好。

## 泛型 variant

variant 也可以是泛型的。创建值时写出类型参数，`match` 的分支里则只写情况的名字：

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

类型推断、协变和逆变、`Comparable` 以及自定义的能力约束，目前都还没有实现。完整列表见[已知限制（英文）](/en/reference/language/known-limitations)。
