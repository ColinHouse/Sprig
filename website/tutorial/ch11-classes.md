# 11. 类和对象

这一章你会学到：

- 怎样把几个相关的值打包成一个**对象**：类的字段；
- 怎样给对象写**方法**，包括修改自己字段的方法；
- 一行类什么时候够用，什么时候必须写完整的类；
- 同一个类的两个对象用 `==` 比较时比的是什么；
- 两个常见错误：漏传必填字段、参数名和字段名重名。

## 11.1 为什么需要类

程序里经常有几个值天生属于同一个东西。比如一个点有横坐标和纵坐标，一笔支出有物品、金额和备注。用分开的变量表示，它们之间“是一伙的”这件事只存在于你脑子里：

```sprig
let x = 3
let y = 4
```

你可以传错一个、漏传一个，编译器看不出问题。类把这件事写进代码：它是一张**蓝图**，说明“这个对象由哪些字段组成”。从一个类创建出来的东西叫**对象**（也叫实例）。

<<< @/snippets/book/ch11_point.spr

```text
Point(x=3, y=4)
7
```

- `class Point:` 定义了一个类，名字用大写开头。类名后面是字段：`let x: Int`、`let y: Int`。字段和普通绑定一样，每个都要写类型。
- 从第 9 章起你见过用类代替元组的写法：一行类 `class Point(x: Int, y: Int)` 和上面的块写法是同一个类，只是省掉了换行。一行类只能写 `let` 字段。
- `Point(x=3, y=4)` 创建了一个对象。**创建对象要写字段名**，这叫命名参数；顺序随便换，读代码的人不用回去翻字段顺序。
- `let start = ...` 把对象绑定到名字。
- `print(start)` 打印对象时，会列出它的全部字段：`Point(x=3, y=4)`。调试时很好用。
- `start.x` 是取字段的写法（点号）。`start.x + start.y` 就是 3 + 4。

字段不能换名字来构造，漏了必填字段编译器会拦住：

::: details 故意写错：漏了必填字段
<<< @/snippets/book/ch06_missing_field.spr

```text
SPR-CALL-MISSING-FIELD [TYPE] main.spr:5:14: Missing required field 'cents:Int'
```

读这条报错：错误码是 `SPR-CALL-MISSING-FIELD`，位置在 `main.spr` 第 5 行第 14 列（`Expense(` 处），消息说你少给了必填字段 `cents:Int`。`sprig explain SPR-CALL-MISSING-FIELD` 会给出完整解释。如果字段声明时给了默认值（像 11.2 里 `Expense` 的 `note`），创建时就可以不写。
:::

## 11.2 方法：挂在这个对象上的函数

类里还能写函数，这种函数叫**方法**。方法直接用字段名访问“自己”的字段，不需要写 `this` 或 `self`。

<<< @/snippets/book/ch06_classes.spr

```text
Expense(item=Coffee, cents=1250, note=morning)
Coffee: 1250 (morning)
false
true
```

- 字段 `item`、`cents` 是 `let`，创建后不能改；`note` 是 `var`，可以改。
- `func is_big() -> Bool:` 和 `func describe() -> String:` 是方法，缩进在类里，写法和第 7 章的普通函数一样，只是多了一个隐式的“当前对象”。
- 方法体里的 `cents` 就是当前这个对象的字段，和 `item`、`note` 一样。
- `let suffix = if note == "": ...` 是第 5 章的 if 表达式，两个分支都给 `suffix` 一个字符串。
- `coffee.note = "morning"` 是给 `var` 字段赋值，点号左边是对象。
- `coffee.describe()` 调用方法；括号里什么都没有，因为 `describe` 没有参数。
- 第一行输出是对象当前的字段值（`note` 在 `print` 之前已经被改成 `morning`），第二行是 `describe()` 拼出来的字符串：`Coffee: 1250 (morning)`。
- `is_big()` 判断金额是否大于 10000。`coffee` 是 1250，所以 `false`；`rent` 是 120000，所以 `true`。

一个细节：**方法按位置传参数**，和普通函数一样。`Expense(item=..., cents=...)` 要写字段名（只有创建对象和构造 variant 时才这样），但调用方法写 `coffee.describe()`、`c.add(4)`，不写 `c.add(amount=4)`；后者会报 `SPR-CALL-POSITIONAL-REQUIRED`。

没有自定义构造函数，没有静态成员，没有可见性修饰符：类里的一切都是公开的。想隐藏内部细节要用模块（第 18 章）。

## 11.3 会变的对象：改字段的方法

字段是 `var` 时，方法可以修改它。修改会留在对象里，所以对象的“状态”会随程序变化。

<<< @/snippets/book/ch11_counter.spr

```text
0
1
5
```

逐步跟踪 `counter.count` 的值：

| 语句 | `counter.count` 之后的值 |
|---|---|
| `let counter = Counter()` | 0（字段默认值） |
| `counter.bump()` | 1 |
| `counter.add(4)` | 5 |

- `func bump() -> Unit:` 没有参数，`count += 1` 把当前对象的 `count` 加一；`+=` 是第 3 章的写法。
- `func add(amount: Int) -> Unit:` 收一个参数 `amount`，`count += amount`。
- 两个方法返回 `Unit`，也就是“不返回值”，和普通函数一样。

::: tip 学过其他语言？
- 创建对象不写 `new`：写 `Counter()`。字段和方法的访问用点号。
- 没有 `this`/`self`：方法直接写字段名。想显式写“当前对象”反而会报 `SPR-NAME-UNRESOLVED`。
- 构造必须写字段名，像 Python 的关键字参数，但在 Sprig 里是强制的。
- `print` 对象会列出所有字段，Python 的 `__repr__` 默认只给地址；Java 的 `toString` 默认也是。
- 没有继承，类也不能有静态成员。第 12 章的 variant 和后面第 16 章的契约类分别解决“几种之一”和“一组共同方法”的问题。
:::

## 11.4 故意写错：参数名和字段名重名

方法里字段和方法参数在同一个命名空间。参数取了一个和字段一样的名字，字段就被挡住了：

<<< @/snippets/book/ch11_field_shadow.spr

```text
SPR-NAME-FIELD-SHADOW [NAME] main.spr:4:5: Parameter 'count' shadows field 'count' of class Counter
  Field declared here at 2:5
```

读法：

- 错误码 `SPR-NAME-FIELD-SHADOW`，位置第 4 行第 5 列，正是参数 `count` 的名字；
- 消息说参数 `count` 遮蔽（shadow）了 `Counter` 的字段 `count`；
- 第二行是关联位置：字段在第 2 行第 5 列声明。编译器知道你说的是哪个字段，但不知道你在方法体里到底想用哪个，所以它不猜。

修法就是换名字。方法的参数和字段不能重名，这一点和很多语言的 `this.count = count` 写法不同。下面的练习 3 会改这个类。

## 11.5 两个对象什么时候算“相等”

类的对象用 `==` 比较的是**身份**：是不是同一个对象，而不是字段是否一样。

<<< @/snippets/book/ch06_identity.spr

```text
false
true
true
```

- `a` 和 `b` 用同样的字段构造，但是两个不同的对象，`a == b` 是 `false`。
- `a == a` 当然是 `true`。
- 想比较内容，要自己写出意思：`a.x == b.x and a.y == b.y` 是 `true`。字段多的时候通常写一个方法，比如 `func same_as(other: Point) -> Bool`。
- 如果一个值本质上是**值**而不是**对象**（坐标、金额、一个形状），更适合用第 12 章的 variant：variant 的 `==` 按内容比较，而且是编译器给的。

## 11.6 什么时候用类

类适合有身份、会变化的东西：一个计数器、一个正在编辑的列表、一个连接。判断标准很简单：

- 同样的内容算不算“同一个东西”？会变化、有独立身份——类。
- 内容一样就完全等价，不会各自变化——第 12 章的 variant。

一行类适合只是把几个值捆在一起、不再改动的场合；需要 `var` 字段、默认值或方法时，就写块形式。类也可以当字段的类型、放进列表、当参数传递，这些用法和值类型一样。

类没有继承，所以“一个基类加几个子类”的设计在 Sprig 里要换一种写法：几种固定的形状放在 variant 里，共同的行为写成普通函数。第 12 章会看到完整的写法。

## 本章小结

- `class` 把字段和方法放在一起；字段写类型，`let` 不可改、`var` 可改，可以有默认值。
- 创建对象用字段名：`Point(x=3, y=4)`；`print` 一个对象会列出全部字段。
- 方法里直接用字段名，没有 `this`/`self`；方法参数按位置传，且不能和字段同名。
- `==` 比较的是不是同一个对象；比较内容要自己写，或者改用 variant。

## 动手练习

**练习 1（一行类）。** 用一行类定义 `Book(title: String, pages: Int)`，创建一个对象，先打印整个对象，再打印它的 `title`。

::: details 参考答案
<<< @/snippets/book/ch11_ex1.spr

```text
Book(title=Sprig, pages=200)
Sprig
```
:::

**练习 2（会变化的字段）。** 写一个 `Reading` 类，有一个 `var read: Int = 0` 字段和一个 `read_pages(n: Int)` 方法，把 `n` 加到 `read` 上。连续读 10 页和 5 页，打印 `read`。

::: details 参考答案
<<< @/snippets/book/ch11_ex2.spr

```text
15
```
:::

**练习 3（修好遮蔽）。** 11.4 的 `Counter` 编译不过。给参数换个不和字段重名的名字，再让它打印 `3`。

::: details 参考答案
<<< @/snippets/book/ch11_ex3.spr

```text
3
```
:::

**练习 4（同一性）。** 先自己写出下面程序的输出，再运行验证：两个字段相同的 `Point`、一个指向第一个对象的别名、以及它们的字段比较，各自是 `true` 还是 `false`？

::: details 参考答案
<<< @/snippets/book/ch11_ex4.spr

```text
false
true
true
```

`a == b` 是 `false`（两个不同的对象），`a == c` 是 `true`（`c` 就是 `a` 这个对象），`a.x == b.x` 是 `true`（字段比较）。
:::

下一章：[枚举、variant 和 match](/tutorial/ch12-enums-variants)。
