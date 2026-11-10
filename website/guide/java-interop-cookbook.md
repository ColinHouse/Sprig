# Java 互操作实用配方

代码和 Java 打交道时，有五件事会反复遇到：可能为 `null` 的返回值、受检异常、集合、回调，还有重载的方法。下面每个配方都先给出编译器会拒绝的写法和它的报错，再给出能用的写法。这页的每个程序都由文档检查实际运行，程序下面的输出块会和真实输出逐字比对。

还没在 Sprig 里调用过 Java 的话，先看[入门书的 Java 一章](/tutorial/ch21-java)。完整的规则在 [JVM 互操作参考（英文）](/en/reference/jvm/interop)。

有个习惯对这五件事都有用：签名先查再写，不要猜。

```sh
sprig api java.util.ArrayList --member sort
```

它会列出每个重载在 Java 里的声明和在 Sprig 里的样子。加 `--json` 可以拿到给程序读的格式（`sprigParameterTypes`、`sprigReturnType`、`checkedExceptions`、`interopReasonCodes`）。JDK 之外的库，要在解析过依赖的项目里运行，或者加 `--classpath`。

## 可能为 null 的返回值 {#nullable}

键不存在时 `HashMap.get` 什么也拿不到，所以 Sprig 把它的结果当作 `String?`，不让你直接当 `String` 用：

<<< @/snippets/interop/nullable_bad.spr

```text
SPR-TYPE-NULLABLE [TYPE] main.spr:4:12: Cannot access 'length' on a value that may be null (receiver type String?)
  hint: Bind the receiver to a let and check it with 'if value != null:' before using 'length'.
```

把结果放进 `let`，两种情况各自怎么办都写出来：

<<< @/snippets/interop/nullable.spr

```text
missing
```

- 只有 `let` 会收窄。值在 `var` 里的话，先复制到一个 `let`，再检查这个副本。
- Java 返回的引用默认可能为 `null`，除非库用注解标明不会为空。`toString()` 的结果总是 `String`。
- 值确实可有可无时，可以用 `@std/nulls.spr` 的 `or_else` 给个默认值。不要为了让报错消失就随手加默认值：数据是必需的，缺了就是一个应该报告的失败。

## 受检异常 {#exceptions}

`URI(text)` 声明了 `URISyntaxException`。具名函数要么捕获它，要么声明它：

<<< @/snippets/interop/exceptions_bad.spr

```text
SPR-FLOW-THROWS [FLOW] main.spr:4:12: Call may throw URISyntaxException; declare 'throws URISyntaxException' or handle it with try/catch
```

下面的程序把两种做法都用上了。`parse_uri` 声明这个 Java 异常并让它继续往外传，调用方还能按它的类型来捕获。`parse_for_callback` 捕获它，再抛出一个带自己消息的 `Error`：

<<< @/snippets/interop/exceptions.spr

```text
https://example.com
invalid URI: ::bad
```

- 函数要被当成值传来传去时，就转成 `Error`。函数类型后面只能写 `throws Error`，所以 Java 的受检异常过不了函数值这一关。
- `Error(message)` 只保留消息，不保留背后的 Java 异常。调用方需要原来那个异常时，就声明它、让它继续传播。
- 捕获到的 Java 异常，`message` 是 `String?`，因为 `getMessage()` 可能返回 `null`。`problem.toString()` 总是 `String`。

## 集合 {#collections}

Sprig 的列表不是 `java.util.List`，哪怕两边装的都是字符串：

<<< @/snippets/interop/collections_bad.spr

```text
SPR-JVM-MEMBER [JVM] main.spr:2:1: Java class Collections has no method 'sort' matching 1 argument(s)
  hint: Run sprig api java.util.Collections --member sort to see the signatures this call can match.
```

用 `@std/jvm.spr` 把列表复制成 Java 的列表，让 Java 在副本上干活，最后再给结果拍一张 Sprig 的快照：

<<< @/snippets/interop/collections.spr

```text
[b, a]
[a, b]
3
```

- `list_copy` 给你一个独立的、可变的 `ArrayList`。给它排序不会动 `original`，这就是第一行。
- `list_snapshot` 给你一个只读的 Sprig `List`，内容是 Java 列表那一刻的样子。后面的 `add("c")` 让 `copy` 变成三个元素，`snapshot` 不受影响。
- Map 用 `map_copy` 和 `map_snapshot`，道理一样。
- 快照不是实时视图，也不会复制里面的对象。Java 集合里有 `null` 时，它会抛出 `Error`。

## 回调 {#callbacks}

Java 方法的参数是函数式接口（`Runnable`、`Comparator`、`Consumer` 等等）时，可以传 Sprig 的 `fn` 值。但不能传可能抛出 `Error` 的那种，因为 Java 看不到这件事：

<<< @/snippets/interop/callbacks_bad.spr

```text
SPR-TYPE-CALLABLE-THROWS [TYPE] main.spr:4:21: A function value that may throw Error cannot be passed to Java parameter 1 of Thread; Java cannot see the throws clause (expected fn() -> Unit, actual fn() -> Unit throws Error)
  hint: Handle the error inside a named function and pass a lambda that calls it.
```

在一个具名函数里决定失败了怎么办，再传一个调用它的 lambda：

<<< @/snippets/interop/callbacks.spr

```text
[a, bb]
failed
```

- 这里的 `run_callback` 把消息打印出来。你的程序也许要记日志、计数，或者把错误存起来以后处理；重要的是这个决定由你做，并且写在了代码里。
- 写比较器时，把参数类型写上，返回 `a.compareTo(b)`。它的结果正好是 `Comparator` 要的 `Int32`。
- 接口有好几个抽象方法，或者回调需要保存状态时，就写一个类再 `conform`。见[一致性声明（英文）](/en/reference/jvm/conformance)。

## 重载的方法和构造器 {#overloads}

`StringBuilder` 有四个构造器，没有一个接受 `Float`：

<<< @/snippets/interop/overloads_bad.spr

```text
SPR-JVM-MEMBER [JVM] main.spr:2:15: No constructor of StringBuilder matches 1 argument(s)
  hint: Run sprig api java.lang.StringBuilder --member '<init>' to see the signatures this call can match.
```

照提示里的命令查一下，选定你要的那个重载，再让参数就是那个类型：

```sh
sprig api java.lang.StringBuilder --member '<init>'
sprig api java.lang.Math --member max
```

<<< @/snippets/interop/overloads.spr

```text
rows=3
3
```

- `Int32` 的值会选中 `int` 的重载。普通的 `Int` 在有 `long` 重载时优先选它；没有的话收窄成 `int`，运行时检查范围。
- 调用 Java 只能用位置参数。
- 方括号里的类型参数（比如 `Collections.emptyList[String]()`）决定的是泛型方法的类型参数，不能用来在重载之间做选择。
- `SPR-JVM-AMBIGUOUS` 表示有两个重载同样合适。看报错里列出的候选，改一个参数的类型，直到只剩一个合适；编译器不会替你选。
