# 21. 调用 Java

前 20 章都在 Sprig 自己的世界里。这一章打开一扇门：Sprig 编译出来的程序跑在 **JVM**（Java 虚拟机，Java 程序的运行环境）上，和 Java 程序跑在同一个地方。Java 生态里现成的库——时间、压缩、网络、数据库——都可以直接拿过来用。

这一章你会学到：

- 用 `import` 引入一个 Java 类，调用它的方法和构造器；
- 用 `sprig api` 查一个 Java 方法在 Sprig 里长什么样；
- 为什么 Java 返回的对象都要先判断 `null`；
- Sprig 的 `Int` 和 Java 的 `int` 之间怎么相处；
- 把 Sprig 的 lambda 和可变参数传给 Java；
- Java 的集合和数组怎么和 Sprig 的集合打交道；
- 怎么接住 Java 的异常。

## 21.1 第一个 Java 类

**为什么。** Java 自带一个巨大的标准类库，比如 `java.lang.Math` 里有各种数学函数。Sprig 自己没重复实现一遍，而是让你直接调用它们。

最小的例子：

<<< @/snippets/book/ch21_first_java.spr

```text
9
4
```

逐行看：

- `import java.lang.Math as Math`：`java.lang.Math` 是这个类的**完整名字**（包路径加点加类名），`as Math` 是你在本文里用的短名字。类名习惯和类名一样写 `Math`；想改短也行，`as M` 之后写 `M.max(...)`。名字不冲突时 `as` 可以省，但本书统一写出来。
- `Math.max(3, 9)`：调用这个类的**静态方法**。静态方法不需要先创建对象，写法就像调用一个函数，参数按位置传。
- `Math.abs(-4)` 同理，取绝对值。

Java 方法的名字是 `camelCase`（首字母小写、词首大写），和 Sprig 自己的 `snake_case` 不一样；不用记，抄 `sprig api` 给的名字就好。

**故意写错：忘了 import。**

<<< @/snippets/book/ch21_no_import.spr

```text
SPR-NAME-UNRESOLVED [NAME] main.spr:1:7: Unresolved name 'Math'
  hint: A Java class needs an import first: import java.lang.Math as Math.
```

编译器在第一行第 7 列（`Math` 开始的地方）说"这个名字不认识"，并且 hint 直接给出了要补的那一行。Java 的类不会自动出现在名称空间里，哪怕它在 `java.lang` 里也要写 import。

## 21.2 用 sprig api 查签名

**为什么。** Java 的方法常有多个重载，参数类型也不一样。与其猜"`max` 收什么样的数"，不如问编译器。

```text
$ sprig api java.lang.Math --member max
Java API: java.lang.Math
constructors:
staticMethods:
  public static double java.lang.Math.max(double,double) => max(Float, Float) -> Float
  public static float java.lang.Math.max(float,float) => max(Float32, Float32) -> Float32
  public static int java.lang.Math.max(int,int) => max(Int32, Int32) -> Int32
  public static long java.lang.Math.max(long,long) => max(Int, Int) -> Int
instanceMethods:
fields:
```

箭头左边是 Java 的声明，右边是 Sprig 看到的签名。`max` 有四个重载，Sprig 各看各的：传两个 `Int` 会选中 `long` 那个，因为 `Int` 就是 64 位整数（第 3 章和第 17 章）。

再看返回类型上的问号，下一节的主角：

```text
$ sprig api java.time.LocalDate --member of
Java API: java.time.LocalDate
constructors:
staticMethods:
  public static java.time.LocalDate java.time.LocalDate.of(int,int,int) => of(Int32, Int32, Int32) -> LocalDate?
  public static java.time.LocalDate java.time.LocalDate.of(int,java.time.Month,int) => of(Int32, Month, Int32) -> LocalDate?
instanceMethods:
fields:
```

`=> of(...) -> LocalDate?` 末尾的问号意思是"结果可能是 `null`"。

## 21.3 Java 返回的引用都可能为 null

**为什么。** Java 世界里 `null` 到处跑，很多 Java 方法在特殊情况下会返回 `null`。Sprig 的类型系统把"可能为 null"写进类型（第 13 章），而它面对 Java 时采取最保守的立场：**凡是 Java 方法返回的引用类型，一律当作可空**，需要先判断再用。

正确的用法是先绑定、再判断：

<<< @/snippets/book/ch21_null_check.spr

```text
2024
```

`LocalDate.of(2024, 2, 28)` 实际不会返回 `null`，但 Sprig 不猜。`date` 的类型是 `LocalDate?`；`if date != null:` 之后的分支里才是非空的 `LocalDate`，这时才能调 `getYear()`。

忘了判断：

<<< @/snippets/book/ch13_nullable_java.spr

```text
SPR-TYPE-NULLABLE [TYPE] main.spr:4:12: Cannot access 'getYear' on a value that may be null (receiver type LocalDate?)
  hint: Bind the receiver to a let and check it with 'if value != null:' before using 'getYear'.
```

报错说：`getYear` 的接收者是 `LocalDate?`，可能是 `null`。hint 给了标准修法。

反方向也一样的严格：**Java 的形参默认不接受可空值**。

<<< @/snippets/book/ch13_null_argument.spr

```text
SPR-TYPE-NULLABLE [TYPE] main.spr:5:11: Nullable value is not accepted by Java parameter 1 of ArrayList.add; Java parameters are treated as non-null (expected Object, actual String?)
  hint: Check for null first (if x != null), or handle the absent case in Sprig.
```

这条规则换来的东西很实在：Java 里最有名的 `NullPointerException`，在 Sprig 代码里写不出来。多打几行判断，编译器替你挡住了整类 bug。

有三类结果不带问号：

- 基本类型的结果：`names.size()` 是 `Int32`，`isEmpty()` 是 `Bool`；
- `toString()`：Java 的契约保证它返回 `String`；
- 方法或字段上有可空性注解（`@NotNull`、`@Nullable` 之类）时按注解来。Sprig 会读这些注解，细节见[附录 F](/tutorial/appendix-f-advanced-java)。

## 21.4 数值：Int 怎么变成 int

**为什么。** Java 的 API 里到处是 32 位的 `int`，而 Sprig 日常用的 `Int` 是 64 位。规则是：**字面量按需要适配，变量带检查地收窄，可能丢精度的转换要自己写**。

<<< @/snippets/book/ch13_numbers.spr

```text
101
[ab, ab, ab, ab, ab]
4294967296
0.1
```

- `Integer.toBinaryString(n)` 要 `int`，`n` 字面上是 `Int` 变量。这种"放得进 32 位"的收窄会自动做，并在运行时检查范围。
- `Collections.nCopies[String](n, "ab")` 用 `n` 做数量，这里泛型的返回类型参数 `[String]` 要自己写出来（编译器没法从一个 `String` 实参反推出元素类型）。
- `Math.abs(big)` 里 `big` 是 `4294967296`，超出 32 位。Java 里 `abs` 有 `long` 重载，Sprig 优先选它，因为 `Int` 正好是 `long`，不需要收窄，于是原样打印。
- 最后一行 `Float` 不会自动变成 `float`。`ratio.toFloat32Lossy()` 明确地说"允许舍入"，才得到 Java 的 `float`。

如果 `Int` 变量真的超出 32 位还不巧被送进 `int` 形参，程序在运行时停下：

```sprig
import java.lang.Integer as Integer

let big: Int = 4294967296
print(Integer.toBinaryString(big))
```

```text
SPR-RUNTIME-EXCEPTION [RUNTIME] main.spr:4:1: Numeric error: Int value outside Int32 range
  hint: Guard the checked arithmetic or use an explicit conversion; see `sprig help numerics`. Run with --stacktrace to see the JVM stack.
```

这是好事：Java 那边会悄悄截断，Sprig 直接报错，不给你一个错误答案。

**故意写错：把 Float 传给 float。**

<<< @/snippets/book/ch13_float_to_float.spr

```text
SPR-JVM-MEMBER [JVM] main.spr:4:7: Java class Float has no method 'valueOf' matching 1 argument(s)
```

`Float.valueOf(float)` 在 Java 里是存在的，但没有一个重载接受 Sprig 的 `Float`（也就是 Java 的 `double`）。报错说"没有参数个数匹配的 `valueOf`"，因为类型不对的参数不算参数。`sprig check --json` 会列出每个候选和拒绝原因。

## 21.5 把函数传给 Java

**为什么。** Java 的库里很多方法要一个"待会儿调用的代码块"，比如排序时的比较规则。Java 用接口表达这种需求（`Comparator`、`Runnable`、`Consumer` 等），Sprig 允许你直接把 lambda（第 15 章）写在那个位置。

<<< @/snippets/book/ch13_callback.spr

```text
[Al, cy, bob]
AL
CY
BOB
```

- `names.sort(fn(a: String, b: String) => a.length() - b.length())`：`sort` 要一个 `Comparator`，lambda 抄起比较的工作。`a.length()` 是 `Int`；lambda 返回 `Int`，编译器把它收窄成 Java 接口要求的 `int`，运行时检查范围。
- **lambda 的参数类型必须写出来**，而且要接口的形状完全一致；这是和 Sprig 自己函数值（类型能推断）不一样的地方。下一小节的错误会现场演示。
- `names.forEach(fn(n: String) => print(n.toUpperCase()))`：`forEach` 要一个 `Consumer`，lambda 每次收一个元素，打印它的大写形式。返回 `void` 的接口接受任何结果，所以这里的 `Unit` 没问题。

**故意写错：lambda 参数没写类型。**

<<< @/snippets/book/ch21_lambda_types.spr

```text
SPR-SYNTAX-ERROR [SYNTAX] main.spr:4:16: mismatched input ',' expecting ':' (unexpected ',')
```

报错停在第一个逗号：编译器在参数表里等的是 `名字: 类型`，看到 `,` 就断了。给 `a` 和 `b` 各补上 `: String` 就好。

### 可变参数

Java 方法可以声明"数量不固定"的尾随参数，比如 `String.format` 的 `Object...`。Sprig 直接往里写：

<<< @/snippets/book/ch13_varargs.spr

```text
order-7
no arguments
notes.txt
```

`String.format("%s-%d", "order", 7)` 里两个尾随实参按元素类型检查，由编译器打包成数组；`format("no arguments")` 一个不传也合法。Java 的 `Paths.get("home", "ada", "notes.txt")` 同样。

::: tip 学过其他语言？
如果你写过 Java：Sprig 的 lambda 在这里就是 Java 的函数式接口实例，省掉了匿名内部类的仪式；但参数类型不能省略，因为编译器不做这方面的推断。接口最多三个抽象方法参数，超了就写不了。
:::

## 21.6 集合和数组

**为什么。** Java 库交换数据用的容器（`ArrayList`、`HashMap`）和 Sprig 自己的 `List`、`Map` 是两套类型。Sprig 的立场是：**互转必须显式发生**，绝不悄悄复制一个集合。

先看两套容器怎么搬数据，用 `@std/jvm` 的四个函数：

<<< @/snippets/book/ch13_collections.spr

```text
[ada, grace]
3
2
```

- `java_names` 是 Java 的 `ArrayList[String]`；`jvm.list_snapshot(java_names)` 现在把它复制成不可变的 Sprig `List`。之后 Java 那边怎么改都不影响这个 Sprig 列表。
- `jvm.list_copy(names)` 反过来：复制出一个新的 Java `ArrayList`，往里面 `add` 不影响原来的 Sprig 列表。所以最后一行 `names.size()` 还是 2。

直接把两者赋值给对方是不行的：

<<< @/snippets/book/ch13_collection_mismatch.spr

```text
SPR-TYPE-ASSIGN [TYPE] main.spr:4:37: Type mismatch in initializer (expected java.util.ArrayList[String], actual List[String])
```

`for` 循环也只认 Sprig 自己的集合：

<<< @/snippets/book/ch13_for_java_list.spr

```text
SPR-TYPE-OPERAND [TYPE] main.spr:5:13: for requires a collection or String (expected List, MutableList, Map, MutableMap or String, actual java.util.ArrayList[String])
```

要么先 `list_snapshot`，要么像 21.5 那样把 lambda 交给 `forEach`。

### 数组和字节

Java 的数组在 Sprig 里是**不透明的值**：可以拿到、判空、原样传给别的 Java 方法，但不能在 Sprig 里构造、下标访问或遍历。最常见的是 `byte[]`，运行时为它准备了显式助手：

<<< @/snippets/book/ch13_bytes.spr

```text
3
dGVh
```

`HostBytes.utf8("tea")` 把文本编成 UTF-8 字节，`HostBytes.length(bytes)` 取长度；`Base64.getEncoder()` 是普通的 Java 调用，`encodeToString` 把字节编成 Base64 文本。`HostBytes` 还有 `utf8String(bytes)`（字节转文本）和 `hex(bytes)`。

## 21.7 Java 异常

**为什么。** Java 方法会抛异常，而且其中一部分是**受检异常**（编译器强制处理的，比如 `IOException`）。Sprig 把这种"可能失败"当成签名的一部分：和 `throws Error`（第 14 章）一样，调用处要么接住，要么在函数签名里声明。

<<< @/snippets/book/ch13_exceptions.spr

```text
could not read: java.nio.file.NoSuchFileException: definitely-missing.txt
```

- `Files.readString` 声明了 `IOException`，所以 `read_text` 的签名写了 `throws IOException`，把处理的责任交给调用者。
- 顶层 `try` 里调用它，`catch problem: IOException:` 抓住失败。`NoSuchFileException` 是 `IOException` 的子类，所以一个 `catch` 能接住它。
- 异常对象是普通的 Java 值：`problem.toString()` 一定有值，`getMessage()` 返回 `String?`。
- **Java 的异常不是 Sprig 的 `Error`**：`catch problem: Error:` 接不住 `NoSuchFileException`。要接就按 Java 的类名接。
- 未受检异常（`ArithmeticException`、`IndexOutOfBoundsException`）不要求声明，但同样可以 `catch`。

**故意写错：受检异常既不声明也不处理。**

<<< @/snippets/book/ch21_checked.spr

```text
SPR-FLOW-THROWS [FLOW] main.spr:8:16: Call may throw IOException; declare 'throws IOException' or handle it with try/catch
  hint: Declare it on 'read_text' by changing its header to 'func read_text(name: String) -> String throws Error, IOException:', and its callers then handle or declare it too (top-level statements need neither), or handle it where it happens: 'try:' around the call, then 'catch problem: IOException:' with what to do instead. Sprig keeps recoverable errors explicit; there is no implicit propagation.
```

hint 就是标准答案：要么把 `IOException` 加进 `throws` 列表，要么就地 `try`/`catch`。

::: tip 学过其他语言？
如果你用过 Java 的 `NullPointerException` 和受检异常：Sprig 把两者都变成了类型层面的事实——可空要判断，受检异常要声明。写起来啰嗦一点，换来的是"运行时意外"少很多。
:::

## 本章小结

- `import java.包.类 as 名字` 引入 Java 类；静态方法、实例方法和构造器都直接调用，参数按位置传。
- 不确定签名就问 `sprig api 类名 --member 方法名`；箭头右边是 Sprig 看到的样子，返回类型上的 `?` 表示可能为 `null`。
- Java 返回的引用一律可空，判过 `null` 才能用；Java 形参默认非空。
- 字面量和小范围 `Int` 可以自动收窄成 `int` 并带运行时检查；`Float` 不会自动变成 `float`，要显式转换。
- lambda 可以传给 Java 的函数式接口，参数类型必须写出。
- Java 集合和 Sprig 集合不自动互转，用 `@std/jvm` 显式复制；`byte[]` 用 `HostBytes` 助手。
- Java 受检异常等同 `throws`，按 Java 类名 `catch`，它不是 Sprig 的 `Error`。

## 动手练习

**练习 1（简单）。** 用 `java.lang.Math` 算 2 的平方根并打印。提示：方法名是 `sqrt`，参数写 `2.0`。

::: details 参考答案
<<< @/snippets/book/ch21_ex_sqrt.spr

```text
1.4142135623730951
```

`sqrt` 返回 Java 的基本类型 `double`，映射成 Sprig 的 `Float`，不是可空的。
:::

**练习 2（简单）。** 用 `java.util.HashMap` 建一个名字到年龄的映射，放入 Ada=36、Grace=45，打印人数和 Ada 的年龄。提示：`put(key, value)`，`size()` 返回 `Int32`，`get` 的结果是可空的。

::: details 参考答案
<<< @/snippets/book/ch21_ex_hashmap.spr

```text
2
36
```

Java 泛型类要写 `HashMap[String, Int]()`；`ages.get("Ada")` 是 `Int?`，先 `if ada != null:`。
:::

**练习 3（中等）。** 用 `java.lang.StringBuilder` 把 `"Sprig"` 和 `"!"` 拼起来再打印。提示：`append` 返回什么可以先不管，最后 `toString()` 拿到 `String`。

::: details 参考答案
<<< @/snippets/book/ch21_ex_builder.spr

```text
Sprig!
```

`append` 的返回值可以当语句直接忽略；`toString()` 保证返回非空 `String`，所以能直接 `print`。
:::

**练习 4（中等）。** 调用 `Integer.parseInt("42")` 和 `Integer.parseInt("abc")`，把后者抛出的 `NumberFormatException` 接住并打印消息。提示：受检异常才要声明，这个异常是未受检的，直接 `catch` 就行。

::: details 参考答案
<<< @/snippets/book/ch21_ex_catch.spr

```text
42
bad number: java.lang.NumberFormatException: For input string: "abc"
```

`catch problem: NumberFormatException:` 按 Java 类名接；`problem.toString()` 一定有值，包含类名和原因。
:::

**练习 5（稍难）。** 建一个 Java `ArrayList[Int]`，放入 3、1、2，用 `sort` 和一个 lambda 让它们从大到小排，打印结果。提示：比较器返回 `b - a` 就是从大到小。

::: details 参考答案
<<< @/snippets/book/ch21_ex_sort.spr

```text
[3, 2, 1]
```

`sort` 的接口要求比较函数返回 Java 的 `int`；lambda 返回 `Int`，编译器自动收窄。`print` 一个 Java `ArrayList` 会按 Java 的 `toString` 打印成 `[3, 2, 1]`。
:::

下一章：[并发](/tutorial/ch22-concurrency)——让 Sprig 程序同时做几件事。
