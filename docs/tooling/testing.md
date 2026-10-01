# Testing Sprig projects

`sprig test` runs ordinary Sprig programs. It adds no test keyword, annotation,
function discovery or reflection rule. A test succeeds when it checks, compiles
with `javac`, and exits with status 0 in a child JVM. Use the existing `assert`
function or throw `Error` to fail a test.

```text
sprig resolve
sprig test [PATH] [--filter TEXT] [--classpath PATH] [--json] [--offline]
```

Run the command inside a `sprig.toml` project or pass a project path. A current
`sprig.lock` is required. With no `PATH`, the runner discovers `tests/**/*.spr`.
You can pass the project directory, a subdirectory, or one `.spr` file. The
runner sorts names lexically relative to `tests/`; `--filter` selects names
containing the supplied text. It uses the project's normal source imports,
Sprig dependencies, locked Maven classpath and offline cache rules. `sprig
test` never resolves or updates a lockfile for you.

`--classpath PATH` adds host Java JARs or output directories to compilation
and test execution; repeat the option or pass a platform-separated classpath.
Relative paths use the command's working directory. The Gradle plugin supplies
the selected source set's real compile classpath so Sprig tests can check
Java bridge types without building a second classpath by hand.

## Runtime tests

For example, `tests/table_squares.spr` can contain a table without extra test
syntax:

```sprig
class Case:
    let input: Int
    let expected: Int

let cases: List[Case] = [Case(input=2, expected=4), Case(input=7, expected=49)]
for item in cases:
    assert(item.input * item.input == item.expected)
```

Each file gets a fresh JVM and a unique temporary directory. The child runs
with the project root as its working directory and receives
`SPRIG_TEST_TMPDIR`; the runner removes that directory after the test. A test
that exits nonzero, raises an uncaught error, fails checking or `javac`, or runs
longer than 30 seconds fails. Tests currently run sequentially.

`@std/test.spr` provides the test-specific helpers:

```sprig
import "@std/test.spr" as testing
import "@std/files.spr" as files

let path = files.join(testing.temp_dir(), "note.txt")
files.write_utf8(path, "ready")
assert(files.read_utf8(path) == "ready")

let child = testing.run_process(["java", "--version"])
assert(child.exit_code == 0)
assert(child.stdout.length() + child.stderr.length() > 0)
```

`temp_dir() -> String throws Error` fails clearly outside `sprig test`.
`run_process(command: List[String]) -> ProcessResult throws Error` passes an
argv vector directly to the operating system, with no shell interpretation.
`ProcessResult` has `exit_code: Int32`, `stdout: String`, and `stderr: String`.
It captures the two output streams separately as UTF-8. A nonzero child exit
is a result value; an empty command, failure to start, invalid UTF-8 output,
or a 30-second child-process timeout raises `Error`. Child processes inherit
the test's environment and project working directory.

## Expected compiler failures

Place negative fixtures under `tests/compile_fail/`. Each `.spr` file needs a
sibling `.expect.toml` file:

```text
tests/compile_fail/nullable.spr
tests/compile_fail/nullable.expect.toml
```

```toml
codes = ["SPR-TYPE-NULLABLE"]
exact = true
```

The runner calls the Sprig checker, without invoking `javac` or executing the
fixture. Every listed code must occur among **error** diagnostics. By default,
additional error codes are allowed. `exact = true` rejects additional error
codes. Warnings are retained in the structured result but do not change the
code comparison. Codes are stable; complete English messages are not matched.
The expectation schema accepts only root `codes` (a nonempty array of unique
`SPR-*` strings) and optional bare `exact = true` or `false`. Missing or
malformed expectations are runner errors.

## Output and exit status

Human output prints one `PASS` or `FAIL` line per file and a summary. Failures
include the relevant compiler/runtime diagnostic. `--json` prints one object
with `schemaVersion`, `toolVersion`, `command`, `exitCode`, `summary`, `tests`,
and top-level `diagnostics`. Each test row has `name`, absolute `path`, `mode`
(`run` or `compile_fail`), `status`, structured `diagnostics`, `programOutput`,
and `programErrorOutput`; a failed row also has `failure`. Rows and diagnostics
retain deterministic discovery/checking order. Program output and filesystem
paths are the program's actual values and may vary if the program makes them
vary.

Exit 0 means every discovered test passed. Exit 1 means at least one program or
expected-diagnostic comparison failed. Exit 2 means an invalid command,
project/lock error, or malformed test expectation. The existing Python compiler
regression harness remains useful for compiler implementation tests; this
command is the product-facing package and application test runner.

See the runnable five-case project in `examples/test_runner/`.
