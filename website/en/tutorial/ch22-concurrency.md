# 22. Concurrency

Most of a program's life is spent waiting — for the disk, the network, the user. The CPU sits idle while that happens. This chapter shows how Sprig puts that waiting time to work by doing several things at once, and which traps await when tasks touch the same data.

In this chapter you will learn to:

- what a thread and "blocking" mean, and why many threads became cheap in JDK 21;
- start tasks and collect results with a `@std/concurrent` scope;
- pass values between tasks through a channel;
- guard atomic counts with a counter, and shared containers with a lock;
- why a top-level `var` is shared between tasks, and when it races;
- how a failed task's error reaches you.

## 22.1 Threads and blocking

**Why.** By default every line you write runs in order: the next line starts when the previous one finishes. A **thread** is another line of execution, so two threads make progress at the same time. When a task is stuck waiting (the term is **blocking**, for instance waiting on a network reply), the operating system can park it and let other threads use the CPU.

Since JDK 21, Java has **virtual threads**: starting ten thousand blocking tasks costs about what ten cost. That is why Sprig does not invent `async`/`await` syntax; it gives you "run a function value on another thread", packaged in the bundled `@std/concurrent` module.

Two waits at the same time:

<<< @/snippets/book/ch22_two_tasks.spr

```text
tick tack
```

- `nap("tick")` and `nap("tack")` each wait 10 milliseconds. Both tasks are started before either is awaited, so the two waits overlap: the total is not 20 milliseconds.
- `first.await()` and `second.await()` fetch the results in source order, so the joined text is always `tick tack`; whichever task finishes first does not matter.

## 22.2 A scope owns its tasks

**Why.** A background task whose result nobody waits for can be lost when the program ends. Sprig's answer is the **scope**: it owns every task started inside it and waits for all of them before it is done.

<<< @/snippets/book_en/ch14_scope.spr

```text
7
[2, 3]
```

- `concurrent.scope(count_two)` creates a fresh scope `s`, hands it to `count_two`, and returns whatever it returns. `count_two` is a named function passed by reference (chapter 15's function references).
- `concurrent.spawn(s, fn() => word_count("..."))` starts a task and returns a `Task[Int]`. A task body is a function value with a result and no thrown errors.
- `await()` waits for that task's result. The scope guarantees: when `scope` returns, every task started inside it has ended.
- Line 4, `concurrent.parallel_map(...)`, is the everyday shortcut: one task per element, results in element order once all have ended.

## 22.3 Channels: passing values between tasks

**Why.** Tasks constantly need to hand data to each other, but they must not read a variable another task is changing. A **channel** is a bounded queue: one side `send`s, the other `receive`s; when it is full the sender waits, when it is empty the receiver waits.

<<< @/snippets/book_en/ch14_channel.spr

```text
got one
got two
got three
6
```

- `concurrent.channel[String](4)`: capacity 4, and the element type must be written.
- The producer task `send`s three values and calls `close()`; the main thread `receive`s in a `while true:` loop and `break`s on `null` — a channel never carries `null`, so `null` has exactly one meaning: "closed and drained".
- When `scope(drain)` ends, `produce` has ended; `producer.await()` returns `3`, and adding the three received values prints `6`.
- `send` blocks while the channel is full and `try_send` does not; `receive()` blocks while empty and `receive_within(millis)` waits at most that long.

## 22.4 Counters, locks and shared state

**Why.** When two tasks change one number at once, the read-modify-write steps can interleave and updates go missing. Sprig ships two tools: the atomic **counter**, and a **lock** guarding a section of code.

<<< @/snippets/book_en/ch14_counter.spr

```text
[1, 2, 3, 4]
4000
4
```

- `concurrent.counter(0)` is an atomic `Int`: four tasks each `increment` a thousand times and the result is exactly 4000. A counter also has `add`, `get`, `set` and `compare_and_set`.
- The shared `MutableList` is guarded by `concurrent.lock()`: `guard.run(fn() => seen.append(id))` runs the append while holding the lock, so four tasks never step on each other.
- Each task's own local `var i` is visible only to that task and needs no protection.

### A top-level var is visible to every task

Chapter 15 said a lambda cannot capture a function's `var` local:

<<< @/snippets/book/ch11_capture.spr

```text
SPR-TYPE-CAPTURE [TYPE] main.spr:3:16: Lambda captures mutable local 'counter'
  hint: Copy it into a 'let' binding before the lambda, or use a class field.
```

**But a top-level `var` is not a local variable** — it is module state. A lambda can read it and write it, and it is exactly what tasks share:

<<< @/snippets/book/ch22_shared_var.spr

```text
HELLO
```

Only one task writes here, so the result is certain: the main thread prints after the task has finished. With two tasks writing the same top-level `var`, the final value depends on scheduling and **cannot be predicted**. Shared mutable state should become a `concurrent.counter` (atomic), be wrapped in a `concurrent.lock`, or be avoided by letting each task write only its own local value and collecting results with `await()`.

## 22.5 When a task fails

**Why.** A task runs on another thread, and a failure must not vanish silently. A task body has type `fn() -> T` with no `throws`, so errors are handled in the named function it calls:

<<< @/snippets/book_en/ch14_tolerate.spr

```text
skipped: not a number: x
[3, 0, 5]
```

- `count_or_zero` does its own `try`/`catch`, prints a line and returns 0 on failure, so the task always succeeds.
- **The first task failure cancels the scope's other tasks** and is rethrown by `scope()` as an `Error`. `await_or(fallback)` only keeps the wait from throwing; it cannot save the scope: to end successfully, a failure must be handled inside the task body.
- Cancellation is cooperative: `cancel()` interrupts the task's thread, taking effect in `sleep`, `await` and a blocking `receive`.

When an error does escape a task body, `await()` rethrows it. A Sprig `Error` keeps its own message:

<<< @/snippets/book/ch22_error.spr

```text
caught: channel is closed
```

`send` on a closed channel raises an `Error` in the task body, and `await()` hands it out unchanged with the message `channel is closed`. Only a **Java exception** escaping a task body is wrapped, with a message starting `task failed:`.

## 22.6 Tasks with no result: run and scope_run

**Why.** `Unit` is not a value (chapter 7), so a "do something, return nothing" task cannot be a `Task[Unit]`. Such work uses `run`, and such a scope body uses `scope_run`:

<<< @/snippets/book/ch22_run.spr

```text
> ready
```

- `concurrent.run(s, work)` starts a `fn() -> Unit` and returns a `Job`. `job.await()` waits for it and throws `Error` on failure; `cancel()`, `is_done()` and the rest match `Task`.
- `scope_run(body)` is `scope()` for a body that returns nothing: it waits for every task and job, and the first failure cancels the rest and is rethrown.

## 22.7 Why there is no async/await

In other languages `async` colors a function: an `async` function can only be awaited inside another `async` function, splitting a codebase in two. Those languages did that because their threads were expensive. Once JVM virtual threads make blocking cheap, a task is just "an ordinary function running on another thread": no new syntax, no function colors. The structure comes from scopes.

What does not exist yet: a `Task` has no `then`, so composition goes through `await_all`, channels and plain functions; a scope has no timeout budget of its own.

::: tip Coming from another language?
From JavaScript or Python: there is no event loop and no `await` keyword. `spawn` starts something like a coroutine and `await()` waits for it; the difference is that a Sprig task really runs on a thread, so blocking Java calls (JDBC, HTTP, files) work unchanged inside one.
:::

## Summary

- Concurrency is the bundled `@std/concurrent` module; a task is a function value on a virtual thread.
- A `scope` owns its tasks and waits for all of them; `spawn`/`await` fetch results, and `parallel_map` parallelizes a loop in one line.
- Channels carry values: `send`, `receive`, `close`; `null` means closed and drained.
- Count with `counter`, guard shared containers and fields with `lock`; a top-level `var` is module state and races when tasks write it together.
- A task body cannot use `throws`: handle errors in the function it calls. The first failure cancels the scope.

## Exercises

**Exercise 1 (easy).** Use `parallel_map` to double each number in `[1, 2, 3]` and print the result. Hint: the lambda parameter type must be written: `fn(n: Int) => ...`.

::: details Answer
<<< @/snippets/book/ch22_ex_double.spr

```text
[2, 4, 6]
```

Results come back in element order, no matter which task finished first.
:::

**Exercise 2 (medium).** Have one task send 10, 20 and 30 through a channel, and sum them on the main thread. Hint: `channel[Int](8)`, `close()` after sending; end the loop on `null`; start the unit-returning producer with `run`.

::: details Answer
<<< @/snippets/book/ch22_ex_channel.spr

```text
60
```

`while true:` with `if next == null: break` is the standard consumption loop; the `job.await()` is good manners even though the scope would wait anyway.
:::

**Exercise 3 (medium).** Have three tasks increment one counter a thousand times each, then print each task's return value and the counter's final value. Hint: `concurrent.counter(0)`, `increment()`, `spawn`, `await_all`.

::: details Answer
<<< @/snippets/book/ch22_ex_counter.spr

```text
[1000, 1000, 1000]
3000
```

The counter's add is atomic, so interleaving tasks lose no updates; `await_all` returns results in task order.
:::

**Exercise 4 (harder).** Create a pool of two platform threads, use `spawn_on` to have three tasks each return `id * 10`, and print the results in order. Hint: `concurrent.pool(2)`, `spawn_on(s, pool, ...)`, then `pool.shutdown()`.

::: details Answer
<<< @/snippets/book/ch22_ex_pool.spr

```text
[10, 20, 30]
```

The pool limits CPU-bound work to two threads at a time while the tasks still belong to the scope; after `shutdown()` the pool takes no new tasks and the running ones finish normally.
:::

Next chapter: [Tools and AI assistants](/en/tutorial/ch23-tooling) — letting the compiler explain the problem for you.
