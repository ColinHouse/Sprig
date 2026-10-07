# Concurrency

Sprig has no `async`/`await` syntax. Concurrency is a library: `libraries/sprig-concurrent` in the SDK, written in Sprig over a small Java kernel (`sprig.runtime.concurrent`) that owns the `java.util.concurrent` pools, queues and locks. The example project `examples/parallel_words` uses every part of it.

Declare the dependency in the project's `sprig.toml`, then `sprig resolve`:

```toml
[[dependency]]
name = "concurrent"
path = "../../libraries/sprig-concurrent"
```

## Tasks: a function value on another thread

```sprig
import "@concurrent/concurrent.spr" as concurrent

func word_count(line: String) -> Int:
    return line.split(" ").size()

let task = concurrent.spawn(fn() => word_count("the quick brown fox"))
print(task.await())
```

- `spawn` takes a `fn() -> T`, runs it on a shared pool and returns a `Task[T]`. Type arguments follow the usual rule: here `Int` comes from the lambda's result.
- `task.await()` waits for the value. A failure inside the task (a thrown `Error`, an integer overflow, a cancellation) is rethrown by `await()` as an `Error`, which is why `await()` declares `throws Error`.
- A task body is a plain function value, so it captures only `let` bindings and parameters: the data races on locals that other languages have cannot be written. Shared `MutableList`/`MutableMap` values and `var` fields are not protected; see locks and channels below.
- All threads are daemon threads: a program ends when its top-level statements end and does not wait for unfinished tasks. `await()` what matters.

## Many tasks: parallel_map and await_all

```sprig
let counts = concurrent.parallel_map(lines, fn(line: String) => word_count(line))
```

`parallel_map` starts one task per item and returns the results in item order; `await_all(tasks)` does the same for a list of `Task[T]`, rethrowing the first failure.

To bound the number of threads, use a pool:

```sprig
let pool = concurrent.pool(2)
let tasks: MutableList[concurrent.Task[Int]] = []
for line in lines:
    tasks.append(concurrent.spawn_on(pool, fn() => word_count(line)))
let results = concurrent.await_all(tasks)
pool.shutdown()
```

After `shutdown()` the pool accepts no more tasks (`spawn_on` reports an `Error`), the running ones finish, and `await_termination(millis)` waits for all of them.

## Channels: values between threads

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

- `channel[T](capacity)` is a bounded queue. `send` blocks while it is full; `receive()` blocks while it is empty and returns `null` once the channel is closed and drained. A channel never carries `null`, so `null` means one thing: the end.
- `try_send` and `receive_within(millis)` do not block.
- Sending on a closed channel fails at run time with an `Error`. `send` has no throws clause, so a task body can call it; the task's `await()` reports the failure.

## Counters, locks and latches

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

- A `Counter` is an atomic `Int`: `get`, `set`, `add`, `increment`, `decrement`, `compare_and_set`.
- A `Lock` is reentrant mutual exclusion: `run(action)` and `locked(lock, work)` run a function value while holding it. Use it around shared `MutableList`/`MutableMap` changes.
- A `Latch` counts down: each task calls `count_down()`, the waiter calls `await()` or `await_within(millis)`.

## Errors inside a task body

A task body is a `fn() -> T` without a throws clause, so it cannot call a function that declares `throws Error` directly. Two ways:

- `try`/`catch` inside a named function the body calls, and return a `variant` (`Ok`/`Failed`);
- let it fail, and `await()` reports it as an `Error` where you wait. `await_or(fallback)` has no throws clause, so one task body can wait on another.

The library functions a task body is likely to call (`send`, `receive`, `receive_within`, `sleep`, `run`, `locked`) have no throws clause either; invalid arguments fail at run time with an `Error`. That is the difference between `concurrent.sleep(millis)` and `time.sleep`: a sleeping task that is cancelled ends with an `Error`.

## Not there yet

No `async`/`await` syntax, no structured concurrency scopes, and a `Task` does not compose with `then`; compose with `await_all`, channels and plain functions. Cancellation is cooperative: `cancel()` interrupts the task's thread, which ends a `sleep` or a blocking `receive`; a loop that never blocks runs to its end.

The full API table is in [`libraries/sprig-concurrent/README.md`](https://github.com/ColinHouse/Sprig/blob/main/libraries/sprig-concurrent/README.md). After `sprig resolve`, `sprig api @concurrent/concurrent.spr --json` lists every signature with its comment, and `sprig help concurrency` is the compiler's own summary of the rules. The repository test `python3 tests/concurrent/check_concurrent.py` checks every behavior on this page on real threads.
