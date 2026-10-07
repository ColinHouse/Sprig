# 14. 并发

Sprig 没有 `async`/`await`，也没有线程关键字。并发是自带的模块 `@std/concurrent.spr`：用 Sprig 写成，底下一小层 Java 内核封装了 `java.util.concurrent`，任务跑在 JDK 21 的虚拟线程上。这一章用一个真实的例子过一遍它的几个部件。

## 14.1 作用域和任务

```sprig
import "@std/concurrent.spr" as concurrent

func word_count(line: String) -> Int:
    return line.split(" ").size()

func count_two(s: concurrent.Scope) -> Int throws Error:
    let first = concurrent.spawn(s, fn() => word_count("the quick brown fox"))
    let second = concurrent.spawn(s, fn() => word_count("a lazy dog"))
    return first.await() + second.await()

print(concurrent.scope(count_two))
```

- `concurrent.scope(body)` 新建一个作用域 `s`，把它交给 `body` 运行，返回 `body` 的结果。`body` 是一个具名函数，按名字传进去（第 11 章的函数引用）。
- `spawn(s, work)` 让一个 `fn() -> T` 在作用域拥有的虚拟线程上运行，返回 `Task[T]`。虚拟线程很便宜：一万个会阻塞的任务和十个一样。
- `await()` 等结果。任务里出的错（抛出的 `Error`、整数溢出）在 `await()` 这里重新作为 `Error` 抛出，所以 `await()` 声明了 `throws Error`。
- **离开作用域时会等所有任务结束**，不管你有没有 `await`。没有任务能活过启动它的函数，所以不会有"忘了等的任务悄悄丢了结果"这回事。
- **第一个失败会取消其他任务**，并由 `scope()` 作为 `Error` 抛出。要容忍某个任务失败，在任务体里把错误处理掉。
- 第 11 章说过 lambda 只能捕获 `let`。这条规则在这里的回报是：**任务体不可能和别的线程竞争同一个局部变量**。共享的 `MutableList`、`MutableMap` 和类的 `var` 字段仍然需要保护，见下面的锁。

## 14.2 一个完整的例子

仓库里的 `examples/parallel_words` 对几段文本并行数词，用到了模块的每个部件：

<<< @/../examples/parallel_words/src/main.spr

```text
[9, 5, 11, 5]
[9, 5, 11, 5]
total 30
reports 4
finished 4
```

逐个看：

- **`parallel_map(items, f)`**：自己开一个作用域，给每个元素一个任务，等全部结束后按元素顺序返回结果。最简单的"把这个循环并行化"。
- **`pool(2)` 和 `spawn_on(s, pool, f)`**：把 CPU 密集的工作限制在两条平台线程上；任务仍然属于作用域 `s`。`await_all(tasks)` 等一组任务，遇到第一个失败就抛出。`shutdown()` 之后池子不接新任务，正在跑的照常完成。
- **`counter(0)`**：原子的 `Int`，`add`、`get`、`increment`、`compare_and_set`。多个任务同时 `add` 是安全的。
- **`lock()`**：可重入互斥锁。`guard.run(fn() => ...)` 在持有锁的时候运行函数值。改共享的 `finished` 列表时必须这样做。
- **`channel[String](16)`**：容量 16 的有界队列。`send` 在满时阻塞，`try_send` 不阻塞；`receive()` 在空时阻塞，通道关闭且取空后返回 `null`。通道里不放 `null`，所以 `null` 只有"结束"一个意思，`while report != null:` 就是标准的消费循环。

## 14.3 任务体里的错误

任务体的类型是 `fn() -> T`，没有 `throws`，所以它不能直接调用 `throws Error` 的函数。两种写法：

- 在任务体调用的具名函数里 `try`/`catch`，把结果转成一个 `variant`（比如 `Ok`/`Failed`）返回；
- 让它失败，由 `await()` 在等待处报出，作用域结束时再报一次。

模块里任务体常用的函数（`send`、`receive`、`sleep`、`run`、`locked`）都没有 `throws` 子句，参数不合法时在运行时报 `Error`。普通的阻塞 Java 调用（`HttpClient.send`、JDBC、套接字）放进任务里原样能跑，虚拟线程会在它阻塞时让出。

## 14.4 为什么没有 async/await

`async` 会给函数染色：`async` 函数只能在另一个 `async` 函数里等待，代码库从此分成两半。那些语言这么做是因为运行时没有便宜的线程。JDK 21 起 JVM 有了虚拟线程，阻塞变得便宜，任务就只是"在别处运行的一个函数"。Sprig 和 Java、Go 一样走这条路：不加语法，不给函数染色，结构来自作用域。

## 14.5 还没有的

`Task` 不能用 `then` 串起来，组合靠 `await_all`、通道和普通函数。作用域没有自己的超时预算（`await_within` 针对单个任务）。取消是协作式的：`cancel()` 中断任务的线程，在 `sleep`、`await`、阻塞的 `receive` 和尊重中断的 Java I/O 处生效，一个从不阻塞的循环会跑到头。

完整 API 见[并发指南](/guide/concurrency)和 `sprig api @std/concurrent.spr`；`sprig help concurrency` 是编译器自带的规则摘要。

## 小结

- 并发是 `@std/concurrent`：`scope(body)` 拥有任务，`spawn(s, work)` / `await`，`parallel_map`，`pool` + `spawn_on` + `await_all`。
- 离开作用域等所有任务；第一个失败取消其余并抛出。
- 共享状态用 `counter`、`lock`、`channel`；lambda 不能捕获 `var` 的规则在这里挡掉了一类竞争。

下一章：[工具链与 AI 助手](/tutorial/ch15-tooling)。
