# 语言导览

本页介绍当前 stage-0 编译器实际实现的 Sprig。页面中的每一段示例都是
`website/snippets/` 下的真实文件，由 `tools/verify-doc-snippets.py` 在文档检查时执行。

权威文档是[语言规范（v0.7 设计）](/en/reference/LANGUAGE_SPEC)与
[已实现功能状态](/en/reference/FEATURE_STATUS_IMPLEMENTED)。当设计稿超出当前实现时，
本页以编译器实际行为为准。

## 布局与注释

代码块由缩进决定。第一个代码行必须从第 1 列开始，同一个块内缩进必须一致，
制表符会被拒绝（`SPR-LEX-TAB`）。`()`、`[]`、`{}` 内的换行会被忽略，因此调用和
字面量可以跨行书写。`#` 开始注释。

## 绑定

<<< @/snippets/variables.spr

`let` 只能绑定一次，`var` 可以重新赋值。局部绑定从初始化表达式推断类型，类字段
必须显式标注类型。Sprig 没有 truthiness：条件必须是 `Bool`。

## 函数

<<< @/snippets/functions.spr

所有具名函数和方法都要声明参数类型与返回类型，包括 `-> Unit`。调用使用位置参数。
可能失败的操作通过 `throws ErrorType` 声明（见下文「错误」）。

## 类

<<< @/snippets/classes.spr

类用 `let`（不可变）和 `var`（可变）声明字段，字段可以有默认值。构造必须使用具名
参数：`Hero(name="Ada", health=80)`；缺失、未知或重复的字段都是编译错误。方法内直接
访问当前实例的字段，参数和局部变量不允许遮蔽字段。

## enum、variant 与 match

<<< @/snippets/variants.spr

`enum` 的 case 不携带负载。`variant` 声明封闭的 sum type，case 的字段具名且不可变。
`match` 是**语句**：每个分支写一个 enum 或 variant case，可以用 `as node` 绑定负载。
缺失、重复、类型错误和不可达的分支都会报错；没有 `default` 或通配分支，也没有
fallthrough。穷尽性由编译器保证，因此给 variant 增加 case 时，所有漏掉它的 visitor
都会编译失败——仓库里的 AST visitor 实验正依赖这一点。

## 集合

<<< @/snippets/collections.spr

`List[T]` 与 `Map[K,V]` 是只读的，`MutableList[T]` 与 `MutableMap[K,V]` 可变。
`toMutableList()`、`toList()`、`toMutableMap()`、`toMap()` 会生成新的外层集合。
通过不可变类型修改集合会得到 `SPR-COLLECTION-IMMUTABLE`。索引、`in`、`get`、`set`、
`append`、`sort` 以及高阶方法 `map`/`filter`/`forEach` 均已实现。浮点数不能作为
Map 的键，因为 IEEE 相等与哈希在 `NaN` 和有符号零上不一致。

## 可空性

<<< @/snippets/nullable.spr

`null` 只能赋给 `T?`。在确认 `!= null` 的分支内，值会被收窄为非空；把可能为空的值
用于非空场景会得到 `SPR-TYPE-NULLABLE`。可变字段不会跨调用被收窄。Java 引用结果
保守地视为可空，见 [JVM 互操作](/guide/jvm-interop)。

## 错误

<<< @/snippets/errors.spr

函数用 `throws` 声明可能抛出的错误类型。调用方必须用 `try`/`catch`（可选 `finally`）
处理，或者声明同样的效果，否则会得到 `SPR-FLOW-THROWS`。`Error` 提供 `message`
字段。Java 受检异常可以作为导入的 Java 异常类被捕获。

## Lambda

<<< @/snippets/lambdas.spr

Lambda 是表达式：`fn(x: Int) => expression`，支持 0 到 3 个参数，函数体是单个表达式，
不能声明 `throws`。捕获 `var` 局部变量会被拒绝（`SPR-TYPE-CAPTURE`），请先复制到
`let` 绑定。

## 模块

<<< @/snippets/modules/main.spr

<<< @/snippets/modules/math_module.spr

`import "./file.spr" as alias` 导入另一个 Sprig 文件。import 必须出现在任何声明或
语句之前；被导入模块只初始化一次；导入环会得到 `SPR-NAME-IMPORT-CYCLE`。导入 Java
类使用同样的语法，只是换成全限定类名：`import java.time.LocalDate as LocalDate`。

## 尚未实现

用户自定义泛型、继承与接口、`match` 表达式、`%=`、元组与解构、数组、变长参数、
字符串插值、源码中的函数类型都**尚未实现**。完整列表见
[已知限制](/reference/known-limitations)，后续规划见
[Stage-1 路线图（英文）](/en/reference/STAGE1_ROADMAP)。
