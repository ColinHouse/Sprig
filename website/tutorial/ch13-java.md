# 13. 调用 Java

Sprig 跑在 JVM 上，整个 Java 生态都在手边。这一章讲怎么用 Java 类，以及 Sprig 在边界上坚持的几条规则：Java 返回的引用可能为 null、数值不悄悄变窄、集合不悄悄复制、受检异常不悄悄传出去。最后两节讲怎么让 Sprig 的类满足 Java 接口，以及怎么继承一个 Java 类。

## 13.1 import 一个 Java 类

<<< @/snippets/book/ch13_java.spr

```text
[apple, pear]
2
APPLE
2024-03-01
9
```

- `import java.util.ArrayList as ArrayList`：完整类名，`as` 后面是你在代码里用的名字。和导入 Sprig 模块是同一个关键字，别名可以随便起（`import java.lang.String as JString`），和 Sprig 自己的类型重名时就靠它区分。
- `ArrayList[String]()` 构造实例；Java 的泛型类**总是**要写类型参数。写成 `ArrayList()` 也能编译，但那是一个"擦除"了元素类型的原始类型，`get` 出来只是 `Object?`，`sprig api` 把这种用法标为 `erased-generic`。
- 实例方法、静态方法（`Collections.sort`、`Math.max`）直接调用，参数按位置传，没有命名参数。`Collections.sort` 的类型参数 `T` 由实参 `ArrayList[String]` 推断出来；只出现在返回值里的类型参数要自己写，比如 `Collections.emptyList[String]()`。
- `names.get(0)` 的类型是 `String?`，所以要先判断再调 `toUpperCase()`。下一节讲为什么。

Java 的基本类型和 Sprig 类型的对应关系固定：

| Java | Sprig | 说明 |
|---|---|---|
| `long` / `Long` | `Int` / `Int?` | 包装类的结果可空 |
| `int`、`short`、`byte` 及其包装类 | `Int32` / `Int32?` | 13.4 讲 `Int` 怎么传进去 |
| `double` / `Double` | `Float` / `Float?` | |
| `float` / `Float` | `Float32` / `Float32?` | |
| `boolean` / `Boolean` | `Bool` / `Bool?` | |
| `char` / `Character` | `String`（单个 UTF-16 单元） | 形参只接受单字符的字符串字面量 |
| `String` 和其他引用类型 | 同名类型，结果带 `?` | `LocalDate.of(...)` 是 `LocalDate?` |

## 13.2 用 sprig api 查签名

不确定一个 Java 方法在 Sprig 里长什么样，先问编译器：

```text
$ sprig api java.util.ArrayList --member add
Java API: java.util.ArrayList
constructors:
staticMethods:
instanceMethods:
  public boolean java.util.ArrayList.add(E) => add(Object) -> Bool
  public void java.util.ArrayList.add(int,E) => add(Int32, Object) -> Unit
fields:
```

箭头左边是 Java 的声明，右边是 Sprig 看到的签名。列表里类型参数 `E` 显示为 `Object`，因为查询时不知道你会写什么类型参数；实际调用 `ArrayList[String]` 的 `add` 时，编译器要求传 `String`。加上 `--json` 还能看到每个重载的 `interopLevel`（`direct`、`concrete-generic`、`java-callable`、`opaque-array` 等）、`nullableResult`、受检异常，以及不能从 Sprig 调用时的原因码。

## 13.3 Java 返回的对象都可能为 null

<<< @/snippets/book/ch13_nullable_java.spr

```text
SPR-TYPE-NULLABLE [TYPE] main.spr:4:12: Cannot access 'getYear' on a value that may be null (receiver type LocalDate?)
  hint: Bind the receiver to a let and check it with 'if value != null:' before using 'getYear'.
```

`LocalDate.of(...)` 在 Java 里其实不会返回 `null`，但 Sprig 不知道，也不猜：**凡是 Java 方法返回的引用类型，一律当作可空**。所以每个 Java 调用的结果都要像第 8 章那样先判断再用：绑定到 `let`，`if x != null:`，分支里就是非空的。这多打几行字，换来的是 Java 的 `NullPointerException` 在 Sprig 代码里写不出来。

有三类例外，不带问号：

- 基本类型的结果：`names.size()` 是 `Int32`，`isEmpty()` 是 `Bool`。
- `toString()`：`Object` 的契约保证它返回 `String`。
- 带可空性注解的方法和字段，下面讲。

反过来，**Java 形参默认不接受可空值**：

<<< @/snippets/book/ch13_null_argument.spr

```text
SPR-TYPE-NULLABLE [TYPE] main.spr:5:11: Nullable value is not accepted by Java parameter 1 of ArrayList.add; Java parameters are treated as non-null (expected Object, actual String?)
  hint: Check for null first (if x != null), or handle the absent case in Sprig.
```

`sprig api` 的输出里，问号就在返回类型上：

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

### 可空性注解

很多库已经把"会不会是 null"写成了注解，Sprig 会读它们。按简单名匹配，所以各家的拼法都算数：

- 返回值或公开字段标了 `@NotNull`、`@NonNull`、`@Nonnull`，就是不带问号的 `T`；标了 `@Nullable`、`@CheckForNull` 的保持 `T?`。
- 形参标了 `@Nullable` 或 `@CheckForNull`，就接受 `T?` 和 `null`。
- 类、外层类、包或模块上的 `@NullMarked`、`@NonNullApi`，以及包上的 `@MethodsReturnNonnullByDefault`、`@FieldsAreNonnullByDefault`，让没有注解的返回值和字段都变成非空；`@NullUnmarked` 取消这个默认。默认值从不让形参变成可空。

运行时可见的注解（JSpecify、JSR-305 的 `javax.annotation`、Checker Framework、Spring）通过反射读取；只保留在 class 文件里的（`org.jetbrains.annotations`、Android、Eclipse 的那些）直接从 class 文件里读，所以注解类不在 classpath 上也算数。一个实际的例子：Minecraft（Mojang 映射）每个包都标了 `@NullMarked`，所以 `Item.use` 返回的 `InteractionResult` 不带问号，而它标了 `@Nullable` 的成员保持 `T?`。JDK 自己没有这些注解，所以 `LocalDate.of` 还是 `LocalDate?`。`sprig api` 会把结果反映出来：非空的结果没有 `?`，`--json` 里的 `nullableResult` 是 `false`。

## 13.4 数值：Int 怎么变成 int

Java 的 API 里到处是 `int`，而 Sprig 日常用的是 64 位的 `Int`。规则是：**字面量按需要适配，变量带检查地收窄，可能丢精度的转换要自己写**。

<<< @/snippets/book/ch13_numbers.spr

```text
101
[ab, ab, ab, ab, ab]
4294967296
0.1
```

- 字面量 `Math.max(3, 9)` 直接能写：`3` 放得进 `int`。放不进 `int` 的字面量不会匹配 `int` 形参。
- `Int` 变量传给 `int`/`Integer` 形参、构造器参数、可变参数、Java 字段，或者作为传给 Java 的回调的返回值时，自动收窄，并在运行时检查范围。超出 32 位时程序以 `SPR-RUNTIME-EXCEPTION` 结束：

```sprig
import java.lang.Integer as Integer

let big: Int = 4294967296
print(Integer.toBinaryString(big))
```

```text
SPR-RUNTIME-EXCEPTION [RUNTIME] main.spr:4:1: Numeric error: Int value outside Int32 range
  hint: Guard the checked arithmetic or use an explicit conversion; see `sprig help numerics`. Run with --stacktrace to see the JVM stack.
```

- 有 `long` 重载时优先选它，和 Java 自己的规则一样：`Math.abs(big)` 选的是 `abs(long)`，所以 `4294967296` 原样打印。两个候选都需要收窄时是 `SPR-JVM-AMBIGUOUS`，把实参的类型写明就好。
- Java 返回的 `int` 是 `Int32`。`Int32` 和 `Int` 可以混算，结果是 `Int`（第 2 章），所以 `names.size() + 1` 不用转换。
- **`Float` 从不自动变成 `float`**，`Int` 也不会变成 `short`、`byte`：那是丢精度，不是丢范围。用 `toFloat32Exact()`（不精确就报错）或 `toFloat32Lossy()`（允许舍入）说清楚。

### 故意写错：把 Float 传给 float

<<< @/snippets/book/ch13_float_to_float.spr

```text
SPR-JVM-MEMBER [JVM] main.spr:4:7: Java class Float has no method 'valueOf' matching 1 argument(s)
```

`Float.valueOf(float)` 是存在的，但没有一个重载接受 Sprig 的 `Float`（也就是 Java 的 `double`），所以报"没有匹配的方法"。`sprig check --json` 会列出每个候选和它被拒绝的原因。

## 13.5 把函数值传给 Java

<<< @/snippets/book/ch13_callback.spr

```text
[Al, cy, bob]
AL
CY
BOB
```

Java 方法要一个函数式接口（`Comparator`、`Consumer`、`Runnable`、`Function` 等）时，直接传 Sprig 的 lambda，编译器生成适配器。规则：

- 接口的那个抽象方法最多三个参数，lambda 的参数类型要写出来，并且和接口的一致。
- 返回 `void` 的接口接受任何结果；返回 `int` 的接口（`Comparator.compare`）也接受返回 `Int` 的 lambda，结果按 13.4 的规则收窄。上面的 `a.length() - b.length()` 是两个 `Int32` 相减，直接能用；第 2 章的 `a.compareTo(b)` 返回 `Int32`，是写比较器最省事的办法。
- 带 `throws Error` 的函数值不能跨过这条边界（`SPR-TYPE-CALLABLE-THROWS`），因为 Java 那边不知道怎么处理 Sprig 的 `Error`。先在 Sprig 这边把错误处理掉。
- 接口类型参数里的通配符不用管：实现了 `Consumer<String>` 的 lambda 就是一个 `Consumer<? super String>`。

### 可变参数

<<< @/snippets/book/ch13_varargs.spr

```text
order-7
no arguments
notes.txt
```

Java 的 `String...`、`Object...` 形参接受零个或多个尾随实参，每个按元素类型检查后由编译器打包成数组；也可以恰好传一个从别的 Java 方法拿到的数组，原样传过去。固定参数个数的重载先试，都不匹配再试可变参数形式，和 Java 一样。

## 13.6 集合、数组和通配符

<<< @/snippets/book/ch13_collections.spr

```text
[ada, grace]
3
2
```

`ArrayList[String]` 是 Java 的类型，`List[String]`、`MutableList[String]` 是 Sprig 的，**两者不自动互转**：

<<< @/snippets/book/ch13_collection_mismatch.spr

```text
SPR-TYPE-ASSIGN [TYPE] main.spr:4:37: Type mismatch in initializer (expected java.util.ArrayList[String], actual List[String])
```

搬数据用 `@std/jvm.spr` 的四个函数：`list_snapshot(source)` 和 `map_snapshot(source)` 把 Java 集合复制成不可变的 Sprig `List`/`Map`，遇到 `null` 元素在运行时报错，不让它混进非空的 Sprig 集合；`list_copy(values)` 和 `map_copy(values)` 反过来复制出独立的 `ArrayList`/`LinkedHashMap`。复制之后两边互不影响，上面 `copy.add` 之后 `names.size()` 还是 2。Sprig 这样做是为了让每一次复制都看得见。

`for` 循环也只认 Sprig 的集合：

<<< @/snippets/book/ch13_for_java_list.spr

```text
SPR-TYPE-OPERAND [TYPE] main.spr:5:13: for requires a collection or String (expected List, MutableList, Map, MutableMap or String, actual java.util.ArrayList[String])
```

要么先 `list_snapshot`，要么像 13.5 那样把 lambda 交给 `forEach`。

### 数组

Java 的数组不对应任何 Sprig 类型，是**不透明的值**：可以拿到、判空、原样传给另一个 Java 方法，但不能在 Sprig 里构造、下标访问或遍历。最常见的是 `byte[]`，运行时提供了几个显式的辅助函数：

<<< @/snippets/book/ch13_bytes.spr

```text
3
dGVh
```

`HostBytes.utf8(text)` 和 `utf8String(bytes)` 在 `String` 和 UTF-8 字节之间转换（非法字节报错，不悄悄替换），`length(bytes)` 取长度，`hex(bytes)` 转成十六进制文本。

### 通配符

Java 签名里的 `?` 在 Sprig 里保留原样：

<<< @/snippets/book/ch13_wildcards.spr

```text
[3, 1]
ArrayList
3
```

- `List.copyOf` 的形参是 `Collection<? extends E>`，`ArrayList[Int]` 放得进去；`? super X` 形参接受 `X` 放得进去的元素类型；单独的 `?`（`Collection<?>`、`Class<?>`）接受任何东西。
- 结果里的通配符按上界读：`getClass()` 返回 `Class<? extends ArrayList>`，用它调 `getSimpleName()` 没问题；一个 `List<? extends Number>` 的结果，`get` 出来是 `Number?`。
- **不能穿过 `? extends` 往里写**：对 `List<? extends Number>` 调 `add`，报 `SPR-JVM-MEMBER`，`--json` 里那个候选的 `rejectedBecause` 写着 "argument 1 would write through a '? extends' wildcard of the receiver; no type can be passed in"。这和 Java 自己的捕获规则一样：没有任何类型能证明放进去是安全的。
- Sprig 没有通配符语法：带通配符的值可以持有、传递，但不能写在自己的声明里。

## 13.7 Java 异常

<<< @/snippets/book/ch13_exceptions.spr

```text
could not read: java.nio.file.NoSuchFileException: definitely-missing.txt
```

- Java 方法声明的**受检异常**（`IOException` 等）在 Sprig 里和 `throws Error` 一样：函数里调用它，要么 `catch`，要么在签名里声明 `throws IOException`（导入那个异常类之后就能写名字）。顶层语句可以直接调用，没接住的异常让程序以 `SPR-RUNTIME-EXCEPTION` 结束，并指出位置。
- **未受检异常**（`ArithmeticException`、`IndexOutOfBoundsException`）不要求声明，但同样可以 `catch`。
- Java 的异常**不是** Sprig 的 `Error`：`catch problem: Error` 接不住 `NoSuchFileException`。要接就导入那个类，按它的名字 `catch`；写父类也行，上面用 `IOException` 接住了它的子类 `NoSuchFileException`。
- 异常对象是普通的 Java 值：`problem.toString()` 一定有值，`getMessage()` 返回的是 `String?`。

## 13.8 让 Sprig 的类满足 Java 接口

第 6 章的契约类和第 9 章的 `conform NotFound to Error(message)` 都用了 `conform`。第三种用法是让一个 Sprig 类满足一个导入的 Java 接口，这样就能把它交给要求那个接口的 Java 库：

<<< @/snippets/book/ch13_conform_interface.spr

```text
hi ada
hi ada
```

- 接口的每个抽象方法都要有同名、同签名的 Sprig 方法，编译器按 Java 的签名逐个检查（`SPR-CONFORM-MEMBER`）；方法能抛出的受检异常不能多于接口声明的（`SPR-CONFORM-EFFECTS`）。
- 之后 `Greeter` 的值可以放在任何要 `Runnable` 的地方：`let task: Runnable`、`Thread(task)`。对象还是同一个对象，没有包装。
- `conform` 不会给类加方法，也不做任何转换：方法该叫 `run` 就得叫 `run`。

方法名和签名要和接口一致，编译器会检查。这是 Sprig 在没有继承的前提下，和面向接口的 Java 库合作的方式；Sprig 自己这边的开放多态是第 6 章的契约类。详见 [JVM 互操作指南](/guide/jvm-interop)。

这是 Sprig 在没有继承的前提下，和面向接口的 Java 库合作的方式；Sprig 自己这边的开放多态是第 6 章的契约类。

## 13.9 继承一个 Java 类

有些框架只认子类：Minecraft 的物品要继承 `Item`，Swing 的组件要继承 `JComponent`。`conform` 的第四种形式让生成的 Java 类 `extends` 一个 Java 类：

<<< @/snippets/book/ch13_conform_class.spr

```text
30
63
2
48
3
```

`conform Counting to Random(seed) as parent` 这一行说了三件事：

- **构造器。** 括号里是 `Counting` 的字段名，按这个顺序传给 `Random` 的构造器；编译器在它公开和 `protected` 的构造器里找参数形状完全一致的那个，这里是 `Random(long)`。只能写字段名，不能写表达式或字面量，因为 `super(...)` 要在任何字段存在之前运行。`conform C to J()` 选无参构造器。
- **覆盖按形状匹配。** `Counting` 里的方法只要和 `Random` 继承链上某个公开或 `protected` 的实例方法同名，就必须和它的某个签名完全一致（`next(int)` 对应 `next(bits: Int32) -> Int32`），生成的 Java 带 `@Override`。名字在链上根本不存在的方法是普通的 Sprig 方法。抽象方法必须有实现；`final` 方法不能覆盖。
- **父类视图。** `as parent` 声明一个只在类的方法里可见的名字，`parent.next(bits)` 就是 Java 的 `super.next(bits)`，调用被覆盖前的实现。它不是值，没有字段，不能调静态方法和抽象方法（`SPR-CONFORM-PARENT`）。类的方法里没有 `self`，也不能直接写继承来的成员名，父类视图是唯一的途径；别名不写也行，覆盖就只是替换。

在类的值上，`Counting` 自己没声明的成员走 Java 那边：`rng.nextInt(100)` 是继承来的公开方法，结果按普通的互操作规则映射。`let plain: Random = rng` 成立，`Random` 的每个父类和它实现的接口也都成立。`Random` 自己的 `toString` 保留，不再生成 Sprig 的那个。

### 故意写错：签名的形状不对

<<< @/snippets/book/ch13_conform_shape.spr

```text
SPR-CONFORM-MEMBER [TYPE] main.spr:6:5: Method 'next' parameter 1 does not match java.util.Random.next (expected int, actual long)
  hint: Match the Java signature exactly: name, arity and JVM shapes.
```

`Random.next(int)` 要的是 `Int32`。Java 会把 `next(long)` 当成一个新的重载悄悄接受，然后你的钩子永远不会被调用；Sprig 直接拒绝。

几条限制：`protected` 方法只能覆盖和通过父类视图调用，不能在类的值上调用（和 Java 跨包的规则一样），`protected` 字段碰不到；`sprig api` 只列出公开成员，`protected` 的要看 Java 文档；泛型父类、第二个父类、构造器里的表达式都不支持。Minecraft 物品的完整例子见 [Fabric 指南](/guide/fabric)，规则的精确表述见 [JVM 互操作指南](/guide/jvm-interop)。

## 13.10 用 Maven 上的库

第 12 章的 `sprig add --jvm group:artifact:version` 把一个 Maven 库加进项目。`sprig resolve` 之后，它的类就能像 JDK 的类一样 `import`，`sprig api` 也能查它。不在项目里的零散 JAR，用 `--classpath 路径`（可以重复）交给 `check`、`run`、`api`；路径太多时写进一个文件，一行一个，用 `--classpath-file 文件`。

## 小结

- `import 完整类名 as 名字`；Java 泛型总写类型参数；`sprig api` 查签名。
- Java 返回的引用一律可空，形参一律非空；可空性注解可以改变这两条。
- 字面量按需要适配 `int`，`Int` 变量带检查收窄，`Float` 不会变成 `float`。
- lambda 可以直接传给 Java 的函数式接口（至多三个参数）；可变参数直接写。
- 集合、数组不自动互转，`@std/jvm.spr` 显式复制；通配符只读不写。
- 受检异常等同 `throws`，Java 异常按自己的类 `catch`。
- `conform 类 to 接口` 满足 Java 接口；`conform 类 to 父类(字段) as 名字` 继承 Java 类。

下一章：[并发](/tutorial/ch14-concurrency)。
