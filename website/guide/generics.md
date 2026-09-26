# 泛型（v0.8）

Sprig v0.8 引入用户自定义泛型，核心原则只有一条：**声明和使用都必须显式**。
没有类型推断，没有型变，也没有隐式转换。本页是可直接运行的导览；权威契约见
[泛型参考（英文）](/en/reference/GENERICS)。

<<< @/snippets/generics.spr

```text
42
```

## 声明

```sprig
generic T:
    class Box:
        let value: T
```

`generic T:` 包裹一个 class、variant、enum 或 function。v0.8 只暴露一个类型
参数（`generic T, E:` 是语法错误），并且 `T` 只在块内可见——在块外使用会得到
`SPR-NAME-UNRESOLVED`。

## 使用处必须写出 `[Type]`

```sprig
let box = Box[Int](value=42)          # 泛型构造
let value = identity[Int](42)          # 泛型函数
let some: Option[Int] = Option[Int].Some(value=1)
let none: Option[Int] = Option[Int].None
```

`identity(42)` 或 `Box(value=42)` 缺少类型参数时会报
`SPR-TYPE-GENERIC-ARGS-REQUIRED`：编译器不会替你猜。数量错误（包括类型位置
里裸写 `Box`，或给非泛型类型加参数）报 `SPR-TYPE-GENERIC-ARITY`。

## 裸 `T` 能做什么

在泛型声明内部，`T` 可以赋值、传参、返回，也可以放进兼容的泛型容器。但它
**没有运算符、排序、相等比较和方法**：`value + value` 会得到
`SPR-TYPE-OPERAND`。`requires T: Comparable` 子句会被解析，但能力蕴含尚未实现，
因此每个子句都会报 `SPR-GENERIC-CONSTRAINT`。

## 可空类型参数

当声明直接存放 `T` 时，`Box[String?]` 合法；如果声明自己对 `T` 应用了 `?`：

```sprig
generic T:
    class Box:
        let value: T?
```

那么 `Box[String?]` 会以 `SPR-TYPE-GENERIC-NULLABLE` 被拒绝——可空位置已由声明
拥有；`Box[String]` 正常。

## 泛型 variant

```sprig
generic T:
    variant Option:
        Some:
            value: T
        None:
```

用 `Option[Int].Some(value=1)` 与 `Option[Int].None` 构造；`match` 仍然穷尽，
对具体实例化同样成立。新增 case 时，漏掉它的 match 依旧报
`SPR-MATCH-NONEXHAUSTIVE`。

## 索引仍然有效

`values[index]` 是索引，不是类型应用。编译器根据符号种类判定：方括号的基名
是泛型声明时按类型应用处理，否则单个简单名称按索引处理。`handler[0](arg)`
（先索引再调用）不是 v0.8 的合法形式。

## JVM 实现

生成的 Java 中泛型被擦除并装箱：类型参数变为 `Object`，泛型类在 JVM 层是原始
类，编译器在参数位置装箱、在结果位置插入转换与拆箱。完整规则见
[泛型契约（英文）](/en/reference/GENERICS)。

## v0.8 尚未包含

多类型参数、类型推断、型变、能力蕴含语义，以及项目/依赖系统。见
[已知限制](/reference/known-limitations)。
