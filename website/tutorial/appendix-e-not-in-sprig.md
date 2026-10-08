# 附录 E. Sprig 没有的东西

这份附录收集 Sprig 有意省略的语言功能。它们不是"还没做完"，而是设计决定：每个省略都配一个替代写法。权威清单是 `sprig capabilities --json` 里的 `unsupportedSyntax` 和 `featureGuidance`；本页按主题整理，并给出可运行的例子。

## E.1 继承

没有 `extends`。类型之间没有父类和子类，方法不会被"重写"。

替代写法：

- **契约类**：一个方法全部没有函数体的类是契约；实现类用 `conform` 接上，之后可以当契约类型使用。
- **组合**：类里放另一个类的字段，把工作转交给它。
- **variant**：种类固定、需要按种类分支时，用 variant 加 `match`。

<<< @/snippets/book/appendix_e_inheritance.spr

```text
Hello, Ada
```

`English` 没有从 `Greeter` 继承；它只是满足契约，所以能赋给 `Greeter` 类型的变量。契约是"开放的集合"（谁都可以 conform），variant 是"封闭的集合"（case 写死在声明里）。

## E.2 async/await 和 Promise

没有 `async`、`await`、`Promise`，也没有"染色函数"：函数要么是普通函数，要么不存在。

替代写法是标准库的 `@std/concurrent`：`spawn` 把普通函数放到 JDK 的虚拟线程上跑，`scope` 负责等所有任务结束。阻塞的 Java 调用（HTTP、JDBC、socket）原样放进任务即可，不需要改写。

<<< @/snippets/book/appendix_e_concurrency.spr

```text
[2, 4, 6]
42
```

- `parallel_map` 对每个元素开一个任务，按原顺序返回结果。
- `spawn` 返回 `Task[T]`，`await()` 取回值；`scope` 结束时所有任务都已结束。
- 任务的第一个失败会取消其余任务，并由 `scope` 重新抛出。

第 22 章会讲完整的 `channel`、`counter`、`lock` 和线程池。

## E.3 元组和解构

没有元组类型，也没有 `let (a, b) = ...` 这样的解构。

替代写法：**一行类**给字段起名字，取字段就是解构。字段少、语义明确时比元组更清楚；字段有意义的名字可以直接当文档用。

<<< @/snippets/book/appendix_e_tuples.spr

```text
1
2
```

`Pair(first=1, second=2)` 的构造用命名参数，读的时候写 `p.first`、`p.second`。需要"多种形状"时用 variant 的命名载荷。

## E.4 字符串插值

没有 `f"{name}"`、没有模板字符串、也没有 `${}` 语法。

替代写法是 `+`：只要一侧是 `String`，另一侧的值就会变成它的文字。

<<< @/snippets/book/appendix_e_interpolation.spr

```text
name: Ada, age: 36
```

拼接从左往右算，所以 `1 + 2 + " then " + 1 + 2` 是 `3 then 12`（见附录 A）。可能为 `null` 的值不能直接拼，要先检查或给它一个兜底值。

## E.5 `Char` 类型

没有单独的字符类型。

替代写法：**一个文字元素就是只有一个 Unicode 码点的 `String`**。`length()`、下标、`charAt`、`substring` 和 `for` 都按码点工作。

<<< @/snippets/book/appendix_e_char.spr

```text
3
A
😀
東
```

`"A😀東"` 的长度是 3，不是 UTF-16 码元数 4；`text[1]` 和 `charAt(1)` 都是完整的 `"😀"`。需要码点时用 `codeAt`，还原用 `String.fromCode`（第 4 章）。

## E.6 块状 lambda

lambda 的右边只能是一个表达式，不能是缩进的多行块。函数式接口的 `fn` 最多三个参数。

替代写法：

- 把逻辑写成**命名函数**，再把它当值传：`items.map(convert)`（第 15 章）。
- 需要多行时，命名函数里用普通语句；lambda 里只留一个表达式。
- 短的表达式 lambda 仍然可以：`fn(x: Int) => x * 2`。

<<< @/snippets/book/appendix_e_block_lambda.spr

```text
[1, 4, 9]
```

## E.7 match 的通配分支

`match` 必须列出每一个 enum/variant case；没有 `case _:`，没有 default。

替代写法：**把每个 case 都写出来**。加一个 case 时，所有忘了处理它的 `match` 会在编译时报错，这正是这个设计的目的。

<<< @/snippets/book/appendix_e_wildcard.spr

```text
go
```

写 `case _:` 会被拒绝，报错会解释"match 只用于 enum 和 variant，没有类型测试"：

<<< @/snippets/book/appendix_e_wildcard_fail.spr

```text
SPR-MATCH-UNKNOWN-CASE [TYPE] main.spr:11:14: Match case must be written as Type.Case
  hint: match is for enums and variants; there are no type tests on class or contract values. A closed set of types is a variant (declare one with a case per type and match on it); an open set is a contract (call its methods instead of testing the type).
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

需要"兜底行为"时，把它写成一个普通的 `case`，或者给 variant 加一个明确的 case（比如 `Unknown`）。

## E.8 运算符重载

不能给 `+`、`==` 定义自己的含义。

替代写法：**具名方法**。`a.plus(b)` 比 `a + b` 更长，但读者知道它做了什么；而且 `==` 的行为因此永远一致：按值或按同一性，看类型（第 11 章）。

<<< @/snippets/book/appendix_e_operator_overloading.spr

```text
(4, 6)
```

## E.9 管道、注解、装饰器和宏

| 没有 | 替代写法 |
|---|---|
| 管道运算符 `\|>` | 普通语句，或者嵌套调用 |
| 注解 `@Foo` | 显式写出来的类型化数据，或者普通函数 |
| 装饰器 | 普通函数和模块 |
| 宏 | 普通函数和模块 |
| 基于反射的结构描述（比如自动把任意对象映射成 JSON） | 显式写出结构：字段列表和构造 JSON 的代码 |

“显式”在这里是有意的：代码里能读到的东西，编译器和读者才能一起检查。

## E.10 数组、varargs 和 Java 函数式接口

- **Sprig 源码里没有数组**。日常用 `List[T]`；需要给 Java 传数组时，Java 数组作为不透明值通过（`byte[]` 有 `HostBytes` 辅助），或者用 `@std/jvm` 的适配器。数组的细节见[附录 F](/tutorial/appendix-f-advanced-java)。
- **Sprig 不能声明 varargs**（`func f(xs: Int...)` 不存在），但调用 Java 的 varargs 可以：末尾参数会打包成数组，也可以显式传一个数组。
- **Java 函数式接口最多支持三个参数**；遇到超过三个参数或类型变量的 varargs（`T...`），要走具名包装或换一个 Java API。

## E.11 泛型变型

泛型是**不变**的：`Box[Cat]` 不是 `Box[Animal]`，即使 `Cat` 可以当 `Animal` 用。

替代写法：想清楚类型参数应该写哪一个；需要转换时写显式的转换函数（第 16 章）。

## E.12 从期望类型推断

类型参数只从**调用的实参**推断，绝不从"你希望它是什么"（赋值目标、返回值、字段类型）推断。

- `let box = Box(value=41)` 可以：`41` 说了 `T` 是 `Int`。
- `let xs: List[Int] = []` 必须写出类型：空的 `[]` 什么都不说。
- `identity(1)` 从实参推断；说不清时写全 `identity[Int](value=1)`。

## E.13 中央注册表

没有官方托管、需要登录上传的中央包仓库。

替代写法：依赖可以来自**本地路径**或 **Git**；包注册表只是一个目录/仓库里的索引（`packages/NAME.toml`），写明每个包的 Git 地址和版本，发布就是向该索引提交一个 pull request。第 18 章会讲怎么声明和使用。

## 相关章节

- [第 11 章：类和对象](/tutorial/ch11-classes)：一行类和同一性。
- [第 12 章：枚举、variant 和 match](/tutorial/ch12-enums-variants)：封闭集合和穷尽匹配。
- [第 15 章：函数作为值](/tutorial/ch15-functions-as-values)：命名函数引用和 lambda。
- [第 16 章：泛型与契约类](/tutorial/ch16-generics-contracts)：`generic`、`requires`、用契约代替继承。
- [第 18 章：模块、项目和依赖](/tutorial/ch18-modules-projects)：路径、Git 和注册表。
- [第 22 章：并发](/tutorial/ch22-concurrency)：`@std/concurrent` 的完整用法。
- [附录 F：Java 互操作进阶](/tutorial/appendix-f-advanced-java)：数组、通配和父类视图。
