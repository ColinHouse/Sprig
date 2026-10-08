# 16. 泛型与契约类

这一章你会学到：

- 为什么需要**泛型**：让同一个类或函数服务多种类型，而不是把代码抄几遍；
- `generic T:` 块、类型参数、从参数**推断**类型，以及什么时候必须把 `[T]` 写出来；
- 裸 `T` 为什么什么都不能做，`requires T: Comparable` / `Equatable` 是怎么回事；
- 泛型 variant：自己写一个 `Option[T]`；
- **契约类**：只列方法、没有函数体，`conform` 让一个类"实现"它；
- 什么时候用 variant、什么时候用契约。

## 16.1 为什么需要泛型

一个"盒子"类，装 `Int` 和装 `String` 是两个不同的类型：

```sprig
class IntBox:
    let value: Int

class StringBox:
    let value: String
```

只有字段类型不同，其余一模一样。每多一种元素类型就要抄一遍，改一处漏一处。**泛型**把"元素类型"变成参数：

<<< @/snippets/book/ch16_generics.spr

```text
42
x
age:18
```

逐行看：

- `generic T:` 开一个块，块里**一个**声明（类、variant 或函数）可以用类型参数 `T`。`T` 只是一个名字，代表"调用者选的某个类型"。
- `class Box: let value: T`：Box 的字段类型是 `T`。
- `generic K, V:` 可以有两个或更多参数；`Entry` 的键是 `K`、值是 `V`，想表达"键值对"就很自然。
- `Box(value=42)` 没有写 `[Int]`。编译器**从参数推断**：`42` 是 `Int`，所以 `T = Int`，`box` 是 `Box[Int]`。调用泛型函数 `identity` 也一样。
- `Box[String](value="x")` 把类型参数写了出来，以写的为准。**要么全写、要么全不写**：`generic K, V` 的 `Entry` 不能只写一半。
- 推断**只看调用的参数**，从不看"这个值要去哪里"。赋值目标、`return` 类型、外层调用的期望都不参与。类型位置上（比如 `let box: Box[Int]`）则必须写出来。

| 调用 | 推断出的类型参数 | 结果类型 |
|---|---|---|
| `Box(value=42)` | `T = Int` | `Box[Int]` |
| `Box[String](value="x")` | 写明 `T = String` | `Box[String]` |
| `Entry(key="age", value=18)` | `K = String, V = Int` | `Entry[String, Int]` |

推断不出来的情况很常见：`[]`、`{}`、`null` 都"什么也不说"。

### 故意写错：两个空列表说不出 `T` 是什么

<<< @/snippets/book/ch16_inference_error.spr

```text
SPR-TYPE-GENERIC-ARGS-REQUIRED [TYPE] main.spr:5:7: Cannot infer type argument T of 'pair': argument 1 is an empty list, which says nothing about T
  hint: Write pair[Type](...) with T spelled out; type arguments are inferred only from a call's arguments, never from where its result goes.
```

两个参数都是 `[]`，谁也说不出 `T`。报错名字直译就是"泛型参数必须写出来"，提示给出修法：`pair[Int]([], [])`（`Int` 换成你想要的类型）。如果旁边有一个 `List[Int]` 的值，普通列表字面量会跟着它变成 `List[Int]`，那种情况不用写。

### 故意写错：类型参数写多了一个

<<< @/snippets/book/ch16_arity_error.spr

```text
SPR-TYPE-GENERIC-ARITY [TYPE] main.spr:5:10: Type 'Box' requires exactly 1 type argument but got 2
```

`Box` 只声明了一个 `T`，却给了 `[String, Int]`。读报错时先看"requires exactly N"这个数字，再去 `generic` 那一行数参数。

::: tip 学过其他语言？
`generic T: class Box` 类似 Java 的 `class Box<T>`、C# 的 `class Box<T>`；用法上也像，但 Sprig 的推断更克制：从不看期望类型（没有 Java 8 的 target typing），而且泛型是**不变**的——`Box[MutableList[Int]]` 不能当 `Box[List[Int]]` 用，`List[Int]` 和 `List[String]` 之间没有子类型关系。
:::

## 16.2 裸 `T` 能做什么

答案是：几乎什么都不能。泛型代码对 `T` 一无所知，所以 `+`、`>`、调方法都不允许：

```sprig
generic T:
    func largest(items: List[T]) -> T:
        var best = items[0]
        for item in items:
            if item > best:
                best = item
        return best
```

这段代码的问题是 `item > best`。编译器不会猜 `T` 有没有顺序，需要你**声明能力**：

<<< @/snippets/book/ch16_constraints.spr

```text
9
pear
true
false
```

- `largest` 的函数体第一行是 `requires T: Comparable`。从这一行起，`T` 才能用 `<`、`<=`、`>`、`>=`。少了它，上面的代码就是编译错误。
- `contains` 用 `requires T: Equatable`，从此 `==` 和 `!=` 可用；它找得到 `2`（`true`），找不到 `"c"`（`false`）。
- 能力有两种，互相独立：要同时比较大小和相等，两条 `requires` 都写。
- `requires` 必须是函数体的**第一条语句**，不能夹在中间。
- 能力在**每次调用**时检查：`largest(["pear", "apple"])` 可以，因为 `String` 是 `Comparable`；`largest([true, false])` 就不行。

| 能力 | 允许 | 满足的类型 |
|---|---|---|
| `Equatable` | `==`、`!=` | 所有类型（类按身份，variant 按内容，和平时一样） |
| `Comparable` | `<`、`<=`、`>`、`>=`、排序 | `Int`、`Int32`、`Float`、`Float32`、`Decimal`、`BigInt`、`String` |

### 故意写错：忘了写 `requires`

<<< @/snippets/book/ch10_constraint.spr

```text
SPR-TYPE-OPERAND [TYPE] main.spr:5:16: Operator '>' is not available for generic type parameter T; this operation needs a supported leading capability
  hint: Use a concrete type, or begin the function with 'requires T: Comparable' for ordering.
```

报错说得很清楚：`>` 对裸的 `T` 不可用，缺的是 `Comparable`；修法就是把它写成函数体第一行。

### 故意写错：传进去的类型没有这个能力

<<< @/snippets/guide/generics_comparable_bool.spr

```text
SPR-GENERIC-CONSTRAINT [TYPE] main.spr:8:7: Type argument 'Bool' for T is not Comparable, which 'larger' requires (expected Comparable type, actual Bool)
  hint: Comparable types are Int, Int32, Float, Float32, Decimal, BigInt and String; for other types, pass an explicit comparison function.
```

函数声明得没问题，问题在**调用**：`Bool` 不在 Comparable 名单里。提示列出了全部有顺序的类型，并给出退路——传一个显式的比较函数（第 15 章的 lambda 正好派上用场）。

## 16.3 泛型 variant：自己写一个 `Option`

第 12 章的 variant 能表示"几种情况之一"，但每种情况里的字段类型是写死的。想让"有值 / 没值"能装任何类型，就需要泛型 variant。标准库之外的经典例子：

<<< @/snippets/book/ch16_option.spr

```text
42
0
kiwi
```

- `generic T:` 里的 variant 可以引用 `T`。这里用展开写法：`Some:` 下一行缩进写 `value: T`；`None:` 没有字段。第 12 章的紧凑写法（`Some(value: T)`）在这里同样能用，展开写法在字段多的时候更好读。
- 构造和普通 variant 一样：`Option.Some(value=42)`。`T` 从 `42` 推断成 `Int`。
- `Option[Int].None` **没有负载可推断**，所以类型参数必须写出来。`read` 的返回类型是具体类型，`match` 按第 12 章的方式穷尽两个情况。
- `Option.Some(value="kiwi")` 推断出 `Option[String]`，`kiwi.value` 就是 `String`。

泛型 variant 和泛型类一样，每次使用都会用具体类型检查一遍；`match` 仍然必须穷尽所有情况。

## 16.4 契约类

有时调用方只关心"这个对象有哪几个方法"，不关心、也不该关心它具体是哪个类：日志写控制台还是写内存，数据存文件还是存数据库。Sprig 用**契约类**（contract class）表达这种关系——方法只有签名、没有函数体：

<<< @/snippets/contracts.spr

```text
console: a
console: b
2
1
```

逐段看：

- `class Sink:` 的两个方法在冒号前就结束了，没有函数体。这样的类就是契约：不能构造 `Sink()`，不能有字段，也不能一部分方法有函数体。
- `conform Console to Sink` 声明 `Console` 遵循这个契约。编译器检查 `Console` 有契约里的每个方法，参数类型、返回类型完全一致，能抛出的错误不多于契约声明的。
- 之后 `Console` 的值可以放在任何要 `Sink` 的地方。`log_all` 的 `sink` 参数就是 `Sink`，它只能调用契约里列出的 `write` 和 `flush`。
- `log_all(["a", "b"], Console())`：`Console` 写两行再返回计数 `2`，所以先打印两行 `console: ...`，最后打印 `2`。
- `log_all(["c"], Memory())`：`Memory` 不打印任何东西，只把行存起来，`flush` 返回 `1`。

**有同名方法不等于遵循契约**，必须写 `conform`：

### 故意写错：方法都写对了，就是没写 `conform`

<<< @/snippets/book/ch16_missing_conform.spr

```text
SPR-TYPE-ASSIGN [TYPE] main.spr:8:18: Type mismatch in initializer (expected Sink, actual Console)
  hint: 'Console' does not conform to the contract 'Sink': declare 'conform Console to Sink' after the class, with every method of the contract matched exactly.
```

`Console` 明明有 `write(String) -> Unit`，但没声明遵循，所以它和 `Sink` 没有关系。提示直接给出了要补的那一行。（同样的缺失出现在函数参数位置时，报错码是 `SPR-TYPE-MISMATCH`，消息一样。）

契约不能拿来构造，也不能当 `requires` 的能力用：

### 故意写错：构造一个契约

<<< @/snippets/book/ch16_contract_construct.spr

```text
SPR-CLASS-ABSTRACT [TYPE] main.spr:4:12: Contract class 'Sink' has methods without a body and cannot be constructed
  hint: Write a class with those methods and 'conform C to Sink', then construct that class; a value of type Sink is any conforming object.
```

契约描述的是"任何满足它的对象"，它自己没有实例。提示里的思路就是本节的 `Console` 和 `Memory`。

### 故意写错：把契约当成能力

<<< @/snippets/book/ch16_contract_bound.spr

```text
SPR-GENERIC-CONSTRAINT [TYPE] main.spr:6:9: 'Sink' is a contract, not a capability; a contract is a type, never a bound on a type parameter
  hint: Take 'Sink' as the parameter type instead of a type parameter bounded by it: replace 'T' with 'Sink' in the signature (sink: Sink) and drop the requires clause. Capabilities are the closed set Equatable and Comparable.
```

`requires` 只接受 `Equatable` 和 `Comparable`。想要"能写日志的东西"，就直接把参数类型写成 `Sink`（像 `log_all` 那样），不要用类型参数。

契约还有几条 0.8 语言的规则：契约不能是泛型的（按元素类型各写一个契约，或者写一个持有 `fn` 字段的泛型类）；契约没有默认方法，共用逻辑就写一个接收契约的普通函数；`conform` 只能写在声明这个类的模块里；一个值通过契约类型只能看到契约的方法，没有向下转型、也没有类型判断。

**什么时候用 variant，什么时候用契约？** 情况**固定**、需要逐情况处理（`match` 必须穷尽）的，用 variant；实现**可以不断增加**、调用方只按方法打交道的边界，用契约。第 12 章的 `Shape` 是 variant 的样子，这里的 `Sink` 是契约的样子。

::: tip 学过其他语言？
契约类像 Java/C# 的接口，但有几处不一样：没有默认方法、不能是泛型的、不能继承另一个契约，也**没有隐式（结构）实现**——必须显式 `conform`，而且只能在声明类的模块里写。这样任何一段代码的"契约关系"都能在本地读出来。更细的规则以后在 Java 互操作（第 21 章）里还会遇到 `conform` 的另几种用法。
:::

## 16.5 本章小结

- `generic T:`（或 `generic K, V:`）里的一个类、variant 或函数可以引用类型参数。
- 调用时通常从参数推断 `[T]`；类型位置和 `None` 这类无负载的情况必须写；推断从不看期望类型，`[]`/`{}`/`null` 不给信息。
- 裸 `T` 没有操作；`requires T: Comparable` / `Equatable` 作为函数体第一条语句声明能力，调用时逐个检查。
- 泛型 variant 用展开或紧凑写法都可以；无负载的情况要写 `[T]`。
- 契约类只有方法签名；`conform C to Contract` 声明遵循；不写就没有关系；契约不能构造、不能当能力、不能是泛型。
- 封闭的情况用 variant，开放的实现用契约。

## 16.6 动手练习

**练习 1（易）** 写一个泛型函数 `last_or(items: List[T], fallback: T) -> T`：空列表返回 `fallback`，否则返回最后一个元素。分别传 `[1, 2, 3]` 和空列表调用。

提示：`items[items.size() - 1]` 是最后一个元素；两次调用的类型由参数决定。

::: details 参考答案
<<< @/snippets/book/ch16_ex1.spr

```text
3
none
```
:::

**练习 2** 写一个有两个类型参数的 `Pair` 类，字段 `first: A`、`second: B`，构造 `Pair(first=1, second="one")` 并分别打印两个字段。

提示：`generic A, B:` 和 `Entry` 一样；推断会同时定出 `A` 和 `B`。

::: details 参考答案
<<< @/snippets/book/ch16_ex2.spr

```text
1
one
```
:::

**练习 3** 定义契约 `Greeter`（方法 `greet(name: String) -> String`），写两个遵循它的类：`Friendly` 返回 `"hello, " + name`，`Formal` 返回 `"Good day, " + name`。再写一个接收 `Greeter` 的 `welcome` 函数，各传一个实例调用。

提示：`conform` 写在每个类后面；`welcome` 只看得到契约方法 `greet`。

::: details 参考答案
<<< @/snippets/book/ch16_ex3.spr

```text
hello, Ada
Good day, Bo
```
:::

**练习 4（难）** 仿照 `Option` 写泛型 variant，配一个 `unwrap_or(option: Option[Int], fallback: Int) -> Int`：`Some` 返回值，`None` 返回 `fallback`。用 `Some(value=7)` 和 `Option[Int].None` 各调用一次。

提示：构造 `Some` 时 `T` 能从 `7` 推断；`None` 没有负载，要写 `Option[Int].None`；`match` 两个情况都要处理。

::: details 参考答案
<<< @/snippets/book/ch16_ex4.spr

```text
7
0
```
:::

下一章：[数字进阶](/tutorial/ch17-numbers)——`Int32`、`Float32`、`Decimal`、`BigInt` 和 `@std/math` 的那点事。
