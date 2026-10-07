# 并发

Sprig 没有 `async`/`await` 这类语法。并发是一个库：SDK 里的 `libraries/sprig-concurrent`，用 Sprig 写成，底下一小层 Java 内核（`sprig.runtime.concurrent`）负责 `java.util.concurrent` 的线程池、队列和锁。示例项目 `examples/parallel_words` 用到了它的每一部分。

在项目的 `sprig.toml` 里声明依赖，然后 `sprig resolve`：

```toml
[[dependency]]
name = "concurrent"
path = "../../libraries/sprig-concurrent"
```

## 任务：把一个函数值交给另一个线程

```sprig
import "@concurrent/concurrent.spr" as concurrent

func word_count(line: String) -> Int:
    return line.split(" ").size()

let task = concurrent.spawn(fn() => word_count("the quick brown fox"))
print(task.await())
```

- `spawn` 接收一个 `fn() -> T`，在共享线程池里运行它，返回 `Task[T]`。类型参数按通常的规则推断：这里从 lambda 的结果得到 `Int`。
- `task.await()` 等结果。任务里出的错（抛出的 `Error`、整数溢出、被取消）会在 `await()` 这里重新作为 `Error` 抛出，所以 `await()` 声明了 `throws Error`。
- 任务体是普通的函数值，只能捕获 `let` 绑定和参数，所以其他语言里那种对局部变量的数据竞争，在 Sprig 里写不出来。共享的 `MutableList`、`MutableMap` 和 `var` 字段不受保护，见下面的锁和通道。
- 线程都是守护线程：程序的顶层语句跑完就结束，不会等没完成的任务。要等就 `await()`。

## 一批任务：parallel_map 和 await_all

```sprig
let counts = concurrent.parallel_map(lines, fn(line: String) => word_count(line))
```

`parallel_map` 给每个元素开一个任务，按元素的顺序返回结果；`await_all(tasks)` 对一组 `Task[T]` 做同样的事，遇到第一个失败就抛出。

要限制同时运行的线程数，用线程池：

```sprig
let pool = concurrent.pool(2)
let tasks: MutableList[concurrent.Task[Int]] = []
for line in lines:
    tasks.append(concurrent.spawn_on(pool, fn() => word_count(line)))
let results = concurrent.await_all(tasks)
pool.shutdown()
```

`shutdown()` 之后池子不再接新任务（再 `spawn_on` 会报 `Error`），正在跑的任务照常完成；`await_termination(millis)` 等它们全部结束。

## 通道：线程之间传值

```sprig
let words = concurrent.channel[String](16)

func produce() -> Int:
    words.send("a")
    words.send("b")
    words.close()
    return 2

let producer = concurrent.spawn(fn() => produce())
var next = words.receive()
while next != null:
    let word = next
    if word != null:
        print(word)
    next = words.receive()
print(producer.await())
```

- `channel[T](capacity)` 是有界队列。`send` 在队列满时阻塞；`receive()` 在队列空时阻塞，通道关闭且取空后返回 `null`。通道里不放 `null`，所以 `null` 只有"结束"这一个意思。
- `try_send` 和 `receive_within(millis)` 不阻塞。
- 往关闭的通道 `send` 会在运行时以 `Error` 失败。`send` 没有 `throws` 子句，这样任务体里才能调用它；失败由那个任务的 `await()` 报出来。

## 计数器、锁和门闩

```sprig
let total = concurrent.counter(0)
total.add(3)
print(total.get())

let guard = concurrent.lock()
let finished: MutableList[String] = []
guard.run(fn() => finished.append("done"))
let size = concurrent.locked(guard, fn() => finished.size())

let gate = concurrent.latch(3)
gate.count_down()
print(gate.await_within(1000))
```

- `Counter` 是原子的 `Int`：`get`、`set`、`add`、`increment`、`decrement`、`compare_and_set`。
- `Lock` 是可重入的互斥锁：`run(action)` 和 `locked(lock, work)` 在持有锁的时候运行函数值。修改共享的 `MutableList`/`MutableMap` 时用它。
- `Latch` 是倒数计数：每个任务 `count_down()`，等待方 `await()` 或 `await_within(millis)`。

## 任务体里的错误

任务体是 `fn() -> T`，没有 `throws` 子句，所以它不能直接调用声明了 `throws Error` 的函数。两种写法：

- 在任务体调用的具名函数里 `try`/`catch`，把结果转成 `variant`（比如 `Ok`/`Failed`）返回；
- 让它失败，由 `await()` 在等待处作为 `Error` 报出。`await_or(fallback)` 没有 `throws` 子句，一个任务体可以用它等另一个任务。

库里任务体常用的函数（`send`、`receive`、`receive_within`、`sleep`、`run`、`locked`）都没有 `throws` 子句，参数不合法时在运行时报 `Error`。`concurrent.sleep(millis)` 和 `time.sleep` 的区别就在这里：任务被 `cancel()` 时，正在 `sleep` 的任务会以 `Error` 结束。

## 还没有的

没有 `async`/`await` 语法，没有结构化并发作用域，`Task` 不能用 `then` 串起来，用 `await_all`、通道和普通函数来组合。取消是协作式的：`cancel()` 中断任务的线程，能结束 `sleep` 和阻塞的 `receive`，一个从不阻塞的循环会跑到头。

完整的 API 表见 [`libraries/sprig-concurrent/README.md`（英文）](https://github.com/ColinHouse/Sprig/blob/main/libraries/sprig-concurrent/README.md)；`sprig resolve` 之后，`sprig api @concurrent/concurrent.spr --json` 列出每个签名和注释，`sprig help concurrency` 是编译器自带的规则摘要。仓库里的测试 `python3 tests/concurrent/check_concurrent.py` 在真实线程上验证这一页说的每条行为。
