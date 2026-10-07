# 14. 并发

Sprig 没有 `async`/`await`，也没有线程关键字。并发是自带的模块 `@std/concurrent.spr`：用 Sprig 写成，底下一小层 Java 内核封装了 `java.util.concurrent`，任务跑在 JDK 21 的虚拟线程上。这一章用一个真实的例子过一遍它的几个部件。

## 14.1 作用域和任务

<<< @/snippets/book/ch14_scope.spr

```text
7
[2, 3]
```

- `concurrent.scope(body)` 新建一个作用域 `s`，把它交给 `body` 运行，返回 `body` 的结果。`body` 是一个具名函数，按名字传进去（第 11 章的函数引用）。
- `spawn(s, work)` 让一个 `fn() -> T` 在作用域拥有的虚拟线程上运行，返回 `Task[T]`。虚拟线程很便宜：一万个会阻塞的任务和十个一样。
- `await()` 等结果。任务里出的错（抛出的 `Error`、整数溢出）在 `await()` 这里重新作为 `Error` 抛出，所以 `await()` 声明了 `throws Error`。
- **离开作用域时会等所有任务结束**，不管你有没有 `await`。没有任务能活过启动它的函数，所以不会有"忘了等的任务悄悄丢了结果"这回事。
- **第一个失败会取消其他任务**，并由 `scope()` 作为 `Error` 抛出。要容忍某个任务失败，在任务体里把错误处理掉。
- 第 11 章说过 lambda 只能捕获 `let`。这条规则在这里的回报是：**任务体不可能和别的线程竞争同一个局部变量**。共享的 `MutableList`、`MutableMap` 和类的 `var` 字段仍然需要保护，见 14.4 的锁。
- `parallel_map(items, f)` 是最常用的捷径：自己开一个作用域，给每个元素一个任务，等全部结束后按元素顺序返回结果。"把这个循环并行化"一行就够。

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

## 14.3 通道

<<< @/snippets/book/ch14_channel.spr

```text
got one
got two
got three
6
```

- `channel[String](4)` 是容量 4 的有界队列，类型参数要写。`send` 在满时阻塞，`try_send` 不阻塞（满或已关闭时返回 `false`）；`receive()` 在空时阻塞，`receive_within(millis)` 最多等这么久，等不到返回 `null`。
- `close()` 之后不能再 `send`，已经在队列里的值照常取出，取空后 `receive()` 返回 `null`。通道里不放 `null`，所以 `null` 只有"结束"一个意思，`if line == null: break` 就是标准的消费循环。
- `produce` 是具名函数，直接按名字传给 `spawn`。它在另一条线程上 `send`，主线程在作用域体里 `receive`；`scope` 返回前会等 `produce` 结束。
- 通道的方法都没有 `throws`：向已关闭的通道 `send` 在运行时报 `Error`，由那个任务的 `await()` 报出。

## 14.4 计数器和锁

<<< @/snippets/book/ch14_counter.spr

```text
[1, 2, 3, 4]
4000
4
```

- `counter(0)` 是原子的 `Int`：`increment`、`decrement`、`add(delta)`、`get`、`set`、`compare_and_set(expected, updated)`。四个任务同时各加一千次，结果正好 4000。
- `lock()` 是可重入的互斥锁。`guard.run(fn() => ...)` 在持有锁的时候运行函数值；要返回值就用 `concurrent.locked(guard, fn() => ...)`。改共享的 `MutableList`、`MutableMap` 和类的 `var` 字段时必须这样做，编译器不会替你检查。
- `latch(count)` 是倒数门闩：`count_down()` 减一，`await()` 等到零，`await_within(millis)` 最多等这么久。
- `work` 里的 `var i` 是任务自己的局部变量，别的线程碰不到；真正共享的只有 `hits`、`seen` 和 `guard` 这三个 `let` 绑定的对象，而 `let` 正是 lambda 能捕获的东西。

## 14.5 任务体里的错误

任务体的类型是 `fn() -> T`，没有 `throws`，所以它不能直接调用 `throws Error` 的函数。要容忍某个任务失败，在它调用的具名函数里把错误处理掉：

<<< @/snippets/book/ch14_tolerate.spr

```text
skipped: not a number: x
[3, 0, 5]
```

不处理会怎样：任务体里抛出的 `Error`、整数溢出、Java 的运行时异常都让这个任务失败。

- `await()` 把失败作为 `Error` 重新抛出，消息以 `task failed:` 开头；`await_or(fallback)` 返回兜底值，不抛，所以另一个任务体里也能用它等待。
- **第一个失败会取消作用域里的其他任务**，并由 `scope()` 作为 `Error` 抛出，哪怕你已经用 `await()` 接住过，哪怕你用的是 `await_or`。也就是说，`await_or` 能让作用域体继续往下走，但救不了这个作用域；要让作用域成功结束，失败必须在任务体里处理掉。
- 取消是协作式的：`cancel()` 中断任务的线程，在 `sleep`、`await`、阻塞的 `receive` 和尊重中断的 Java I/O 处生效，一个从不阻塞的循环会跑到头。被取消的任务 `await()` 报 `task was cancelled`。

模块里任务体常用的函数（`send`、`receive`、`sleep`、`run`、`locked`）都没有 `throws` 子句，参数不合法时在运行时报 `Error`。普通的阻塞 Java 调用（`HttpClient.send`、JDBC、套接字）放进任务里原样能跑，虚拟线程会在它阻塞时让出。

## 14.6 没有结果的任务：run 和 scope_run

`Unit` 不是值，所以只有副作用的任务不能写成 `Task[Unit]`：`spawn(s, fn() => print("x"))` 报 `SPR-TYPE-UNIT`。0.8 起这种任务用 `run`，只负责调度、不返回结果的作用域体用 `scope_run`：

```sprig
import "@std/concurrent.spr" as concurrent

func announce(line: String) -> Unit:
    print("> " + line)

func announce_all(s: concurrent.Scope) -> Unit throws Error:
    for line in ["ready", "steady"]:
        concurrent.run(s, fn() => announce(line))

concurrent.scope_run(announce_all)
```

- `run(s, work)` 启动一个 `fn() -> Unit`，返回 `Job`。它的方法和任务一样：`await()` 等它结束，失败时报 `Error`，只是不返回值；`cancel()`、`is_done()` 也照常可用。
- `scope_run(body)` 就是给"不返回值的 body"用的 `scope()`，规则完全相同：等所有任务和 job 结束，第一个失败会取消其余的并重新抛出。
- 两条输出的先后顺序不固定，因为它们是同时运行的。

## 14.7 为什么没有 async/await

`async` 会给函数染色：`async` 函数只能在另一个 `async` 函数里等待，代码库从此分成两半。那些语言这么做是因为运行时没有便宜的线程。JDK 21 起 JVM 有了虚拟线程，阻塞变得便宜，任务就只是"在别处运行的一个函数"。Sprig 和 Java、Go 一样走这条路：不加语法，不给函数染色，结构来自作用域。

## 14.8 还没有的

`Task` 不能用 `then` 串起来，组合靠 `await_all`、通道和普通函数。作用域没有自己的超时预算（`await_within` 针对单个任务）。作用域可以嵌套，但没有跨作用域的任务：一个任务只属于启动它的作用域。

完整 API 见[并发指南](/guide/concurrency)和 `sprig api @std/concurrent.spr`；`sprig help concurrency` 是编译器自带的规则摘要。

## 小结

- 并发是 `@std/concurrent`：`scope(body)` 拥有任务，`spawn(s, work)` / `await`，`parallel_map`，`pool` + `spawn_on` + `await_all`。
- 离开作用域等所有任务；第一个失败取消其余并抛出，`await_or` 救不了作用域，容错写在任务体里。
- 通道传值（`send`、`receive`、`close`，`null` 表示结束），`counter` 原子计数，`lock` 保护共享的可变状态；lambda 不能捕获 `var` 的规则在这里挡掉了一类竞争。
- 没有结果的任务用 `run` 和 `scope_run`。

下一章：[工具链与 AI 助手](/tutorial/ch15-tooling)。
