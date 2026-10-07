# 14. 并发

Sprig 没有 `async`/`await`，也没有线程关键字。并发是一个用 Sprig 写的库 `sprig-concurrent`，底下一小层 Java 内核封装了 `java.util.concurrent`。这一章用一个真实的例子过一遍它的几个部件。

## 14.1 加依赖

在项目的 `sprig.toml` 里：

```toml
[[dependency]]
name = "concurrent"
path = "../../libraries/sprig-concurrent"   # 或者 sprig add concurrent 从注册表添加
```

然后 `sprig resolve`，之后就能 `import "@concurrent/concurrent.spr" as concurrent`。

## 14.2 任务

一个任务就是一个 `fn() -> T` 函数值在另一个线程上运行：

```sprig
import "@concurrent/concurrent.spr" as concurrent

func word_count(line: String) -> Int:
    return line.split(" ").size()

let task = concurrent.spawn(fn() => word_count("the quick brown fox"))
print(task.await())
```

- `spawn` 返回 `Task[Int]`，类型参数从 lambda 的结果推断。
- `await()` 等结果。任务里出的错（抛出的 `Error`、整数溢出、被取消）在 `await()` 这里重新作为 `Error` 抛出，所以 `await()` 声明了 `throws Error`。
- 第 11 章说过 lambda 只能捕获 `let`。这条规则在这里的回报是：**任务体不可能和别的线程竞争同一个局部变量**，这类数据竞争在 Sprig 里写不出来。共享的 `MutableList`、`MutableMap` 和类的 `var` 字段仍然需要保护，见下面的锁。
- 所有线程都是守护线程：顶层语句跑完程序就结束，不会等还没完成的任务。要等就 `await()`。

## 14.3 一个完整的例子

仓库里的 `examples/parallel_words` 对几段文本并行数词，用到了库的每个部件：

<<< @/../examples/parallel_words/src/main.spr

```text
[9, 5, 11, 5]
[9, 5, 11, 5]
total 30
reports 4
finished 4
```

逐个看：

- **`parallel_map(items, f)`**：给每个元素开一个任务，按元素顺序返回结果列表。最简单的"把这个循环并行化"。
- **`pool(2)` 和 `spawn_on(pool, f)`**：限制同时运行的线程数。`await_all(tasks)` 等一组任务，遇到第一个失败就抛出。`shutdown()` 之后池子不接新任务，正在跑的照常完成。
- **`counter(0)`**：原子的 `Int`，`add`、`get`、`increment`、`compare_and_set`。多个任务同时 `add` 是安全的。
- **`lock()`**：可重入互斥锁。`guard.run(fn() => ...)` 在持有锁的时候运行函数值。改共享的 `finished` 列表时必须这样做。
- **`channel[String](16)`**：容量 16 的有界队列。`send` 在满时阻塞，`try_send` 不阻塞；`receive()` 在空时阻塞，通道关闭且取空后返回 `null`。通道里不放 `null`，所以 `null` 只有"结束"一个意思，`while report != null:` 就是标准的消费循环。

## 14.4 任务体里的错误

任务体的类型是 `fn() -> T`，没有 `throws`，所以它不能直接调用 `throws Error` 的函数。两种写法：

- 在任务体调用的具名函数里 `try`/`catch`，把结果转成一个 `variant`（比如 `Ok`/`Failed`）返回；
- 让它失败，由 `await()` 在等待处作为 `Error` 报出。

库里任务体常用的函数（`send`、`receive`、`sleep`、`run`、`locked`）都没有 `throws` 子句，参数不合法时在运行时报 `Error`。

## 14.5 还没有的

没有 `async`/`await` 语法，没有结构化并发作用域，`Task` 不能用 `then` 串起来。组合靠 `await_all`、通道和普通函数。取消是协作式的：`cancel()` 中断任务的线程，能结束 `sleep` 和阻塞的 `receive`，一个从不阻塞的循环会跑到头。

完整 API 见[并发指南](/guide/concurrency)和 `sprig api @concurrent/concurrent.spr`；`sprig help concurrency` 是编译器自带的规则摘要。

## 小结

- 并发是库：`spawn` / `await`、`parallel_map`、`pool` + `spawn_on` + `await_all`。
- 共享状态用 `counter`、`lock`、`channel`；lambda 不能捕获 `var` 的规则在这里挡掉了一类竞争。
- 任务里的错误在 `await()` 处报出。

下一章：[工具链与 AI 助手](/tutorial/ch15-tooling)。
