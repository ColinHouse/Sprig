# Concurrency

Sprig has no `async`/`await` syntax, and no colored functions. Concurrency is the bundled module `@std/concurrent.spr`, written in Sprig over a small Java kernel (`sprig.runtime.concurrent`). Tasks run on JDK 21 virtual threads, so ten thousand blocking tasks cost what ten cost, and ordinary blocking code (a Java `HttpClient.send`, JDBC, file I/O) runs inside a task unchanged. The example project `examples/parallel_words` uses every part of it.

## Scopes: every task has an owner

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

- `concurrent.scope(body)` runs `body` with a fresh `Scope` and returns its result. The body is usually a named function passed by reference, since a lambda is one expression.
- `spawn(s, work)` starts a `fn() -> T` on a virtual thread owned by the scope and returns a `Task[T]`. Type arguments follow the usual rule: `Int` comes from the lambda's result.
- **Leaving the scope waits for every task**, awaited or not. No task outlives the function that started it.
- **The first failure cancels its siblings** and is rethrown by `scope()` as an `Error`, so `scope` declares `throws Error`. A failure inside a task is also reported by that task's `await()`; catching it there does not undo the scope's failure. To tolerate a failure, handle it inside the task body, for example by returning a variant such as `Ok`/`Failed`. `await_or(fallback)` doesn't tolerate anything: it gives the fallback for a failed or cancelled task without a `throws` clause, so one task body can wait on another, but the scope still fails.
- A task cancelled on purpose (`task.cancel()`, `s.cancel_all()`) is not a failure. Cancellation interrupts the task's thread and takes effect at its blocking points: `sleep`, `await`, a channel `receive`, Java I/O that honors interruption. A loop that never blocks runs to its end.
- There is no unscoped `spawn`: a task always belongs to the scope it was started in, and starting one after the body returned is an `Error`.
- A task body is a plain function value, so it captures only `let` bindings and parameters: the data races on locals that other languages have cannot be written. Shared `MutableList`/`MutableMap` values and `var` fields are not protected; see locks and channels below.

## Work with no result: run and scope_run

`Unit` is not a value, so a task that only has an effect can't be a `Task[Unit]`. Use `run` for it, and `scope_run` for a scope body that only coordinates:

```sprig
import "@std/concurrent.spr" as concurrent

func announce(line: String) -> Unit:
    print("> " + line)

func announce_all(s: concurrent.Scope) -> Unit throws Error:
    for line in ["ready", "steady"]:
        concurrent.run(s, fn() => announce(line))

concurrent.scope_run(announce_all)
```

- `run(s, work)` starts a `fn() -> Unit` and returns a `Job`. It has the same methods as a task: `await()` waits and reports a failure as an `Error` but returns nothing, and `cancel()` and `is_done()` work as before.
- `scope_run(body)` is `scope()` for a body that returns nothing, with the same rules: it waits for every task and job, and the first failure cancels the rest and is rethrown.
- The two announcements may print in either order, since they run at the same time.

## Many tasks: parallel_map and await_all

```sprig
let counts = concurrent.parallel_map(lines, fn(line: String) => word_count(line))
```

`parallel_map` opens a scope of its own, starts one task per item and returns the results in item order once every task has ended; `await_all(tasks)` does the same for a list of `Task[T]` inside your scope, rethrowing the first failure.

Virtual threads make the number of tasks cheap, so there is no need to bound I/O-bound work. To bound CPU-bound work to a few platform threads, use a pool:

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

A pooled task still belongs to the scope. After `shutdown()` the pool accepts no more tasks (`spawn_on` reports an `Error`), the running ones finish, and `await_termination(millis)` waits for all of them. `parallel_map_on(pool, items, transform)` is the one-line form.

## Channels: values between threads

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

`next` is a `var`, so `while next != null` does not narrow it. Copy it to `let word` and check `word`; inside that check, the value can be used as a non-null string.

- `channel[T](capacity)` is a bounded queue. `send` blocks while it is full; `receive()` blocks while it is empty and returns `null` once the channel is closed and drained. A channel never carries `null`, so `null` means one thing: the end.
- `try_send` never blocks and returns `false` when the channel is full or closed; `receive_within(millis)` waits at most `millis` and returns `null` on timeout (check `is_closed()` to tell timeout from closed).
- Sending on a closed channel fails at run time with an `Error`. `send` has no throws clause, so a task body can call it; the task's `await()` and the scope report the failure.

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
- A `Lock` is reentrant mutual exclusion: `run(action)` and `locked(lock, work)` run a function value while holding it. Use it around shared `MutableList`/`MutableMap` changes. It is a `java.util.concurrent` lock, so a task holding it never pins its carrier thread.
- A `Latch` counts down: each task calls `count_down()`, the waiter calls `await()` or `await_within(millis)`.

## Errors inside a task body

A task body is a `fn() -> T` without a throws clause, so it cannot call a function that declares `throws Error` directly. Two ways:

- `try`/`catch` inside a named function the body calls, and return a `variant` (`Ok`/`Failed`);
- let it fail: `await()` reports it where you wait, and the scope reports it when it ends. `await_or(fallback)` has no throws clause, so one task body can wait on another.

The module functions a task body is likely to call (`send`, `receive`, `receive_within`, `sleep`, `run`, `locked`) have no throws clause either; invalid arguments fail at run time with an `Error`. That is the difference between `concurrent.sleep(millis)` and `time.sleep`: a sleeping task that is cancelled ends at once.

## Why not async/await

`async` colors functions: an `async` function can only be awaited from another one, and a code base splits in two. Languages adopt it when their runtime has no cheap threads. Since JDK 21 the JVM has virtual threads, so blocking is cheap and a task is just a function running elsewhere. Sprig takes that road, as Java and Go do: no new syntax, no colored functions, and structure from scopes instead of from the type system.

## Not there yet

A `Task` does not compose with `then`; compose with `await_all`, channels and plain functions. Scopes have no deadline or time budget of their own (`await_within` is per task), and `java.util.concurrent.StructuredTaskScope` is not used while it is a preview API.

`sprig api @std/concurrent.spr --json` lists every signature with its comment, and `sprig help concurrency` is the compiler's own summary of the rules. The repository test `python3 tests/concurrent/check_concurrent.py` checks every behavior on this page on real threads, including ten thousand sleeping tasks finishing together and a loopback socket echo inside a task.
