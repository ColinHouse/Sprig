# JVM 互操作

Sprig 通过生成 Java 源码来编译，因此可以用显式 import 别名调用 JDK 类。下面的规则
有意保持保守：Java 不提供可空信息时，Sprig 一律假设该值可能为 `null`。

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
`Object` 形式参数是文档中说明的例外。编译器不读取 type-use 可空性注解，这套规则
不依赖注解。

## 受检异常

Java 受检异常可以出现在 Sprig 的 `throws` 子句中并被捕获：

<<< @/snippets/jvm_exceptions.spr

```text
String
class not found: does.not.Exist
```

## 互操作尚未覆盖的部分

- Java 泛型签名和泛型集合元素类型没有被完整解释；Java 集合不会自动转换为 Sprig 的
  `List`/`Map`。
- 数组、变长参数和注解支持有限或尚未支持。
- 不读取 Java 的 type-use 可空性注解。
- JVM 内部运算不受 Sprig 受检数值规则保护：Java 方法里的 `int` 溢出不会触发 Sprig
  的数值错误。
- `short`/`byte` 形参需要显式受检转换，目前尚未提供；请改传 `Int32`。

这些边界记录在[已知限制](/reference/known-limitations)与
[数值语义（英文）](/en/reference/NUMERIC_SEMANTICS)。
