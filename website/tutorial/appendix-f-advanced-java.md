# 附录 F. Java 互操作进阶

第 21 章讲了调用 Java 的日常用法：import、`sprig api`、判空、数值、lambda、集合、异常。这个附录把 JVM 边界上的细节一次说清楚，遇到具体问题时来查。里面的每条规则都来自编译器本身（`sprig help jvm`、`sprig api`），每条命令都真实运行过。

这一章你会学到：

- `sprig api` 除签名之外还告诉你什么；
- 可空性注解怎么改变返回类型上的 `?`；
- 泛型方法的类型实参：什么时候能推断，什么时候必须手写；
- `conform` 的两个方向：实现 Java 接口，以及用 `conform ... as parent` 继承 Java 类；
- 函数式接口的边界，和为什么带 `throws Error` 的函数值传不进去；
- 受检异常在函数、顶层语句和 `conform` 里的完整规则；
- `--classpath` 和 `--classpath-file`；
- JVM 类型映射速查表。

## F.1 sprig api：签名之外的元数据

**为什么。** Java 重载多、注解多，肉眼猜不出来。`sprig api` 读的是编译器实际使用的元数据，和它一起测试。

`--member` 只看一个成员。对 `final` 类或接口，`protectedMethods` 是空的；对能被继承的类，它列出 protected 方法，以及谁会用到它们：

```text
$ sprig api java.util.Random --member next
Java API: java.util.Random
constructors:
staticMethods:
instanceMethods:
fields:
protectedMethods (in a class declared with 'conform C to Random(...) as NAME': override them, call them as NAME.m(...)):
  protected int java.util.Random.next(int) => next(Int32) -> Int32
```

`protected` 方法不能直接在值上调用，只能在自己的类里用父类视图（F.5）。

加 `--json` 每个成员还带这些字段（后文用到哪个说哪个）：

- `nullableResult`：结果是否可能为 `null`；
- `interopLevel`：互操作等级（`direct`、`concrete-generic`、`opaque-array`、`adaptable`、`sprig-callable`、`java-callable`、`erased-generic`、`unsupported`）；
- `interopReasonCodes`：机器可读的原因，例如 `explicit-type-arguments-required`、`wildcard-bounds`、`generic-array-unsupported`；
- `usableFromSprig` 和 `unusableReason`：这个成员能不能调用、不能的话为什么。

`sprig api .` 查整个项目；`sprig api @std/lists.spr` 查 Sprig 模块。`api` 只读签名，从不运行你的程序。

## F.2 可空性注解

**为什么。** 默认规则（第 21 章）是「Java 引用结果一律可空，Java 形参一律非空」。库作者用注解表达得更精确时，Sprig 按注解来。

`sprig help jvm` 的规则原文：

- 结果默认可空，例外是 `toString()` 和标注了 `NotNull`/`NonNull`/`Nonnull` 的结果，或类/包上有 `NullMarked`、`NonNullApi`、`MethodsReturnNonnullByDefault`；
- 形参标注 `Nullable` 或 `CheckForNull` 时才能接受 `T?` 或 `null`；
- 只在 class 文件里保留的注解（CLASS 保留，例如 `org.jetbrains.annotations`）也读得到；注解按**简单名**匹配，每个库的拼法都算。

用一个自己的类验证。目录里放三个文件：

```java
// src/fixture/NonNull.java
package fixture;

public @interface NonNull {}
```

```java
// src/fixture/Nullable.java
package fixture;

public @interface Nullable {}
```

```java
// src/fixture/Library.java
package fixture;

public class Library {
    @NonNull
    public String label() { return "ok"; }

    @Nullable
    public String nick() { return null; }

    public String plain() { return "p"; }
}
```

编译后用 `--classpath`（F.8 细讲）查签名：

```text
$ javac -d out/classes src/fixture/*.java
$ sprig api fixture.Library --classpath out/classes --member label
Java API: fixture.Library
constructors:
staticMethods:
instanceMethods:
  public java.lang.String fixture.Library.label() => label() -> String
fields:
$ sprig api fixture.Library --classpath out/classes --member nick
Java API: fixture.Library
constructors:
staticMethods:
instanceMethods:
  public java.lang.String fixture.Library.nick() => nick() -> String?
fields:
```

`@NonNull` 的 `label` 是 `String`（没有 `?`），`@Nullable` 的 `nick` 是 `String?`，没标注的 `plain` 是 `String?`。所以 `label()` 可以不再判空：

```sprig
import fixture.Library as Library

let lib = Library()
print(lib.label().toUpperCase())
let nick = lib.nick()
if nick == null:
    print("no nick")
```

```text
$ sprig run use_library.spr --classpath out/classes
OK
no nick
```

类一级的默认值（`NullMarked` 等）必须是**运行时可见**的注解（RUNTIME 保留）。给 `Marked` 类加上 RUNTIME 的 `@NullMarked` 后，没标注的结果也变成非空：

```java
// src/fixture/NullMarked.java
package fixture;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface NullMarked {}
```

```text
$ sprig api fixture.Marked --classpath out/classes --member label
Java API: fixture.Marked
constructors:
staticMethods:
instanceMethods:
  public java.lang.String fixture.Marked.label() => label() -> String
fields:
```

`@Nullable` 的成员不受默认值影响，仍是 `String?`；而**默认值永远不会把形参变成可空**，只有显式标注 `Nullable` 的形参才接受 `null`——Sprig 从不主动把一个 `null` 交给 Java。

成员级的 `@NonNull` / `@Nullable` 还有一个细节：注解类即使不在 classpath 上，只要 class 文件里有它的名字就能识别。把 `Library.class` 单独拷进一个目录再查，结果不变。

::: tip 学过其他语言？
Minecraft 的官方映射（Mojang mappings）用 JSpecify 在包上加 `@NullMarked`；Fabric Loader 用 `org.jetbrains.annotations`。所以调用模组 API 时，未标注的方法结果通常是非空的，标注 `@Nullable` 的才要判空。
:::

## F.3 泛型方法：推断还是手写

**为什么。** Java 的静态方法常带自己的类型参数（`<T> ...`）。Sprig 的规则是：**能从实参精确推断就推断，推不出来就必须写出 `方法[类型](...)`**。

`sprig help jvm` 的原文：类型参数在「实参恰好固定每一个类型参数」时被推断；lambda、`null`、Sprig 集合、原始 Java 值都不提供信息，返回类型也不参与推断。实参本身能提供的就是精确的类型，比如形参 `T` 收 `String` 得 `T = String`，形参 `List<T>` 收 `ArrayList[String]` 得 `T = String`；同一个 `T` 出现两次必须得到同一个类型。

看一个能推断的例子，和一个必须手写的例子：

<<< @/snippets/book/appendix_f_generic.spr

```text
[ada, grace]
0
```

逐行看：

- `Collections.sort(names)` 的签名带递归约束 `T extends Comparable<? super T>`。`names` 是 `ArrayList[String]`，`T = String` 从实参推出来，约束在调用处检查通过（`String` 实现 `Comparable[String]`），排序原地进行。
- `Collections.emptyList[String]()` 没有任何实参能定出 `T`，所以把 `[String]` 写在方法名后面；结果仍然按 Java 引用规则是可空的 `List[String]?`，所以先判空再 `size()`。
- 注意写法：`[String]` 紧跟在方法名后面，是**类型实参**，不是 Sprig 的集合下标。

推断有精确的边界：形参 `List<T>` 只接受**具体的** Java 泛型实参（`ArrayList[String]` 可以），原始的 `ArrayList()` 不行；lambda 和 `null` 什么都不说明。写不出来的调用会像这样失败：

<<< @/snippets/book/appendix_f_generic_missing.spr

```text
SPR-JVM-MEMBER [JVM] main.spr:4:12: Java class Collectors has no method 'toList' matching 0 argument(s)
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

报错说 `toList` 没有匹配 0 个实参的重载——它的类型参数没有实参可用。`sprig check --json` 在 `data.candidates` 里列出每个候选取不到的类型，`repair.kind` 是 `inspect-api-and-make-arguments-explicit`。修法就是 `Collectors.toList[Int]()`。

泛型实参之间是不变的：`ArrayList[String]` 可以当作 `List[String]`（子类型投影到接口），但 `ArrayList[Int32]` 不能当 `List[String]`，原始的 `ArrayList()` 也不会「变成」任何具体类型。

通配符保留边界。Sprig 自己没有通配符语法，但 Java 结果里可以出现，例如：

```text
$ sprig api java.lang.Object --member getClass
Java API: java.lang.Object
constructors:
staticMethods:
instanceMethods:
  public final native java.lang.Class<?> java.lang.Object.getClass() => getClass() -> java.lang.Class[?]?
fields:
```

`Class[?]?` 这个值可以接收、判空、再传给别的 Java 方法，但不能在 Sprig 声明里写出来。带 `? extends` 的容器只读：读取的元素按上界处理，任何写入（`add`）都会被拒绝。

<<< @/snippets/book/ch13_wildcards.spr

```text
[3, 1]
ArrayList
3
```

## F.4 用 conform 实现 Java 接口

**为什么。** Java 框架要的是「实现了这个接口的对象」：线程要 `Runnable`，回调要 `Consumer`。Sprig 的类可以声明它满足一个 Java 接口，这个方法叫 `conform`：

<<< @/snippets/book/ch13_conform_interface.spr

```text
hi ada
hi ada
```

逐行看：

- `class Greeter` 有一个 `func run() -> Unit`，和 `Runnable` 唯一的抽象方法形状一致。
- `conform Greeter to Runnable` 是一行声明，没有方法、没有适配；编译器验证形状后，生成的 Java 类把 `Runnable` 放进接口列表。
- `let task: Runnable = Greeter(name="ada")` 把 Sprig 对象交给 Java 视角；`Thread(task)` 照常接收。
- `conform` 必须写在声明这个类的模块里：没有事后补救，也没有「形状对就算」的结构化匹配（第 16 章的契约类就要求显式 `conform`）。

规则（`sprig help conform`）：

- 目标必须是 import 进来的 **public、非泛型、非 sealed 的 Java 接口**；
- 每个抽象实例方法都要有一个同名、同参数 JVM 形状、同返回 JVM 形状的方法；重名的抽象方法（重载）不被支持；
- 一个类可以有多个接口 `conform`，但同一条关系只写一次；
- `Task → Runnable` 和 `Task? → Runnable?` 成立，`Task? → Runnable` 要先收窄；`List[Task] → List[Runnable]` 永远不成立（泛型不变）。

## F.5 扩展 Java 类：`conform ... to J(fields) as parent`

**为什么。** 有些 Java 框架要求你继承它的类并覆盖方法（比如模板方法）。Sprig 类没有继承，但可以用 `conform C to J(字段, ...) as parent` 让生成的类 `extends J`。

<<< @/snippets/book/ch13_conform_class.spr

```text
30
63
2
48
3
```

逐行看：

- `class Counting` 有 `seed` 和 `calls` 两个字段，并定义 `next(bits: Int32) -> Int32`。
- `conform Counting to Random(seed) as parent`：括号里的 `seed` 是 `Counting` 的字段，按顺序传给 `Random` 的那个 JVM 形状匹配的构造器（`Random(long)`）；`as parent` 给父类实现起一个类内可见的名字。
- `func next` 覆盖 `Random` 的 protected `next(int)`（`sprig api java.util.Random --member next` 能在 `protectedMethods` 里看到它）。方法体的 `parent.next(bits)` 就是 Java 的 `super.next(bits)`。
- 前两次 `rng.nextInt(100)` 内部调用了被覆盖的 `next`，所以 `rng.calls` 是 2。把 `rng` 交给父类视图后（`let plain: Random = rng`）再调一次得到 `48`，随后打印的 `calls` 是 3：`Random.nextInt` 内部调用的 `next` 仍然是 `Counting` 覆盖的那个，父类视图没有切开对象身份。

规则（`sprig help conform` 和 `docs/jvm/conformance.md`）：

- 目标必须是 public、非 final、非泛型、非 sealed 的 Java 类（抽象或具体）；
- 括号里只能是 `C` 的字段名，不写表达式：`super(...)` 在任何字段存在之前就运行了；`conform C to J()` 选择无参构造器；
- 名字和父类某个 public/protected 方法相同的 `C` 方法，形状必须精确匹配其中之一，否则 Java 会悄悄生成一个重载、钩子永远不会被调用；覆盖 `final` 方法或遮挡 `static` 方法是 `SPR-CONFORM-MEMBER`；
- `parent` 不是值、没有字段、不能调抽象方法；protected 方法只能通过它调用，不能直接在有类型的值上调用；
- 在 `C` 的值上，未声明的方法照常通过 Java 视图解析（`rng.nextInt(100)`）；
- 泛型父类、第二个 Java 父类、构造器表达式都不在 v1 范围内。

## F.6 函数式接口

**为什么。** Java 用一个「只有一个抽象方法」的接口表示「待会儿要执行的代码」。Sprig 的 `fn` 值可以直接放在这些位置，参数类型必须写出。

<<< @/snippets/book/appendix_f_functional.spr

```text
[ada, bob]
[ADA, BOB]
```

- `removeIf` 收 `Predicate`：`fn(name: String) => name.length() < 3` 删掉短名字。
- `replaceAll` 收 `UnaryOperator`：它对每个元素原地替换，返回 `Unit` 也可以。
- 两个接口的类型参数都通过接收者（`ArrayList[String]`）绑定了，所以 lambda 的参数类型写 `String` 就行。

规则：

- 一个抽象方法、没有方法级类型参数、最多三个参数；
- 形参类型必须和接口方法的映射完全一致（第 21 章的 `SPR-SYNTAX-ERROR` 就是漏写类型）；
- `void` 的结果接受任何 lambda 结果；`int` 结果也接受返回 `Int` 的 lambda（带运行时范围检查）；
- 接口类型实参里的通配符按上界读，例如实现 `Consumer<String>` 的 lambda 满足 `Consumer<? super String>`；
- lambda 的类型必须不带 `throws Error`：Java 看不见这条信息。直接传进去会报：

<<< @/snippets/book/appendix_f_callable_throws.spr

```text
SPR-TYPE-CALLABLE-THROWS [TYPE] main.spr:7:21: A function value that may throw Error cannot be passed to Java parameter 1 of Thread; Java cannot see the throws clause (expected fn() -> Unit, actual fn() -> Unit throws Error)
  hint: Handle the error inside a named function and pass a lambda that calls it.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

修法是在 Sprig 这边把错误处理掉，再传一个不抛错误的函数值：Java 侧的接口只有 `void run()`，没有地方表达「可能失败」。

## F.7 受检异常

**为什么。** Java 把「可能失败」写进签名：`IOException`、`SQLException` 等受检异常必须处理。Sprig 把它们当成和 `throws Error` 同等的事实。

<<< @/snippets/book/ch13_exceptions.spr

```text
could not read: java.nio.file.NoSuchFileException: definitely-missing.txt
```

- `Files.readString` 带 `throws IOException`，所以 `read_text` 的签名声明 `throws IOException`，把责任传给调用者。
- 顶层 `try` / `catch problem: IOException:` 接住失败。`NoSuchFileException` 是 `IOException` 的子类，一个 `catch` 能接住它。
- 异常对象是 Java 值：`problem.toString()` 一定有值，格式是 `类名: 消息`；`problem.message` 是 `String?`，因为 `getMessage()` 可能返回 `null`。
- **Java 异常不是 Sprig 的 `Error`**：`catch problem: Error:` 接不住 `NoSuchFileException`；反过来，Java 代码把 Sprig 错误转成文本时看到的是 `sprig.runtime.SprigError: 消息`。
- `catch problem: IOException:` 里如果 `try` 块根本不会抛它，是 `SPR-FLOW-CATCH-NEVER-THROWN`。

在**命名函数**里，受检异常要么 `try`/`catch`，要么写进 `throws`，二选一。顶层语句是临时例外：`sprig check` 不要求处理，程序运行时如果真抛出就中止：

```text
$ sprig check toplevel.spr
$ sprig run toplevel.spr
SPR-RUNTIME-EXCEPTION [RUNTIME] toplevel.spr:6:5: NoSuchFileException: definitely-missing.txt
  hint: Inspect the failing operation and the values it received. Run with --stacktrace to see the JVM stack.
```

未受检异常（`ArithmeticException`、`NumberFormatException`）不要求声明，但也可以写进 `throws`；写不写都不影响调用者。

在 `conform` 里，见证方法的 `throws` 只能是 Java 接口方法允许的受检异常的**子集**，否则是 `SPR-CONFORM-EFFECTS`；Sprig 的 `Error` 映射为未受检的 `SprigError`，总是允许。

## F.8 classpath：`--classpath` 和 `--classpath-file`

**为什么。** 用别人的 JAR 或自己 `javac` 出来的类时，要告诉编译器去哪里找。凡是有 `--classpath` 的命令（`api`、`check`、`build`、`run`、`lsp`）都同时有 `--classpath-file`。

```text
$ javac -d out/classes src/fixture/*.java
$ sprig check use_library.spr --classpath out/classes
$ sprig check use_library.spr --classpath-file classpath.txt
$ echo $?
0
```

`classpath.txt` 一行一个 JAR 或目录，空行和 `#` 注释会被忽略：

```text
# one JAR or directory per line; blank lines and # comments are ignored
out/classes
```

这个形式是给构建工具用的：Windows 的命令行有长度上限，一长串 `--classpath` 塞不下。重复的 `--classpath` 也可以，或用系统的路径分隔符一次给多个。

查找顺序是：JDK 运行时类 → 项目锁定的依赖 JAR（按记录顺序）→ 显式传入的条目；先找到的算。条目不存在时直接报错，不会悄悄忽略：

```text
$ sprig api fixture.Library --classpath nope.jar
SPR-JVM-CLASSPATH [JVM] Classpath entry does not exist: .../nope.jar
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

（真实输出是绝对路径。）在项目里不需要这些参数：`sprig.toml` 的 `[[jvm]]` 依赖由 `sprig resolve` 下载并锁定，`api`/`check`/`build`/`run` 自动使用锁定的 JAR（第 18 章）。

## F.9 把 Java 类包装成 Sprig 源码：sprig wrap

**为什么。** 一个陌生的 Java 类，手抄方法签名容易出错。`sprig wrap` 读同样的 classpath，把能支持的方法生成成普通、可编辑的 Sprig 源码：

```text
$ sprig wrap fixture.Library --classpath out/classes --out wrapped.spr
Wrapped fixture.Library -> .../wrapped.spr
  generated: 4, skipped: 0
```

生成的 `wrapped.spr` 开头几行：

```sprig
# Generated by sprig wrap from fixture.Library.
# Ordinary Sprig source; safe to edit.
# Regeneration overwrites only with --force.

import fixture.Library as Host

class Library:
    let host: Host

    func label() -> String?:
        return host.label()
```

- 不支持的方法会被跳过并给出结构化原因（`varargs`、通配符、泛型数组等），不会猜；
- `--force` 才能覆盖已有文件；生成的源码先过检查再写盘；
- 注意它很保守：`label` 有 `@NonNull`，包装后还是 `String?`，多判一次空总是安全的；
- `--member` 可以只包装一个方法。

## F.10 JVM 类型映射速查

| Java | Sprig | 说明 |
|---|---|---|
| `long` | `Int` | 64 位整数 |
| `int`、`short`、`byte` | `Int32` | 32 位以内 |
| `double` | `Float` | |
| `float` | `Float32` | `Float` 不会自动收窄 |
| `boolean` | `Bool` | |
| `char` | `String` | 一个 UTF-16 单元的字符串字面量 |
| 装箱类型（`Long`、`Integer`…） | 可空版本（`Int?`、`Int32?`…） | 结果默认可空 |
| 引用结果 | `T?` | `toString()` 和有非空注解的除外 |
| `byte[]` 等数组 | 不透明值 | 用 `HostBytes` 助手处理 `byte[]` |
| `List`/`Map` 等集合 | 不自动转换 | 用 `@std/jvm` 显式复制 |

几条容易忘的：

- `Int` 变量传给 `int`/`Integer` 形参时自动收窄并做运行时范围检查，有精确的 `long` 重载时仍优先 `long`；字面量放不进 `int` 时在静态检查就报错（`SPR-JVM-MEMBER`）。
- `Float` 永不自动变 `float`：写 `toFloat32Lossy()`（允许舍入）或 `toFloat32Exact()`。
- 数组不能构造、下标访问或遍历；`byte[]` 用 `HostBytes.utf8`/`utf8String`/`length`/`hex`。
- 泛型类写具体实参（`ArrayList[String]()`），泛型方法能推断就推断、不能就写（F.3）。
- Java 形参默认非空；`Int?` 传给 `int` 槽要先收窄（`if x != null` 或 `or_else`）。

## 本章小结

- `sprig api --json` 给 `nullableResult`、`interopLevel`、`interopReasonCodes` 等机器可读信息；`protectedMethods` 配合父类视图使用。
- 可空性注解按简单名匹配：`NotNull`/`NonNull`/`Nonnull` 让结果非空，`Nullable`/`CheckForNull` 让形参接受 `null`；类级默认值要求注解运行时可见，形参永远不因默认值变可空。
- 泛型方法在实参能精确固定类型参数时推断，否则写 `method[Type](...)`；lambda、`null`、原始值不提供信息。
- `conform C to Interface` 声明实现；`conform C to Class(fields) as parent` 让类继承 Java 类，并用父类视图调用被覆盖的 `super` 方法。
- `fn` 值可以传给函数式接口，参数类型必须写；带 `throws Error` 的不行。
- 受检异常在命名函数里必须处理或声明；顶层语句暂不强制；`conform` 里只能是接口允许的子集。
- `--classpath` / `--classpath-file` 使用和项目锁定依赖同一套查找顺序；`sprig wrap` 把 Java 类生成成可编辑的 Sprig 源码。

## 动手练习

**练习 1（简单）。** 用 `sprig api` 查 `java.lang.Integer.parseInt`。哪个重载收 `String`？结果可空吗？

::: details 参考答案
```text
$ sprig api java.lang.Integer --member parseInt
Java API: java.lang.Integer
constructors:
staticMethods:
  public static int java.lang.Integer.parseInt(java.lang.CharSequence,int,int,int) throws java.lang.NumberFormatException => parseInt(CharSequence, Int32, Int32, Int32) -> Int32
  public static int java.lang.Integer.parseInt(java.lang.String) throws java.lang.NumberFormatException => parseInt(String) -> Int32
  public static int java.lang.Integer.parseInt(java.lang.String,int) throws java.lang.NumberFormatException => parseInt(String, Int32) -> Int32
instanceMethods:
fields:
```

收 `String` 的重载是 `parseInt(String) -> Int32`。`int` 是基本类型，结果映射成不可空的 `Int32`（不是 `Int32?`）。
:::

**练习 2（简单）。** 用 `removeIf` 和 lambda 从 `["ada", "alan", "bob"]` 里删掉所有以 `"a"` 开头的名字，打印剩下什么。

::: details 参考答案
<<< @/snippets/book/appendix_f_ex_predicate.spr

```text
[bob]
```

`removeIf` 收 `Predicate`，lambda 返回 `Bool`；`startsWith` 是 Java `String` 的方法。
:::

**练习 3（中等）。** 把 F.5 的 `Random` 例子改成覆盖 `nextInt(bound)`：统计 `nextInt` 被调用几次，并让每次调用都走父类实现。提示：方法签名是 `nextInt(bound: Int32) -> Int32`，和 `Random` 的 public 方法形状一致。

::: details 参考答案
<<< @/snippets/book/appendix_f_ex_parent.spr

```text
30
63
2
```

`nextInt` 的形状和父类匹配，所以是覆盖；`parent.nextInt(bound)` 调用父类实现。两个 `print(rng.nextInt(100))` 之后 `calls` 是 2。
:::

**练习 4（稍难）。** 把 F.8 的 fixture 类目录写进 `classpath.txt`，用 `--classpath-file` 检查 `use_library.spr`，再故意把路径写错一次，看报什么。提示：文件里空行和 `#` 注释都可以有。

::: details 参考答案
```text
$ cat classpath.txt
# one JAR or directory per line; blank lines and # comments are ignored
out/classes

$ sprig check use_library.spr --classpath-file classpath.txt
$ echo $?
0
$ sed -i '' 's|out/classes|out/typo|' classpath.txt
$ sprig check use_library.spr --classpath-file classpath.txt
SPR-JVM-CLASSPATH [JVM] Classpath entry does not exist: .../out/typo
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

`check` 通过时没有输出、退出码 0。路径错了立刻报 `SPR-JVM-CLASSPATH`，并给出解析后的绝对路径。
:::

回到：[21. 调用 Java](/tutorial/ch21-java)，或者用[第 24 章的综合项目](/tutorial/ch24-project-ledger)把学到的用起来。

::: tip 学过其他语言？
如果你写过 Java：这里的 `conform` 一行同时是 `implements` 和 `extends` 的受限版本——接口方向零适配、显式声明，父类方向要求构造器字段和精确覆盖。`sprig wrap` 生成的代码就是普通 Sprig，可以当作调用陌生库的起点。
:::
