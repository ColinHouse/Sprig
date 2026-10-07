# 6. 类

类是把几个字段和操作它们的方法放在一起的东西。Sprig 的类**没有继承**，这是有意的设计：需要"几种形状之一"时用第 7 章的 variant；需要"任何实现了这几个方法的对象"时用本章 6.3 的契约类；需要满足某个 Java 接口时用 `conform`（第 9 章和第 13 章会遇到）。

## 6.1 字段、方法、构造

<<< @/snippets/book/ch06_classes.spr

```text
Expense(item=Coffee, cents=1250, note=morning)
Coffee: 1250 (morning)
false
true
```

- 字段像绑定一样声明：`let` 字段创建后不可改，`var` 字段可以。每个字段都要写类型。
- 字段可以有默认值（`note: String = ""`），创建时就可以不传。
- **构造实例必须写字段名**：`Expense(item="Coffee", cents=1250)`。这是 Sprig 里唯一使用命名参数的地方。看起来多打了几个字，但读代码的人不用去翻字段顺序。
- 方法在类里用 `func` 定义，直接用字段名访问自己的字段，不需要 `self.`。
- `print` 一个对象会列出它的全部字段，调试时很方便。

没有自定义构造函数、没有 `self`、没有静态成员、没有可见性修饰符：类里的一切都是公开的。把"内部细节"藏起来的工具是模块（第 12 章）。

只有 `let` 字段、没有方法的小类，可以写成一行：

```sprig
class Point(x: Int, y: Int)
let origin = Point(x=0, y=0)
```

它和块写法是同一种类，只是省掉了换行。需要 `var` 字段、默认值或方法时，就用块写法；在一行写法后面加冒号、`var` 或 `=`，编译器会指出这一点。

### 故意写错：漏了必填字段

<<< @/snippets/book/ch06_missing_field.spr

```text
SPR-CALL-MISSING-FIELD [TYPE] main.spr:5:14: Missing required field 'cents:Int'
```

## 6.2 相等是身份

<<< @/snippets/book/ch06_identity.spr

```text
false
true
true
```

两个类实例用 `==` 比较的是**是否同一个对象**，不是字段是否相等。字段逐个相等的两个 `Point` 是两个不同的东西。想按内容比较就自己写一个方法，或者，如果这个类型本质上是"值"而不是"对象"，用第 7 章的 variant：variant 的 `==` 比较内容。

## 6.3 契约类：方法没有函数体

有时调用方只关心"这个对象有哪几个方法"，不关心、也不该关心它具体是哪个类：日志写到控制台还是文件，数据存在内存还是数据库。Sprig 用**契约类**表达这种关系：一个类，方法只有签名、没有函数体。

<<< @/snippets/contracts.spr

```text
console: a
console: b
2
1
```

- `Sink` 的两个方法在冒号前就结束了，没有函数体。这样的类是契约：不能构造 `Sink()`，不能有字段，也不能有一部分方法带函数体。
- `conform Console to Sink` 声明 `Console` 遵循这个契约。编译器检查 `Console` 有契约里的每一个方法，参数类型、返回类型完全一致，抛出的错误不多于契约声明的。
- 之后 `Console` 的值可以放在任何要 `Sink` 的地方：参数、`let sink: Sink`、`List[Sink]` 字面量。通过 `Sink` 只能调用契约里的方法；反过来，拿到一个 `Sink` 不能把它变回 `Console`。
- 没有继承：契约里没有状态，也没有默认实现。要共用代码，就写一个接收契约的普通函数，比如 `func log_all(sink: Sink, lines: List[String]) -> Unit`。
- 选择的口诀：**一组封闭的类型用 variant，一组开放的类型用契约。** 契约在 0.8 语言里还有三条定下来的规则：不能是泛型的（按元素类型各写一个契约），只能当类型、不能当 `requires` 的约束（直接写 `sink: Sink`），没有默认方法。违反时编译器会报错并给出写法；详见[语言速查](/guide/language-tour#类)。

和第 7 章的 variant 对照着看：variant 把所有情况写在一处，`match` 必须穷尽，适合"情况固定"的值；契约只列方法，任何模块都可以再加一个遵循它的类，适合"实现可以不断增加"的边界。第 13 章会看到同一个 `conform` 也用来满足 Java 接口，甚至继承一个 Java 类。

几条编译器会替你把关的细节：

- `conform` 必须写在声明这个类的模块里，不能给导入的类补一条 `conform`（`SPR-CONFORM-SOURCE`）；需要时写一个本地类转发调用。
- 有同名方法但没写 `conform` 的类不算遵循契约：`let sink: Sink = Console()` 在没有那一行时报 `SPR-TYPE-MISMATCH`，并指出缺的就是 `conform`。
- 契约值没有向下转型，也没有类型判断：`match` 只用于 enum 和 variant。拿到 `Sink` 就只能用 `Sink` 的方法。
- 一个类可以同时遵循几个契约和几个 Java 接口，每对关系只写一条 `conform`。

## 6.4 什么时候用类

类适合有身份、会变化的东西：一笔正在编辑的支出、一个连接、一个计数器。固定的几种形状之一、按内容比较的值，用 variant。实现可以增加的边界，用契约类。

类不能继承，所以"一个基类加几个子类"的设计在 Sprig 里要换一种写法：共同的数据放进一个类，不同的部分做成 variant 字段，或者把不同的行为做成遵循同一契约的几个类。第 16 章的记账工具用的是前一种结构。

## 小结

- `class` 里有带类型的 `let`/`var` 字段、默认值和方法。
- 构造实例用字段名，`print` 显示全部字段。
- `==` 比身份，没有继承。
- 方法没有函数体的类是契约，`conform C to Contract` 让 C 的值能当作契约使用。

下一章：[枚举、variant 和 match](/tutorial/ch07-enums-variants)。
