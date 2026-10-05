# 语言速查

这页把 Sprig 的语法从头到尾过一遍，适合已经会一门编程语言、想快速上手的人。第一次接触 Sprig 的话，建议先做[入门教程](/tutorial)，再回来查这页。

页面上的每段代码都是仓库里 `website/snippets/` 下的真实文件，文档检查时会实际编译运行。想确认某个特性有没有实现，以[已实现功能清单（英文）](/en/reference/language/feature-status)为准。

## 缩进和注释

代码块靠缩进区分，和 Python 一样：

- 只能用空格缩进，用 Tab 会报 `SPR-LEX-TAB`。同一个块里的缩进要一致，空几格由你定。
- 文件里第一行代码要从第 1 列开始写。
- 圆括号、方括号、花括号里面可以随意换行，所以长的调用和字面量可以拆成多行。
- `#` 后面是注释。

## 变量：let 和 var

<<< @/snippets/variables.spr

- `let` 绑定之后不能再改，`var` 可以。
- 局部变量可以不写类型，Sprig 会从等号右边推断。类的字段则必须写类型。
- 条件必须是 `Bool`。`0`、空字符串都不会被当成 false，`if count:` 会报 `SPR-TYPE-CONDITION`。

## 函数

<<< @/snippets/functions.spr

- 参数和返回值都要写类型。没有返回值就写 `-> Unit`。
- 调用函数时按位置传参。创建对象时则要写字段名，见下面的「类」。
- 可能失败的函数要在签名里写 `throws`，见「错误处理」。

## 类

<<< @/snippets/classes.spr

- 字段用 `let`（创建后不能改）或 `var`（可以改）声明，可以带默认值。
- 创建对象时必须写字段名：`Hero(name="Ada", health=80)`。少写、写错名字、重复写，都会编译报错。
- 方法里直接写字段名就能访问当前对象的字段，不用加前缀。
- 参数和局部变量不能和字段同名。

## enum、variant 和 match

<<< @/snippets/variants.spr

- `enum` 的每个值都不带数据，比如 `Mode.Fast`。
- `variant` 的每种情况可以带自己的字段，字段不可变。比如 `Expr.Add` 带着 `left` 和 `right`。
- `case Expr.Add as node:` 把匹配到的值绑定到 `node`，然后就能读它的字段。
- `match` 要把每种情况都写出来。没有 `default`，没有通配分支，也不会贯穿到下一个分支。漏写、重复、写了不可能出现的情况，都会编译报错。
- `match` 可以当语句用，每个分支写多行；也可以当表达式用（比如 `return match ...`），这时每个分支只能写一个表达式。

穷尽检查在改代码时最有用：给 variant 加一种情况，所有没处理它的 `match` 都会报错，一个都漏不掉。仓库里的 `tests/visitor/ast_visitor.spr` 是一个用 Sprig 写的小型 AST 解释器，测试时会实际运行，靠的就是这一点。

## 集合

<<< @/snippets/collections.spr

- `List[T]` 和 `Map[K, V]` 是只读的，要修改就用 `MutableList[T]` 和 `MutableMap[K, V]`。对只读集合做修改会报 `SPR-COLLECTION-IMMUTABLE`。
- `toMutableList()`、`toList()`、`toMutableMap()`、`toMap()` 都会复制出一个新的集合（只复制外面这一层），原来的集合不受影响。
- 用 `let` 声明列表时如果不写类型，得到的是 `MutableList`，不能直接传给参数类型为 `List` 的函数。要只读列表，就写成 `let xs: List[Int] = [1, 2]`。把字面量直接写在参数位置则没有问题。
- 按键从 `Map` 里取值，得到的是可空类型，键不存在时是 `null`。
- 支持下标、`in`、`get`、`set`、`append`、`sort`，以及 `map`、`filter`、`forEach` 这几个接收 lambda 的方法。
- `Map` 的键不能是浮点数，因为 `NaN` 和正负零在相等比较和哈希上对不上。

按某个字段排序、分组、汇总，用标准库的 `@std/lists`：

<<< @/snippets/guide/lists_group.spr

```text
Coffee
Lunch
Taxi
food: 3050
transport: 3600
```

- `sort_by` 是稳定排序，排序的键要能比较大小（见[泛型](/guide/generics)里的 `Comparable`）。
- `group_by` 按键第一次出现的顺序分组，每组里保持原来的顺序；键用 `==` 比较。
- `fold` 从左到右把元素累积成一个结果，求和、计数都可以用它。

## 可空值

<<< @/snippets/nullable.spr

- 可能没有值的类型写成 `T?`。`null` 只能赋给 `T?`。
- 用之前先检查。在 `if x != null:` 的分支里，`x` 就当作有值。比 v0.5.0-beta.1 更新的版本里，`if x != null and x.length() > 3:` 这样写也可以。
- 提前返回也行：写了 `if x == null: return ...` 之后，后面的代码都把 `x` 当作有值。
- 把可能为 `null` 的值用在需要非空的地方，会报 `SPR-TYPE-NULLABLE`。
- `var` 字段检查过之后，只要中间调用了函数，之前的检查就不算数了，因为函数可能改了它。
- Java 方法返回的对象一律当作可能为 `null`，见 [JVM 互操作](/guide/jvm-interop)。

## 错误处理

<<< @/snippets/errors.spr

- 函数在签名里用 `throws` 写明会抛出哪种错误。
- 调用它的地方二选一：用 `try` / `catch` 处理（可以再加 `finally`），或者在自己的签名里也写上 `throws`。两样都不做，会报 `SPR-FLOW-THROWS`。
- `Error` 有一个 `message` 字段。
- Java 的受检异常也能这样捕获，`catch` 后面写导入的 Java 异常类就行。

## 泛型

<<< @/snippets/generics.spr

你自己写的类、variant 和函数可以放进 `generic T:`（或 `generic K, V:`）块里。用的时候每次都要写出类型参数，比如 `Box[Int](value=42)`。Sprig 不推断类型参数，泛型也没有协变和逆变。要对类型参数用 `==`，需要在函数开头写 `requires T: Equatable`；要比较大小，写 `requires T: Comparable`。详细规则见[泛型](/guide/generics)。

## Lambda

<<< @/snippets/lambdas.spr

- lambda 是一个表达式：`fn(x: Int) => x * 2`。
- 参数 0 到 3 个，函数体只能是一个表达式，不能声明 `throws`。
- lambda 不能捕获 `var` 局部变量，否则报 `SPR-TYPE-CAPTURE`。先把值复制到一个 `let` 里再用。

## 函数类型

<<< @/snippets/function_types.spr

- `fn(Int) -> Int` 是**类型**，`fn(x: Int) => x + 1` 是**值**。
- 参数 0 到 3 个。参数和返回类型必须完全一致：没有函数子类型，也不会自动转换。
- 整个函数可空时要加括号：`(fn(Int) -> Int)?`。而 `fn(Int) -> Int?` 表示返回值可空。可空的函数值和其他可空值一样，调用前先判空。
- 函数类型可以用在变量、字段、参数和返回值上，也可以作为显式的泛型参数。
- 函数类型不能声明 `throws`，所以里面可能出现的受检错误要在函数内部处理掉。

函数值传给 Java 时，只能传给参数类型是 Sprig 自带的 `sprig.runtime.Fn0` 到 `Fn3` 的方法，不会自动转换成 Java 的 `Function`、`Consumer`、`Runnable` 或其他接口。不确定的时候，用 `sprig api <类名> --json` 查一下实际签名。

## 在 JSON 里查找字段

<<< @/snippets/json_lookup.spr

`json.find_member` 的结果有三种：`Missing`（没有这个键）、`Found`（有，值在 `value` 里）和 `NotObject`（查的不是对象）。键存在但值是 `null`、`false`、`0` 或空字符串时，结果都是 `Found`，不会和「没有这个键」混在一起。对象里有重复的键会抛出 `Error`；成员保持原来的顺序。解析、查找和序列化的完整规则见[标准库说明（英文）](https://github.com/ColinHouse/Sprig/blob/main/docs/projects/standard-library.md)。

## 模块

<<< @/snippets/modules/main.spr

<<< @/snippets/modules/math_module.spr

- `import "./file.spr" as alias` 导入另一个 Sprig 文件。
- `import` 要写在文件开头，在所有声明和语句之前。
- 每个模块只初始化一次。两个模块互相导入（导入环）会报 `SPR-NAME-IMPORT-CYCLE`。
- 导入 Java 类也是这个写法，只是换成完整类名：`import java.time.LocalDate as LocalDate`。
- 标准库以 `@std` 开头，比如 `import "@std/json.spr" as json`。

## 另外几个小功能

- `sprig fmt` 把代码整理成统一的格式，会保留注释，没有配置项。见[格式化（英文）](/en/reference/tooling/formatter)。
- 模块可以用 `export alias.Symbol` 把导入的声明再导出，但不能借此绕过依赖包的导出范围。没有 `export *` 这样的通配导出。见[重导出（英文）](/en/reference/language/module-reexports)。
- 表达式形式的 `match` 每个分支只能写一个表达式；要写多行，就用语句形式。见 [match 表达式（英文）](/en/reference/language/match-expressions)。

## 还没有的

下面这些目前都还没有：

- 泛型推断、协变和逆变
- 继承和接口
- `%=`
- 元组和解构
- 字符串插值：`"${name}"` 只是普通文本，拼接字符串用 `+`

和 Java 打交道时，Sprig 没有数组语法，也不支持变长参数和通配符类型。Java 数组本身可以原样接收和传递，见 [JVM 互操作](/guide/jvm-interop)。

完整列表见[已知限制（英文）](/en/reference/language/known-limitations)，后面的计划见[路线图（英文）](/en/reference/language/stage1-roadmap)。
