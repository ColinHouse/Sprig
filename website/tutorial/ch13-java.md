# 13. 调用 Java

Sprig 跑在 JVM 上，整个 Java 生态都在手边。这一章讲怎么用 Java 类，以及 Sprig 在边界上坚持的几条规则。

## 13.1 import 一个 Java 类

<<< @/snippets/book/ch13_java.spr

```text
[apple, pear]
2
APPLE
2024-03-01
9
```

- `import java.util.ArrayList as ArrayList`：完整类名，`as` 后面是你在代码里用的名字。和导入 Sprig 模块是同一个关键字。
- `ArrayList[String]()` 构造实例；Java 的泛型类**总是**要写类型参数。
- 实例方法、静态方法（`Collections.sort`、`Math.max`）直接调用，参数按位置传。
- Java 的 `int` 对应 Sprig 的 `Int32`，字面量 `3` 会按需要适配，所以 `Math.max(3, 9)` 直接能写。

## 13.2 Java 返回的对象都可能为 null

<<< @/snippets/book/ch13_nullable_java.spr

```text
SPR-TYPE-NULLABLE [TYPE] main.spr:4:12: Cannot access 'getYear' on a value that may be null (receiver type LocalDate?)
  hint: Bind the receiver to a let and check it with 'if value != null:' before using 'getYear'.
```

`LocalDate.of(...)` 在 Java 里其实不会返回 `null`，但 Sprig 不知道，也不猜：**凡是 Java 方法返回的引用类型，一律当作可空**。所以每个 Java 调用的结果都要像第 8 章那样先判断再用。这多打几行字，换来的是 Java 的 `NullPointerException` 在 Sprig 代码里写不出来。

例外只有 `toString()`（一定返回 `String`）、基本类型（`int`、`boolean` 等）和标了 `@NonNull` 注解的方法。

用 `sprig api` 可以查任何 Java 方法在 Sprig 里的签名，问号就在那里：

```text
$ sprig api java.time.LocalDate --member of
Java API: java.time.LocalDate
constructors:
staticMethods:
  public static java.time.LocalDate java.time.LocalDate.of(int,int,int) => of(Int32, Int32, Int32) -> LocalDate?
  public static java.time.LocalDate java.time.LocalDate.of(int,java.time.Month,int) => of(Int32, Month, Int32) -> LocalDate?
```

## 13.3 把函数值传给 Java

<<< @/snippets/book/ch13_callback.spr

```text
[Al, cy, bob]
AL
CY
BOB
```

Java 方法要一个函数式接口（`Comparator`、`Consumer`、`Runnable`、`Function` 等）时，直接传 Sprig 的 lambda，编译器生成适配器。目前支持最多三个参数的接口。带 `throws Error` 的函数值不能跨过这条边界，因为 Java 那边不知道怎么处理 Sprig 的 `Error`。

## 13.4 Java 的集合和 Sprig 的集合

`ArrayList[String]` 是 Java 的类型，`MutableList[String]` 是 Sprig 的，两者不自动互转。需要在两边之间搬数据时，用 `@std/jvm.spr` 里的适配函数，或者自己遍历一遍。Sprig 这样做是为了让每一次复制都看得见。

Java 的数组也不直接对应任何 Sprig 类型：可以把一个 Java 方法返回的数组原样传给另一个 Java 方法，但不能在 Sprig 里下标访问。

## 13.5 Java 异常

一个 Java 方法声明了受检异常（比如 `IOException`），在 Sprig 里就和 `throws Error` 一样：调用方要么 `catch`，要么在签名里声明 `throws IOException`（导入那个异常类后可以写名字）。未受检异常（`ArithmeticException`、`IndexOutOfBoundsException`）不要求声明，但也可以 `catch`。

## 13.6 让 Sprig 的类满足 Java 接口

第 6 章的契约类和第 9 章的 `conform NotFound to Error(message)` 都用了 `conform`。第三种用法是让一个 Sprig 类满足一个导入的 Java 接口，这样就能把它交给要求那个接口的 Java 库：

```sprig
import java.lang.Runnable as Runnable

class Greeter:
    let name: String
    func run() -> Unit:
        print("hi " + name)
conform Greeter to Runnable
```

方法名和签名要和接口一致，编译器会检查。这是 Sprig 在没有继承和接口的前提下，和面向接口的 Java 库合作的方式。详见 [JVM 互操作指南](/guide/jvm-interop)。

## 13.7 用 Maven 上的库

第 12 章的 `sprig add --jvm group:artifact:version` 把一个 Maven 库加进项目。`sprig resolve` 之后，它的类就能像 JDK 的类一样 `import`，`sprig api` 也能查它。

## 小结

- `import 完整类名 as 名字`；Java 泛型总写类型参数。
- Java 返回的引用一律可空，先判断再用；`sprig api` 查签名。
- lambda 可以直接传给 Java 的函数式接口（至多三个参数）。
- 集合、数组不自动互转；受检异常等同 `throws`。
- `conform 类 to 接口` 让 Sprig 类满足 Java 接口。

下一章：[并发](/tutorial/ch14-concurrency)。
