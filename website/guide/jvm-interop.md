# JVM 互操作

Sprig 通过生成 Java 源码来编译，因此可以用显式 import 别名调用 JDK 与第三方 JVM 类。
规则有意保持保守：Java 不提供可空信息时，Sprig 一律假设引用结果可能为 `null`；Java
引用形参默认为非空。

权威契约（英文）见 [JVM interop](/en/reference/jvm/interop)；本页是可运行的导览。
需要接入 host 构建系统（Gradle/Loom、Maven 等）时，见
[Fabric / JVM 框架集成](/guide/fabric)。

## 导入 Java 类

<<< @/snippets/jvm_interop.spr

```text
9
4
Sprig!
2026
25
```

`import java.lang.Math as Math` 在当前模块绑定简单名 `Math`。通过别名可以调用构造器、
静态方法、实例方法和静态字段。重载会根据实参类型解析；无法匹配或存在歧义时分别得到
`SPR-JVM-MEMBER` 或 `SPR-JVM-AMBIGUOUS`。

## 类型映射

| Java | Sprig（原始类型） | Sprig（装箱/引用） |
|---|---|---|
| `long` | `Int` | `Int?` |
| `int`、`short`、`byte` | `Int32` | `Int32?` |
| `double` | `Float` | `Float?` |
| `float` | `Float32` | `Float32?` |
| `boolean` | `Bool` | `Bool?` |
| `char` | `String` | `String?` |
| `void` | `Unit` | — |
| `String` 及其他引用类型 | — | 可空 Sprig 类型（`T?`） |

原始类型结果非空；引用与装箱结果视为可空，使用前必须判空：

```sprig
let version = System.getProperty("java.version")
if version != null:
    print(version.length() > 0)
```

Java 引用形式参数保守地视为非空，因此 `T?` 实参必须先收窄；接受 `null` 的
`Object` 形式参数是文档中说明的例外。编译器不读取 type-use 可空性注解。

`char`/`Character` 参数只接受恰好一个 UTF-16 代码单元的 String 字面量：`"a"` 合法，
`"ab"` 与 `"😀"` 会被拒绝，因为一个 Java `char` 无法表示补充平面码点。这与 Sprig
String 的 Unicode 码点位置语义不同（见 `sprig help strings --json`）。

## 受检异常

Java 受检异常可以出现在 Sprig 的 `throws` 子句中并被捕获：

<<< @/snippets/jvm_exceptions.spr

```text
example.com
bad uri
```

## 数组与字节

Java 数组是**不透明的外部值**：可以接收、判空、原样传给需要兼容数组类的 Java 成员，
也可以经另一次调用返回。重载会区分 `byte[]`、`int[]`、`String[]` 与 `Object[]`，数组
协变跟随真实 JVM 类。Sprig 没有数组字面量、类型标注、索引、赋值或遍历语法；这些写法
会在 `javac` 之前被拒绝（`SPR-SYNTAX-ERROR` 或 `SPR-TYPE-OPERAND`）。变长参数是另一种
调用契约，仍然不支持。

`byte[]` 是最常见的二进制边界，`sprig.runtime.jvm.HostBytes` 提供显式 helper：

```text
HostBytes.utf8(String) -> byte[]
HostBytes.utf8String(byte[]) -> String   # 严格 UTF-8；畸形字节报运行时错误
HostBytes.length(byte[]) -> Int
HostBytes.hex(byte[]) -> String          # 小写十六进制
```

转换永远是显式的，`byte[]` 始终是 JVM 值。

## 具体 Java 泛型

显式类型实参可以应用到导入的 Java 类和泛型方法：

```sprig
import java.util.ArrayList as ArrayList

let values = ArrayList[String]()
values.add("x")
let first = values.get(0)          # String?
```

`Box[String]`、`List[Map[String, Int32]]` 与 `Host.method[String](value)` 会保留具体
实参；类类型变量沿接收者的继承层级解析（`Source[String]` 经由 `StringSource` 也成立）。
Java 引用结果保守可空，因此 `ArrayList[String].get` 是 `String?`。方法类型参数不做推断：
泛型方法必须显式写实参。raw 证据不会升级成具体证据：raw 泛型值不能赋给或传入具体参数化
类型，`ArrayList[String]` 可以当 `List[String]` 使用，`ArrayList[Int32]` 不行。wildcard
与泛型数组（`T[]`）被拒绝并给出稳定 `interopReasonCodes`。

## 显式集合适配器

Java 集合不会隐式转换为 Sprig 集合，Sprig `List`/`Map` 也不会被悄悄当作 Java 集合传入。
适配器在 `@std/jvm.spr` 中显式调用：

```sprig
import "@std/jvm.spr" as jvm

let foreign = SomeJavaApi.names()
if foreign != null:
    let names = jvm.list_snapshot[String](foreign)   # 不可变 Sprig List[String]
    let copy = jvm.list_copy[String](names)          # 独立的 java.util.ArrayList
    SomeJavaApi.acceptNames(copy)
```

`list_snapshot`/`map_snapshot` 按顺序复制并校验非空内容：Java 的 null 元素/键/值会报
运行时错误，而不是泄漏进非空 Sprig 集合。`list_copy`/`map_copy` 生成独立的
`ArrayList`/`LinkedHashMap`；之后任意一侧的修改都不会影响另一侧。元素类型与源绑定，
类型不符会在检查阶段被拒绝。

## 先查询，再包装

写集成代码前先查 `sprig api`：它会给出 `interopLevel`、`interopReasonCodes`、
`adaptation` 与递归泛型形状。需要把生态类带进 Sprig 时用 `sprig wrap` 生成**普通可
编辑源码**（默认不覆盖，`--force` 才替换，写出前先在同一 classpath 下检查）：

```bash
sprig api com.example.Client --classpath lib/client.jar --json
sprig wrap com.example.Client --out src/main/sprig/client.spr --classpath lib/client.jar --json
```

完整策略见 [wrapper 生成器（英文）](/en/reference/jvm/wrap)。

## 尚未覆盖的部分

- **变长参数**：仍是不同的调用契约，不支持。
- **source 数组语法**：没有数组字面量/标注/索引/遍历；数组只能作为不透明值传递。
- **wildcard 形状**：当前 profile 不表达，含 wildcard 的成员以结构化原因被拒绝；
  Brigadier 等嵌套 builder 需要一个窄 Java adapter（见
  [框架集成](/guide/fabric)）。
- **泛型推断**：没有推断与型变，类型实参必须显式。
- **注解**：不读取 Java type-use 可空性注解。
- **JVM 内部运算**：Java 方法里的 `int` 溢出不会触发 Sprig 的受检数值错误。
- `short`/`byte` 形参需要显式受检转换，目前尚未提供；请改传 `Int32`。

完整边界见[已知限制](/en/reference/language/known-limitations)与
[已知限制（英文原文）](/en/reference/language/known-limitations)。
