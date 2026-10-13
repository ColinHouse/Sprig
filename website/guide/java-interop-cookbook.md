# Java 互操作实用配方

这页解决已经在调用 Java 时反复遇到的边界问题。导入和基本调用先看
[入门书 Java 章节](/tutorial/ch21-java)，完整规则看
[JVM 互操作参考](/en/reference/jvm/interop)。下面的程序都由文档检查编译，
成功示例实际运行并比对输出，失败示例核对稳定诊断代码。

先查真实签名：`sprig api java.util.ArrayList --member sort --json`。
第三方类要使用与程序相同的已解析项目或 `--classpath`。查看
`sprigParameterTypes`、`sprigReturnType`、`checkedExceptions`、
`interopReasonCodes` 和候选重载，不要猜 Java API。

## 可空返回值 {#nullable}

`HashMap.get` 可能找不到值，直接使用会报 `SPR-TYPE-NULLABLE`：

<<< @/snippets/interop/nullable_bad.spr

把结果绑定到 `let`，明确处理有值和无值两个分支：

<<< @/snippets/interop/nullable.spr

输出 `missing`。只有不可变绑定能收窄；来源是 `var` 时，可以先复制到 `let`，
再检查这个副本。Java 引用返回值默认可空，受支持的注解和 `toString()` 契约除外。
不要为了消除报错而给必填数据悄悄设置默认值；确实可选的数据可以用
`@std/nulls.spr` 的 `or_else`。

## 异常的传播与转换 {#exceptions}

命名函数必须捕获或声明 Java 受检异常。下面报 `SPR-FLOW-THROWS`：

<<< @/snippets/interop/exceptions_bad.spr

调用者需要 Java 异常类型时，直接声明并传播。跨 Sprig 函数值的异常只支持
`throws Error`，因此可以在命名函数中捕获 Java 异常，再有意识地转换：

<<< @/snippets/interop/exceptions.spr

输出依次为 `https://example.com` 和 `invalid URI: ::bad`。
这个例子刻意改成领域消息：`Error(message)` 不保留原始 Java 异常作为 cause。
需要保留异常身份时直接传播 Java 异常；有保留 cause 的库边界时优先使用它。
捕获的 Java 异常的 `message` 是 `String?`，`toString()` 则是非空的诊断描述。
不要用宽泛捕获把失败变成看似成功的空值。

## 集合类型与可变性 {#collections}

Sprig 集合和 Java 集合是两种类型。即使元素都是字符串，下面仍报
`SPR-JVM-MEMBER`：

<<< @/snippets/interop/collections_bad.spr

明确复制成 Java 集合，调用 Java，再取独立的 Sprig 快照：

<<< @/snippets/interop/collections.spr

输出依次为 `[b, a]`、`[a, b]`、`3`。`list_copy` 创建独立的可变 Java
`ArrayList`；`list_snapshot` 返回不可变的 Sprig `List`。之后修改 Java
集合不会改变原始集合或快照。Map 对应 `map_copy` 和 `map_snapshot`。
快照函数遇到 null 元素、键或值会抛出 `Error`，它们不是实时视图，也不深拷贝对象。

## 回调与 SAM 接口 {#callbacks}

Java 函数式接口参数可以接受参数类型精确匹配的 Sprig `fn`。
Java 无法表达 Sprig 的 `throws Error` 契约，下面报 `SPR-TYPE-CALLABLE-THROWS`：

<<< @/snippets/interop/callbacks_bad.spr

先在命名函数中处理错误，再传回调。`Comparator[String]` 可以用带明确参数类型的
lambda 和 `compareTo`，让返回值匹配 `Int32`，无需靠减法猜比较结果：

<<< @/snippets/interop/callbacks.spr

输出 `[a, bb]`，然后 `failed`；程序退出前会 join 线程。
实际项目要自行决定错误处理策略，这里打印只是示例的选择。
有状态的框架回调或多个抽象方法见 [`conform`](/en/reference/jvm/conformance)。
一般的多方法接口不是 SAM，编译器不会从一个 lambda 合成全部实现。

## 方法和构造器重载 {#overloads}

浮点容量不能匹配 `StringBuilder` 的构造器，下面报 `SPR-JVM-MEMBER`：

<<< @/snippets/interop/overloads_bad.spr

先查询，再修改：

```sh
sprig api java.lang.StringBuilder --member '<init>' --json
sprig api java.lang.Math --member max --json
```

按真实意图给参数定类型，不要仅仅为了匹配重载而把值取整：

<<< @/snippets/interop/overloads.spr

输出 `rows=3` 和 `3`。这里显式的 `Int32` 选择 `int` 形状；若有精确的
`long` 重载，`Int` 通常优先匹配它。Java 方法和构造器都用位置参数。
`Collections.emptyList[String]()` 这样的显式类型参数解决泛型参数，
不能随意解决重载平局。遇到 `SPR-JVM-AMBIGUOUS` 应检查所有同等适用的签名，
诊断中的配方链接不会替用户猜选哪个重载。

## 大字符串的构建 {#string-building}

循环中的 `result += fragment` 会复制此前累积的前缀。片段长度相近时，
复制的总量可能按平方增长。`"count=" + count` 这样的短表达式仍很方便；
逐步构造序列化结果时，应使用 builder，或连接已经持有的字符串片段：

<<< @/snippets/interop/string_building.spr

两行都输出 `oak,birch,松`。全部追加完后再调用一次 `toString()`。
`@std/text.spr` 的 `text.join` 接收字符串片段列表，保留全部片段会占用内存。
Java `StringBuilder.length()` 按 UTF-16 单元计数，Sprig String 按码点计数；
本例只用它判断是否为空，因此不受影响。Java append 对 Sprig 对象的格式化
可能不同，需要保留 Sprig 展示方式时，先显式调用该值的 `toString()` 再追加。
这里没有引入自动循环优化或改变语言语义。
限定规模的测试和局限见[可复现性能对照](/en/reference/jvm/string-building)。
