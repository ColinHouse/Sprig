# 并发

Sprig 没有 `async`/`await` 这类语法，也没有"带颜色"的函数。并发是自带的模块 `@std/concurrent.spr`，用 Sprig 写成，底下一小层 Java 内核（`sprig.runtime.concurrent`）。任务跑在 JDK 21 的虚拟线程上：一万个会阻塞的任务和十个一样便宜，普通的阻塞代码（Java 的 `HttpClient.send`、JDBC、文件读写）放进任务里原样就能跑。示例项目 `examples/parallel_words` 用到了它的每一部分。

## 作用域：每个任务都有主人

```sprig
import "@std/concurrent.spr" as concurrent

func word_count(line: String) -> Int:
    return line.split(" ").size()

func count_all(s: concurrent.Scope) -> List[Int] throws Error:
    let tasks: MutableList[concurrent.Task[Int]] = []
    for line in ["the quick brown fox", "a lazy dog"]:
        tasks.append(concurrent.spawn(s, fn() => word_count(line)))
    return concurrent.await_all(tasks)

print(concurrent.scope(count_all))
```

- `concurrent.scope(body)` 新建一个 `Scope`，把它交给 `body` 运行，返回 `body` 的结果。`body` 通常是按名字传入的具名函数，因为 lambda 只能写一个表达式。
- `spawn(s, work)` 在这个作用域拥有的一条虚拟线程上运行 `fn() -> T`，返回 `Task[T]`。类型参数按通常的规则推断：这里从 lambda 的结果得到 `Int`。
- **离开作用域时会等所有任务结束**，不管有没有 `await`。没有任务能活过启动它的那个函数。
- **第一个失败会取消其他兄弟任务**，并由 `scope()` 作为 `Error` 重新抛出，所以 `scope` 声明了 `throws Error`。任务里的失败也会由那个任务的 `await()` 报出来；在那里接住它并不能撤销作用域的失败。要容忍某个任务失败，就在任务体里处理掉，或者对一个不会失败的任务体用 `await_or(fallback)`。
- 主动取消的任务（`task.cancel()`、`s.cancel_all()`）不算失败。取消会中断任务的线程，在阻塞点生效：`sleep`、`await`、通道的 `receive`、尊重中断的 Java I/O。一个从不阻塞的循环会跑到头。
- 没有不属于作用域的 `spawn`：任务永远属于启动它的那个作用域，body 返回之后再启动任务会报 `Error`。
- 任务体是普通的函数值，只能捕获 `let` 绑定和参数，所以其他语言里那种对局部变量的数据竞争在 Sprig 里写不出来。共享的 `MutableList`、`MutableMap` 和 `var` 字段不受保护，见下面的锁和通道。

## 一批任务：parallel_map 和 await_all

```sprig
let counts = concurrent.parallel_map(lines, fn(line: String) => word_count(line))
```

`parallel_map` 自己开一个作用域，给每个元素一个任务，等全部结束后按元素顺序返回结果；`await_all(tasks)` 在你的作用域里对一组 `Task[T]` 做同样的事，遇到第一个失败就抛出。

虚拟线程让任务数量不再是成本，I/O 为主的工作不需要限流。要把 CPU 密集的工作限制在几条平台线程上，用线程池：

```sprig
func count_on_pool(s: concurrent.Scope) -> List[Int] throws Error:
    let pool = concurrent.pool(2)
    let tasks: MutableList[concurrent.Task[Int]] = []
    for line in lines:
        tasks.append(concurrent.spawn_on(s, pool, fn() => word_count(line)))
    let results = concurrent.await_all(tasks)
    pool.shutdown()
    return results
```

池里的任务仍然属于作用域。`shutdown()` 之后池子不再接新任务（再 `spawn_on` 会报 `Error`），正在跑的任务照常完成；`await_termination(millis)` 等它们全部结束。`parallel_map_on(pool, items, transform)` 是一行的写法。

## 通道：线程之间传值

```sprig
let words = concurrent.channel[String](16)

func produce() -> Int:
    words.send("a")
    words.send("b")
    words.close()
    return 2

func consume(s: concurrent.Scope) -> Int throws Error:
    let producer = concurrent.spawn(s, fn() => produce())
    var next = words.receive()
    while next != null:
        let word = next
        if word != null:
            print(word)
        next = words.receive()
    return producer.await()

print(concurrent.scope(consume))
```

- `channel[T](capacity)` 是有界队列。`send` 在队列满时阻塞；`receive()` 在队列空时阻塞，通道关闭且取空后返回 `null`。通道里不放 `null`，所以 `null` 只有"结束"这一个意思。
- `try_send` 和 `receive_within(millis)` 不阻塞。
- 往关闭的通道 `send` 会在运行时以 `Error` 失败。`send` 没有 `throws` 子句，这样任务体里才能调用它；失败由那个任务的 `await()` 和作用域报出来。

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
- `Lock` 是可重入的互斥锁：`run(action)` 和 `locked(lock, work)` 在持有锁的时候运行函数值。修改共享的 `MutableList`/`MutableMap` 时用它。它是 `java.util.concurrent` 的锁，持有它的任务不会钉住虚拟线程的载体线程。
- `Latch` 是倒数计数：每个任务 `count_down()`，等待方 `await()` 或 `await_within(millis)`。

## 任务体里的错误

任务体是 `fn() -> T`，没有 `throws` 子句，所以它不能直接调用声明了 `throws Error` 的函数。两种写法：

- 在任务体调用的具名函数里 `try`/`catch`，把结果转成 `variant`（比如 `Ok`/`Failed`）返回；
- 让它失败：`await()` 在等待处报出来，作用域结束时再报一次。`await_or(fallback)` 没有 `throws` 子句，一个任务体可以用它等另一个任务。

任务体常用的模块函数（`send`、`receive`、`receive_within`、`sleep`、`run`、`locked`）都没有 `throws` 子句，参数不合法时在运行时报 `Error`。`concurrent.sleep(millis)` 和 `time.sleep` 的区别就在这里：正在 `sleep` 的任务被取消时会立刻结束。

## 为什么不是 async/await

`async` 会给函数染色：`async` 函数只能在另一个 `async` 函数里 `await`，代码库从此分成两半。语言选择它，是因为运行时没有便宜的线程。JDK 21 起 JVM 有了虚拟线程，阻塞变得便宜，任务就只是"在别处运行的一个函数"。Sprig 和 Java、Go 一样走这条路：不加语法，不给函数染色，结构来自作用域而不是类型系统。

## 还没有的

`Task` 不能用 `then` 串起来，用 `await_all`、通道和普通函数来组合。作用域本身没有超时预算（`await_within` 是针对单个任务的）；`java.util.concurrent.StructuredTaskScope` 还是预览 API，暂不使用。

`sprig api @std/concurrent.spr --json` 列出每个签名和注释，`sprig help concurrency` 是编译器自带的规则摘要。仓库里的测试 `python3 tests/concurrent/check_concurrent.py` 在真实线程上验证这一页说的每条行为，包括一万个睡眠任务同时完成，以及任务里的本地回环套接字回显。
