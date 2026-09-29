# Built-in `sprig test` Implementation Plan

**Goal:** Let a Sprig project run ordinary `.spr` programs as isolated tests and assert compiler diagnostic codes for negative fixtures.

**Architecture:** A dedicated CLI command discovers project tests, reuses the existing dependency resolver, compiler, Java generator and `javac` bridge, and runs each compiled program in a child JVM. The runner owns per-test temporary directories, timeout and structured reporting. A small bundled `@std/test.spr` façade exposes the temporary path and argv-based child process service.

**Tech Stack:** Java 17 stage-0 compiler/runtime, existing strict `Toml` parser, Python integration tests, bundled Sprig std modules.

**Spec:** `docs/TESTING.md` (the product-facing contract created with this change; source request is the built-in `sprig test` brief in this task).

## Global Constraints

- Keep tests as ordinary Sprig programs; no grammar, type-system, annotation or reflection-discovery changes.
- Default discovery is `tests/**/*.spr`, with `tests/compile_fail/**/*.spr` checked against sibling `.expect.toml` files.
- Runtime cases execute separately, with `SPRIG_TEST_TMPDIR` and a finite timeout; argv process calls never invoke a shell.
- Use the same locked Sprig/Maven dependencies and offline rules as project builds.
- `--json` emits one deterministic object and structured diagnostics.
- The existing Python compiler regression harness stays in place; one repository test is also dogfooded through `sprig test`.

---

### Task 1: CLI discovery and compile-fail contract

**Files:** Create `tests/test_runner/check_test_runner.py`, `compiler/src/main/java/sprig/compiler/cli/TestCommand.java`; modify `Main.java`, `JsonWriter.java`, `scripts/test.py`.

**Interfaces:** `TestCommand.run(String[] args) -> int` owns parsing, project discovery and test rows. `JsonWriter.diagnosticMap(Diagnostic, String) -> Map<String,Object>` supplies the existing diagnostic schema to row JSON.

- [x] Write integration fixtures for default project discovery, deterministic order, `--filter`, no-project error, missing/malformed expectation, subset codes and `exact=true`. A fixture `tests/compile_fail/nullable.spr` with `codes = ["SPR-TYPE-NULLABLE"]` demonstrates matching compiler diagnostics.
- [x] Run `python3 tests/test_runner/check_test_runner.py`; confirm `sprig test` is an unknown command before implementing it.
- [x] Add the CLI dispatch, argument validation and deterministic discovery. Parse expectations with `Toml.parse(lines, true)` and reject unknown keys, wrong kinds and missing codes.
- [x] Compile negative files with `Compiler.compile` using the project `DependencyResolver.Result`; compare only error-severity codes. Return runner status 2 for invalid fixture configuration and 1 for a test mismatch.
- [x] Add JSON rows in lexical relative-path order and compact human `PASS`/`FAIL` output. Re-run the focused suite.

### Task 2: Isolated runtime compilation and execution

**Files:** Modify `JavaRunner.java` and `Main.java`; extend `TestCommand.java` and `tests/test_runner/check_test_runner.py`.

**Interfaces:** A new `JavaRunner.run` overload accepts a child environment map and finite timeout, with `Result.timedOut`. `TestCommand` reuses `Compiler`, `JavaGenerator`, `JavacRunner`, runtime sources and JVM classpath; no recursive `sprig` invocation.

- [x] Add runtime fixtures that check successful output, compile failure, nonzero child exit, uncaught error, per-test temporary path isolation, and state isolation (`System.exit` in one child does not terminate the runner).
- [x] Run the focused suite and verify these fixtures fail while runtime execution is absent.
- [x] Compile each test to a separate generated-source/classes directory, run the main class in a new JVM, set `SPRIG_TEST_TMPDIR` to a unique sibling directory, capture UTF-8 stdout/stderr and classify timeouts.
- [x] Preserve source-location diagnostic mapping for `javac` and runtime failures, remove each temporary directory after the case, and rerun the focused suite.

### Task 3: Bundled test utilities and process API

**Files:** Create `runtime/src/main/java/sprig/runtime/test/HostTest.java`, `std/test.spr`; extend `tests/test_runner/check_test_runner.py`; update tracked example lockfiles whose bundled-std digest changes.

**Interfaces:** `@std/test.spr` exports `temp_dir() -> String throws Error`, `ProcessResult` with `exit_code: Int32`, `stdout`, `stderr`, and `run_process(command: List[String]) -> ProcessResult throws Error`. The Sprig façade copies its typed list into a narrow Java argv adapter; the host starts `ProcessBuilder` and captures both streams in UTF-8.

- [x] Write a Sprig fixture using `temp_dir`, file write/read, and a child command that independently produces stdout, stderr and a nonzero exit; assert `temp_dir()` fails clearly outside the test runner.
- [x] Run the fixture to establish the missing-module failure.
- [x] Implement only the host methods needed for these functions, mapping process start/decoding errors to Sprig `Error` and preserving nonzero child status as data.
- [x] Re-run focused integration tests and regenerate affected tracked lockfiles with the rebuilt SDK.

### Task 4: Product dogfood, docs and full gate

**Files:** Create `docs/TESTING.md` and a small example project under `examples/`; modify `AGENT_GUIDE.md`, `docs/FEATURE_STATUS_IMPLEMENTED.md`, `docs/KNOWN_LIMITATIONS.md`, CLI help/catalog data and `scripts/test.py`.

**Interfaces:** `sprig help testing --json` and `capabilities --json` advertise concrete test-runner support. The example contains ordinary, table-driven, temp-file, process and compile-fail cases.

- [x] Run the example project through `sprig resolve` and `sprig test --json`; assert case order, output and diagnostic payloads from an independent Python oracle.
- [x] Add documentation showing the exact commands, expectation TOML, `@std/test.spr`, exit rules, timeout and limits. Keep existing Python oracle tests.
- [x] Run `python3 tests/test_runner/check_test_runner.py`, `python3 scripts/test-stdlib.py` and `./scripts/verify.sh`; inspect exit codes and logs before reporting success.
- [x] Review `git diff --check`, stage only this feature, and create one focused PR. Do not include JVM generic interoperability work from PR #50.
