# 22. 并发

程序大部分时间不是在算，而是在等：等磁盘、等网络、等用户输入。等待的时候，CPU 闲着。这一章讲 Sprig 怎么把等待的时间用起来，同时做几件事；以及几个任务碰到同一份数据时，有哪些坑。

这一章你会学到：

- 线程和"阻塞"是什么意思，为什么 JDK 21 起开很多线程很便宜；
- 用 `@std/concurrent` 的作用域启动任务、拿回结果；
- 用通道在任务之间传值；
- 用计数器保护原子计数，用锁保护共享的容器；
- 顶层 `var` 为什么会被多个任务共享，什么时候会出竞争；
- 任务失败时错误怎么回到你手里。

## 22.1 线程和阻塞

**为什么。** 你写的每行代码默认按顺序执行：上一行做完才轮到下一行。**线程**是另一条执行线，两条线程同时前进。当一个任务卡在等待上（术语叫**阻塞**，blocking），比如等网络回复，操作系统可以让它先睡，把 CPU 让给别的线程。

Java 从 JDK 21 起有了**虚拟线程**：开一万条做阻塞操作的任务，代价和开十条差不多。所以 Sprig 不发明 `async`/`await` 语法，直接给你"在另一条线程上跑一个函数值"的能力，放在自带的 `@std/concurrent` 模块里。

两个同时进行的等待：

<<< @/snippets/book/ch22_two_tasks.spr

```text
tick tack
```

- `nap("tick")` 和 `nap("tack")` 各等 10 毫秒。两个 `spawn` 启动之后才 `await`，所以两段等待是重叠的：总时间不是 20 毫秒。
- `first.await()` 和 `second.await()` 按写的顺序取结果，拼出来的字符串因此固定是 `tick tack`；谁先跑完无所谓。

## 22.2 作用域拥有任务

**为什么。** 任务在后台跑，如果没人管，程序可能结束了它还没跑完，结果就丢了。Sprig 的答案叫**作用域**（scope）：它拥有在它里面启动的所有任务，离开作用域时等它们全部结束。

<<< @/snippets/book/ch14_scope.spr

```text
7
[2, 3]
```

- `concurrent.scope(count_two)` 新建一个作用域 `s`，交给 `count_two` 运行，`count_two` 的返回值就是 `scope` 的返回值。`count_two` 是具名函数，按名字传进去（第 15 章的"函数引用"）。
- `concurrent.spawn(s, fn() => word_count("..."))` 启动一个任务，返回 `Task[Int]`。任务体是一个返回值、不抛错的函数值。
- `await()` 等这个任务的结果。作用域保证：`scope` 返回时，里面启动的每个任务都已经结束。
- 第 4 行 `concurrent.parallel_map(...)` 是最常用的捷径：给每个元素开一个任务，全部结束后按元素顺序返回结果列表。

## 22.3 通道：任务之间传值

**为什么。** 任务之间常常要送数据，但不能直接去读对方正在改的变量。**通道**（channel）是一个有界的队列：一端 `send`，另一端 `receive`，满的时候发的人等，空的时候收的人等。

<<< @/snippets/book/ch14_channel.spr

```text
got one
got two
got three
6
```

- `concurrent.channel[String](4)`：容量是 4，元素类型要写出来。
- 生产者任务 `send` 三个值然后 `close()`；主线程在 `while true:` 里 `receive`，拿到 `null` 就 `break`——通道里不放 `null`，所以 `null` 只有一个意思："关闭且取空了"。
- `scope(drain)` 结束时，`produce` 已经结束；`producer.await()` 返回 `3`，加上收到的 `3` 条，打印 `6`。
- `send` 在通道满时阻塞，`try_send` 不阻塞；`receive()` 在空时阻塞，`receive_within(毫秒)` 最多等那么久。

## 22.4 计数器、锁和共享状态

**为什么。** 两个任务同时改一个数，读—改—写三步可能交错，结果就少了几次。Sprig 提供了两个工具：原子的**计数器**（counter）和保护一段代码的**锁**（lock）。

<<< @/snippets/book/ch14_counter.spr

```text
[1, 2, 3, 4]
4000
4
```

- `concurrent.counter(0)` 是一个原子的 `Int`：四个任务各 `increment` 一千次，结果是恰好 4000。`counter` 还能 `add`、`get`、`set`、`compare_and_set`。
- 共享的 `MutableList` 用 `concurrent.lock()` 保护：`guard.run(fn() => seen.append(id))` 在持有锁的时候执行 append，四个任务的 append 不会互相踩。
- 每个任务自己的局部 `var i` 只有它自己看得见，不需要保护。

### 顶层 var 会被所有任务看见

第 15 章讲过：lambda 不能捕获函数的 `var` 局部变量。

<<< @/snippets/book/ch11_capture.spr

```text
SPR-TYPE-CAPTURE [TYPE] main.spr:3:16: Lambda captures mutable local 'counter'
  hint: Copy it into a 'let' binding before the lambda, or use a class field.
```

**但顶层 `var` 不是局部变量**，它是模块状态：lambda 读得到、也写得到，任务之间共享的就是它本身。

<<< @/snippets/book/ch22_shared_var.spr

```text
HELLO
```

这段程序只有一个任务在写，所以结果是确定的：任务写完之后主线程才打印。如果两个任务同时写同一个顶层 `var`，最后的值取决于调度，**不能预测**。共享可变状态要么换成 `concurrent.counter`（原子），要么用 `concurrent.lock` 包起来，要么让每个任务只写自己的局部量，最后用 `await()` 收集。

## 22.5 任务失败时

**为什么。** 任务在另一条线程上跑，出了错不能默默消失。任务体的类型是 `fn() -> T`，没有 `throws`，所以错误要在任务体调用的具名函数里处理掉：

<<< @/snippets/book/ch14_tolerate.spr

```text
skipped: not a number: x
[3, 0, 5]
```

- `count_or_zero` 自己 `try`/`catch`，失败时打印一行并返回 0，所以任务永远成功。
- **第一个失败的任务会取消作用域里的其他任务**，并由 `scope()` 作为 `Error` 重新抛出。`await_or(fallback)` 只能让等待不抛错，救不了作用域：要让它成功结束，失败必须在任务体里被处理掉。
- 取消是协作式的：`cancel()` 中断任务的线程，在 `sleep`、`await` 和阻塞的 `receive` 处生效。

错误真的漏出任务体时，`await()` 会把它重新抛出来。Sprig 的 `Error` 保留自己的消息：

<<< @/snippets/book/ch22_error.spr

```text
caught: channel is closed
```

往已关闭的通道 `send` 在任务体里抛了一个 `Error`，`await()` 原样把它交给外面，消息就是 `channel is closed`。只有任务体里逃出的 **Java 异常**会被包装，消息以 `task failed:` 开头。

## 22.6 没有结果的任务：run 和 scope_run

**为什么。** `Unit` 不是值（第 7 章），所以"只管做事、不返回结果"的任务不能写成 `Task[Unit]`。这种任务用 `run`，这种作用域体用 `scope_run`：

<<< @/snippets/book/ch22_run.spr

```text
> ready
```

- `concurrent.run(s, work)` 启动一个 `fn() -> Unit`，返回 `Job`。`job.await()` 等它结束，失败时抛 `Error`；`cancel()`、`is_done()` 等方法都和 `Task` 一样。
- `scope_run(body)` 就是给"不返回值的 `body`"用的 `scope()`：等所有任务和 job 结束，第一个失败取消其余的并抛出。

## 22.7 为什么没有 async/await

在别的语言里，`async` 会给函数染色：`async` 函数只能被另一个 `async` 函数等待，代码库从此分成两半。那些语言这么做是因为它们的线程太贵。JVM 的虚拟线程让阻塞变便宜之后，任务就只是"在另一个线程上跑的一个普通函数"，不需要新语法，也不需要给函数分类。结构来自作用域。

还没做的：`Task` 没有 `then`，组合靠 `await_all`、通道和普通函数；作用域没有自己的超时预算。

::: tip 学过其他语言？
如果你来自 JavaScript 或 Python：这里没有事件循环和 `await` 关键字。`spawn` 对应启动一个协程，`await()` 对应等待；区别是 Sprig 的任务真的跑在线程上，阻塞的 Java 调用（JDBC、HTTP、文件）放进去原样能用。
:::

## 本章小结

- 并发是自带的 `@std/concurrent` 模块；任务 = 在虚拟线程上跑的函数值。
- `scope` 拥有任务，离开作用域时等所有任务结束；`spawn`/`await` 取值，`parallel_map` 一行并行化一个循环。
- 通道传值：`send`、`receive`、`close`，`null` 表示关闭且取空。
- 共享计数用 `counter`，共享容器和字段用 `lock`；顶层 `var` 是模块状态，多任务同时写会产生竞争。
- 任务体里不能用 `throws`，容错写在任务体调用的函数里；第一个失败取消整个作用域。

## 动手练习

**练习 1（简单）。** 用 `parallel_map` 把 `[1, 2, 3]` 的每个数翻倍，打印结果。提示：lambda 的参数类型要写 `fn(n: Int) => ...`。

::: details 参考答案
<<< @/snippets/book/ch22_ex_double.spr

```text
[2, 4, 6]
```

结果按元素顺序返回，和任务完成的先后无关。
:::

**练习 2（中等）。** 一个任务通过通道发 10、20、30，主线程求和。提示：容量写 `channel[Int](8)`，发完 `close()`；收 `null` 结束循环；`run` 启动不返回值的生产任务。

::: details 参考答案
<<< @/snippets/book/ch22_ex_channel.spr

```text
60
```

`while true:` 加 `if next == null: break` 是标准的消费循环；别忘了 `job.await()`，作用域本来也会等它。
:::

**练习 3（中等）。** 三个任务各把同一个 counter 加一千次，打印每个任务的返回值以及计数器的最终值。提示：`concurrent.counter(0)`、`increment()`、`spawn`、`await_all`。

::: details 参考答案
<<< @/snippets/book/ch22_ex_counter.spr

```text
[1000, 1000, 1000]
3000
```

`counter` 的加法是原子的，三个任务交错也不会丢更新；`await_all` 按任务顺序返回结果。
:::

**练习 4（稍难）。** 建一个 2 条平台线程的池，用 `spawn_on` 让三个任务各返回 `id * 10`，按顺序打印结果。提示：`concurrent.pool(2)`、`spawn_on(s, pool, ...)`，最后 `pool.shutdown()`。

::: details 参考答案
<<< @/snippets/book/ch22_ex_pool.spr

```text
[10, 20, 30]
```

池限定 CPU 密集的任务最多两条同时在跑，任务仍然属于作用域；`shutdown()` 之后池不再接新任务，正在跑的照常完成。
:::

下一章：[工具链与 AI 助手](/tutorial/ch23-tooling)——让编译器替你把问题说清楚。
