# sprig-concurrent 0.1 development library

Tasks, pools, channels, counters, locks and latches for ordinary Sprig
programs. The API is Sprig (`libraries/sprig-concurrent/src/concurrent.spr`);
a small Java kernel in `sprig.runtime.concurrent` owns the
`java.util.concurrent` executor, queue and lock mechanics. This is a source
checkout library, not a published registry package.

## Install and run

```toml
[[dependency]]
name = "concurrent"
path = "../../libraries/sprig-concurrent"
```

```sh
sprig resolve
sprig run
```

`examples/parallel_words` uses every part of the library; `sprig api
@concurrent/concurrent.spr --json` lists the signatures and the comment above
each declaration.

## Model

```sprig
import "@concurrent/concurrent.spr" as concurrent

func word_count(line: String) -> Int:
    return line.split(" ").size()

let counts = concurrent.parallel_map(lines, fn(line: String) => word_count(line))

let task = concurrent.spawn(fn() => word_count("a b c"))
print(task.await())
```

- **A task body is a plain function value** (`fn() -> T`), so it captures only
  immutable bindings (`let`, parameters): the data races on locals that other
  languages have cannot be written. Shared `MutableList`/`MutableMap` values and
  `var` fields are not protected; guard them with a `Lock`, count with a
  `Counter`, or send values through a `Channel` instead.
- **Errors stay explicit.** A task body has no throws clause. A failure inside
  it (a thrown `Error`, a checked arithmetic failure, a cancelled sleep) ends
  the task, and `await()` rethrows it as an `Error` at the place that waits.
  `await_or(fallback)` has no throws clause, so one task body can wait on
  another. The library functions a task body is likely to call (`send`,
  `receive`, `receive_within`, `sleep`, `run`, `locked`) have no throws clause
  either; their argument checks fail at run time with an `Error`.
- **Threads are daemon threads.** A program whose top-level statements end does
  not wait for unfinished tasks: `await()` what matters, or
  `pool.shutdown()` + `await_termination`.

| Area | API |
|---|---|
| Task | `spawn[T](work: fn() -> T) -> Task[T]`; `await() throws Error`, `await_within(millis) throws Error`, `await_or(fallback)`, `is_done()`, `cancel()`, `is_cancelled()` |
| Pool | `pool(threads) throws Error`, `spawn_on[T](pool, work) throws Error` (a shut-down pool), `threads()`, `shutdown()`, `shutdown_now()`, `is_shutdown()`, `await_termination(millis)` |
| Many tasks | `await_all[T](tasks) -> List[T] throws Error` (first failure rethrown), `parallel_map[T, R](items, transform) -> List[R] throws Error`, `parallel_map_on(pool, items, transform)`; results keep item order |
| Channel | `channel[T](capacity) throws Error`; `send(value)` blocks while full and fails on a closed channel, `try_send(value) -> Bool`, `receive() -> T?` blocks and is `null` once closed and drained, `receive_within(millis) -> T?`, `close()`, `is_closed()`, `size()` |
| Counter | `counter(start)`; `get`, `set`, `add(delta)`, `increment`, `decrement`, `compare_and_set(expected, updated)` on an `Int` |
| Lock | `lock()`; `run(action: fn() -> Unit)`, `locked[T](lock, work: fn() -> T) -> T`, `is_held_by_current_thread()`; reentrant |
| Latch | `latch(count) throws Error`; `count_down()`, `remaining()`, `await()`, `await_within(millis) -> Bool throws Error` |
| Sleep | `sleep(millis)`: pauses the current thread; a cancelled task's sleep ends the task |

Type arguments follow the usual rule: `spawn(fn() => 42)` infers `T` from the
lambda's result, `channel[String](16)` writes it because no argument says what
`T` is.

## Limits

- No async/await syntax, no structured concurrency scopes, no futures that
  compose with `then`; compose with `await_all`, channels and plain functions.
- Interruption is cooperative: `cancel()` interrupts the task's thread, which
  ends a `sleep` or a blocking `receive`; a loop that never blocks runs to its
  end.
- A `Channel` carries values, never `null`; `receive()` returning `null` means
  the channel is closed and drained.
- The shared pool grows with demand (cached daemon threads). CPU-bound work
  that should be bounded goes on a `pool(n)`.
