# 6. 类

类是把几个字段和操作它们的方法放在一起的东西。Sprig 的类**没有继承**，也没有接口，这是有意的设计：需要"几种形状之一"时用第 7 章的 variant；需要满足某个 Java 接口时用 `conform`（第 9 章和第 13 章会遇到）。

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

## 6.3 什么时候用类

类适合有身份、会变化的东西：一笔正在编辑的支出、一个连接、一个计数器。固定的几种形状之一、按内容比较的值，用 variant。

类不能继承，所以"一个基类加几个子类"的设计在 Sprig 里要换一种写法：把共同的字段放进一个类，把不同的部分做成一个 variant 字段。第 16 章的记账工具用的就是这个结构。

## 小结

- `class` 里有带类型的 `let`/`var` 字段、默认值和方法。
- 构造实例用字段名，`print` 显示全部字段。
- `==` 比身份，没有继承和接口。

下一章：[枚举、variant 和 match](/tutorial/ch07-enums-variants)。
