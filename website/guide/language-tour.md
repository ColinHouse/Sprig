# 语言速查

这页把 Sprig 的语法从头到尾过一遍，适合已经会一门编程语言、想快速上手的人。第一次接触 Sprig 的话，建议先做[入门教程](/tutorial/)，再回来查这页。

页面上的每段代码都是仓库里 `website/snippets/` 下的真实文件，文档检查时会实际编译运行。想确认某个特性有没有实现，以[已实现功能清单（英文）](/en/reference/language/feature-status)为准。

## 缩进和注释

代码块靠缩进区分，和 Python 一样：

- 只能用空格缩进，用 Tab 会报 `SPR-LEX-TAB`。同一个块里的缩进要一致，空几格由你定。
- 文件里第一行代码要从第 1 列开始写。
- 在没有开始新块的地方缩进，或者 `else:`、`if count > 0:` 这样的块头下面没有缩进的代码，报错会直接说明是哪种情况。
- 圆括号、方括号、花括号里面可以随意换行，所以长的调用和字面量可以拆成多行。
- `#` 后面是注释。

## 变量：let 和 var

<<< @/snippets/variables.spr

- `let` 绑定之后不能再改，`var` 可以。
- 局部变量可以不写类型，Sprig 会从等号右边推断。类的字段则必须写类型。
- 把数字或其他值放进文本，直接用 `+`：`"visits " + visits`，不需要先转换。只有可能为 `null` 的值要先检查，见[可空值](#可空值)。
- 条件必须是 `Bool`。`0`、空字符串都不会被当成 false，`if count:` 会报 `SPR-TYPE-CONDITION`。

## 函数

<<< @/snippets/functions.spr

- 参数和返回值都要写类型。没有返回值就写 `-> Unit`。
- 调用函数时按位置传参。创建对象时则要写字段名，见下面的「类」。
- 可能失败的函数要在签名里写 `throws`，见「错误处理」。

## 类

<<< @/snippets/classes.spr

- 字段用 `let`（创建后不能改）或 `var`（可以改）声明，可以带默认值。
- 只有 `let` 字段、没有方法的小类可以写成一行：`class Position(x: Int, y: Int)`。它和块写法是同一种类；要 `var` 字段、默认值或方法，就用块写法。
- 方法**没有函数体**的类是契约（contract）。另一个类写 `conform Console to Sink`，就必须有契约里的每个方法、类型完全一致；之后 `Console` 的值可以放在任何要 `Sink` 的地方，通过契约的方法使用。契约没有字段、没有默认实现、不能构造，也不能反向转换。例子：

<<< @/snippets/contracts.spr
- 创建对象时必须写字段名：`Hero(name="Ada", health=80)`。少写、写错名字、重复写，都会编译报错。
- 方法里直接写字段名就能访问当前对象的字段，不用加前缀。
- 参数和局部变量不能和字段同名。
- 两个对象用 `==` 比较，问的是「是不是同一个对象」。两个 `Point(x=1, y=2)` 字段完全一样，也不相等：改了其中一个，另一个不会跟着变。想比较内容，就比较你关心的字段（`a.id == b.id`），或者改用 variant。

## enum、variant 和 match

<<< @/snippets/variants.spr

- `enum` 的每个值都不带数据，比如 `Mode.Fast`。
- `variant` 的每种情况可以带自己的字段，字段不可变。比如 `Expr.Add` 带着 `left` 和 `right`。
- `case Expr.Add as node:` 把匹配到的值绑定到 `node`，然后就能读它的字段。
- variant 和 enum 的值按内容比较：`Shape.Circle(radius=1.0) == Shape.Circle(radius=1.0)` 是 `true`。字符串、数字、列表和 Map 也一样。
- `match` 要把每种情况都写出来。没有 `default`，没有通配分支，也不会贯穿到下一个分支。漏写、重复、写了不可能出现的情况，都会编译报错。
- `match` 可以当语句用，每个分支写多行；也可以当表达式用（比如 `return match ...`），这时每个分支只能写一个表达式。

穷尽检查在改代码时最有用：给 variant 加一种情况，所有没处理它的 `match` 都会报错，一个都漏不掉。仓库里的 `tests/visitor/ast_visitor.spr` 是一个用 Sprig 写的小型 AST 解释器，测试时会实际运行，靠的就是这一点。

## 用 if 选一个值

<<< @/snippets/if_expressions.spr

- 能写表达式 `match` 的地方，`if` 也能产生一个值：`=`、`return`、`throw` 后面，以及 lambda 的函数体。每个分支是单独一行、缩进的一个表达式，`elif` 和 `else` 跟 `if` 开头的那一行对齐。
- `else` 分支必须写，漏了会报 "An if expression needs an else branch"；在你补上之前，文件的其余部分照常检查，编辑器功能也不受影响。不需要产生值的时候，就写普通的 `if` 语句：写在语句开头的 `if` 总是 `if` 语句。
- 所有分支是同一个类型：这个位置要求的类型，比如 `let ratio: Float = if ...`；没有要求的话，就是第一个不是 `null` 的分支的类型。有 `null` 分支，结果就是可空类型。
- 没有 Python 的 `a if c else b`，也没有 C 的 `c ? a : b`，编译器会提示你改用 `if` 表达式。
- 圆括号里的换行会被忽略，所以 `if` 表达式不能直接写进调用里。先用 `let` 绑定，再把名字传进去。细节见 [if 表达式（英文）](/en/reference/language/if-expressions)。

## 集合

<<< @/snippets/collections.spr

- `List[T]` 和 `Map[K, V]` 是只读的，要修改就用 `MutableList[T]` 和 `MutableMap[K, V]`。对只读集合做修改会报 `SPR-COLLECTION-IMMUTABLE`。
- `toMutableList()`、`toList()`、`toMutableMap()`、`toMap()` 都会复制出一个新的集合（只复制外面这一层），原来的集合不受影响。
- 用 `let` 声明列表时如果不写类型，得到的是 `MutableList`。它可以直接传给参数类型为 `List` 的函数：函数看到的是同一个列表的只读视角，所以之后通过可变名字 `append` 的元素它也看得到；需要快照就调用 `toList()`。反过来 `List` 不会自动变成 `MutableList`，要用 `toMutableList()`。
- 按键从 `Map` 里取值，得到的是可空类型，键不存在时是 `null`。
- 支持下标、`in`、`get`、`set`、`append`、`sort`，以及 `map`、`filter`、`forEach` 这几个接收 lambda 的方法。
- `Map` 的键不能是浮点数，因为 `NaN` 和正负零在相等比较和哈希上对不上。

按某个字段排序、分组、汇总或查找，用标准库的 `@std/lists`（v0.6.0-beta.1 新增）：

<<< @/snippets/guide/lists_group.spr

```text
Coffee
Lunch
Taxi
food: 3050
transport: 3600
first ride: Taxi
true
2
```

- `sort_by` 是稳定排序，排序的键要能比较大小（见[泛型](/guide/generics)里的 `Comparable`）。`sorted` 给本身就能比较大小的元素排序，比如 `List[String]`。
- `group_by` 按键第一次出现的顺序分组，每组里保持原来的顺序；键用 `==` 比较。
- `sum_by` 把每个元素算出的一个 `Int` 加起来，`sum` 把 `List[Int]` 加起来。
- `find` 返回第一个通过检查的元素，一个都没有就返回 `null`。`any`、`all`、`count` 接收同样的检查函数。
- `fold` 从左到右把元素累积成一个结果，上面几个函数做不了的再用它。
- `first`、`last`、`take`、`drop`、`reversed`、`distinct`、`index_of`、`enumerate`、`zip` 负责列表上的小活；`@std/sets` 提供 `Set[T]`（`sets.of[String]([...])`、`has`、`add`、`union`）；`@std/random`、`@std/regex`、`@std/dates` 各自包了一个 JDK 设施。每个函数的说明见[标准库说明（英文）](https://github.com/ColinHouse/Sprig/blob/main/docs/projects/standard-library.md)。

## 可空值

<<< @/snippets/nullable.spr

- 可能没有值的类型写成 `T?`。`null` 只能赋给 `T?`。
- 用之前先检查。在 `if x != null:` 的分支里，`x` 就当作有值。从 v0.6.0-beta.1 开始，`if x != null and x.length() > 3:` 这样写也可以。
- `elif` 和 `else` 知道前面的条件都不成立：`if x == null:` 之后的 `elif flag:` 分支和 `else` 分支里，`x` 都当作有值，不用再检查一遍。再写一次 `x != null` 也不报错，只是多余。
- 提前返回也行：写了 `if x == null: return ...` 之后，后面的代码都把 `x` 当作有值。前面几个分支都提前返回的 `if/elif` 链也一样。
- 把可能为 `null` 的值用在需要非空的地方，会报 `SPR-TYPE-NULLABLE`。
- `var` 字段检查过之后，只要中间调用了函数，之前的检查就不算数了，因为函数可能改了它。
- Java 方法返回的对象一律当作可能为 `null`（`toString()` 的结果除外），见 [JVM 互操作](/guide/jvm-interop)。

如果只是想要一个默认值，或者没有值就报错，用 `@std/nulls`（v0.6.0-beta.1 新增）可以省掉 `if`：

<<< @/snippets/guide/nulls_fallback.spr

```text
12
0
{tea: 2, rice: 1}
8080
port must be a number: eighty
```

- `or_else` 返回这个值；值是 `null` 时返回你给的默认值。
- `require` 返回这个值；值是 `null` 时抛出 `Error`，消息由你来写。
- 两个函数都不用在方括号里写类型：和其他泛型调用一样，类型从你传的值里来。（这比 v0.7.1-beta.1 新；在 v0.7.1-beta.1 里要写成 `nulls.or_else[Int](...)`。）

## 错误处理

<<< @/snippets/errors.spr

- 函数在签名里用 `throws` 写明会抛出哪种错误。
- 调用它的地方二选一：用 `try` / `catch` 处理（可以再加 `finally`），或者在自己的签名里也写上 `throws`。两样都不做，会报 `SPR-FLOW-THROWS`。
- `Error` 有一个 `message` 字段。`print(problem)`、`"failed: " + problem` 和 `problem.toString()` 显示的也都是这条消息。
- Java 的受检异常也能这样捕获，`catch` 后面写导入的 Java 异常类就行。这类异常按 Java 的格式显示，类名在前；它的 `message` 是 `String?`，因为 Java 的 `getMessage()` 可能返回 `null`。
- 自己的错误类型叫错误类：一个带 `message: String` 字段的类，再加一句 `conform NotFound to Error(message)`。它可以抛出、在签名里写 `throws NotFound`、按名字捕获来读它的字段，也可以用 `catch problem: Error:` 一次接住所有种类。写在 `catch Error` 之后的 `catch NotFound` 永远跑不到，编译器会报出来。

<<< @/snippets/error_classes.spr

## 泛型

<<< @/snippets/generics.spr

你自己写的类、variant 和函数可以放进 `generic T:`（或 `generic K, V:`）块里。调用时类型参数由实参算出来，所以 `Box(value=42)` 就是 `Box[Int]`（这比 v0.7.1-beta.1 新，那个版本要写 `Box[Int](value=42)`）。实参说明不了的时候，比如传的是空列表，就自己写出来：`lists.first[String]([])`。泛型没有协变和逆变。要对类型参数用 `==`，需要在函数开头写 `requires T: Equatable`；要比较大小，写 `requires T: Comparable`。详细规则见[泛型](/guide/generics)。

## Lambda

<<< @/snippets/lambdas.spr

- lambda 是一个表达式：`fn(x: Int) => x * 2`。
- 参数 0 到 3 个，函数体只能是一个表达式。函数体里可以调用会抛出 `Error` 的函数，这时 lambda 的类型会带上这一点（见下面的「函数类型」）。
- lambda 不能捕获 `var` 局部变量，否则报 `SPR-TYPE-CAPTURE`。先把值复制到一个 `let` 里再用。
- 具名函数、模块函数和方法不加括号就是函数值：`items.map(shout)`、`lists.sum`、`counter.bump`。它的类型就是转发它的 lambda 的类型，会不会抛 `Error` 也一样。方法引用在创建时就把接收者算好，之后接收者变量再变也不影响。泛型函数要写出类型参数：`identity[Int]`。`print` 只在要 `fn(T) -> Unit` 的地方可以当值用，比如 `items.forEach(print)`。Java 方法和内置方法仍然要写 lambda。函数值不能用 `==` 比较。

<<< @/snippets/function_references.spr

## 函数类型

<<< @/snippets/function_types.spr

- `fn(Int) -> Int` 是**类型**，`fn(x: Int) => x + 1` 是**值**。
- 参数 0 到 3 个。参数和返回类型必须完全一致：没有函数子类型，也不会自动转换。
- 整个函数可空时要加括号：`(fn(Int) -> Int)?`。而 `fn(Int) -> Int?` 表示返回值可空。可空的函数值和其他可空值一样，调用前先判空。
- 函数类型可以用在变量、字段、参数和返回值上，也可以作为显式的泛型参数。
- 函数类型末尾可以写 `throws Error`，而且只能是 `Error`：`fn(String) -> Int throws Error`。调用了会抛 `Error` 的函数的 lambda 就是这个类型；调用这样的值和调用任何会抛错的函数一样，要么自己声明 `throws Error`，要么 `try`/`catch`。没有这个子句的值可以用在要求这个子句的地方，反过来不行（`SPR-TYPE-CALLABLE-THROWS`）。Java 的受检异常不能穿过函数值，要在具名函数里处理掉。
- 只负责把回调的错误往外传的函数，用 `rethrows` 代替 `throws`：`func twice(step: fn(Int) -> Int throws Error, value: Int) -> Int rethrows:`。调用它时抛出的正好就是你传进去的 lambda 会抛的东西，所以传一个不会失败的 lambda 就是普通调用。`@std/lists` 里的函数都是这样写的。

<<< @/snippets/callable_throws.spr

```text
[1, 2]
caught: not a number: x
18
21
caught: not a number: three
```

函数值可以传给 Java 期望函数式接口的参数（最多三个参数，比如 `Comparator`、`Consumer`、`Runnable`），也可以传给 Sprig 自带的 `sprig.runtime.Fn0` 到 `Fn3`，见 [JVM 互操作](/guide/jvm-interop)。不确定的时候，用 `sprig api <类名> --json` 查一下实际签名。

## 在 JSON 里查找字段

<<< @/snippets/json_lookup.spr

`json.find_member` 的结果有三种：`Missing`（没有这个键）、`Found`（有，值在 `value` 里）和 `NotObject`（查的不是对象）。键存在但值是 `null`、`false`、`0` 或空字符串时，结果都是 `Found`，不会和「没有这个键」混在一起。对象里有重复的键会抛出 `Error`；成员保持原来的顺序。解析、查找和序列化的完整规则见[标准库说明（英文）](https://github.com/ColinHouse/Sprig/blob/main/docs/projects/standard-library.md)。

要读整条记录，用 `@std/json_codec`（v0.6.0-beta.1 新增）按名字和类型取字段，不用给每个字段写一遍 `match`：

<<< @/snippets/json_fields.spr

```text
Read the tour {"id":1,"title":"Read the tour","done":true}
$[1].id: expected integer, found string
```

- `required_int`、`required_string`、`required_bool` 返回字段的值；字段不存在、是 `null` 或类型不对时抛出 `Error`。不做任何转换：字符串 `"2"` 不算整数。
- 每条错误消息开头都写明出错的位置，这里是列表的第 1 个元素的 `id` 字段。
- `optional_` 开头的版本在字段不存在或是 `null` 时返回 `null`。
- `root` 读取最外层是对象的文档，`root_array` 读取最外层是列表的文档。
- `object`、`member`、`int`、`text`、`bool` 用来构造要写回去的值。

## 模块

<<< @/snippets/modules/main.spr

<<< @/snippets/modules/math_module.spr

- `import "./file.spr" as alias` 导入另一个 Sprig 文件。
- `import` 要写在文件开头，在所有声明和语句之前。
- 每个模块只初始化一次。两个模块互相导入（导入环）会报 `SPR-NAME-IMPORT-CYCLE`。
- 导入 Java 类也是这个写法，只是换成完整类名：`import java.time.LocalDate as LocalDate`。
- 标准库以 `@std` 开头，比如 `import "@std/json.spr" as json`。

## 另外几个小功能

- 字符串必须在同一行闭合；漏掉结尾引号或把字符串换到下一行时，`SPR-LEX-STRING` 会标出这行未闭合的字符串。
- `sprig fmt` 把代码整理成统一的格式，会保留注释，没有配置项。见[格式化（英文）](/en/reference/tooling/formatter)。
- 模块可以用 `export alias.Symbol` 把导入的声明再导出，但不能借此绕过依赖包的导出范围。没有 `export *` 这样的通配导出。见[重导出（英文）](/en/reference/language/module-reexports)。
- 表达式形式的 `match` 每个分支只能写一个表达式；要写多行，就用语句形式。见 [match 表达式（英文）](/en/reference/language/match-expressions)。

## 还没有的

下面这些目前都还没有：

- 从期望的类型推断类型参数，以及协变和逆变
- 继承（开放多态用契约类，见[类](#类)）
- `%=`
- 元组和解构
- 字符串插值：`"${name}"` 只是普通文本。拼接用 `+`：两边放什么值都可以，`"count " + count` 不用写 `toString()`，显示效果和 `print` 一样。`null`、可能为 `null` 的值（比如 `Int?`）和返回 `Unit` 的调用不能拼。运算从左到右，所以 `1 + 2 + " items"` 是 `3 items`。

和 Java 打交道时，Sprig 没有数组语法，也没有自己的通配符语法。Java 数组可以原样接收和传递，变长参数方法直接把参数依次写在后面就行，Java 通配符会保留它的边界，见 [JVM 互操作](/guide/jvm-interop)。

完整列表见[已知限制（英文）](/en/reference/language/known-limitations)，后面的计划见[路线图（英文）](/en/reference/language/stage1-roadmap)。
