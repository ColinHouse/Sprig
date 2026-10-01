# Sprig tutorial

Sprig is for people and coding agents. Sprig does not try to make coding agents smarter; it tries to give them less to guess. The same principle helps human readers: **agent-friendly should also mean review-friendly.** This course starts with runnable programs and uses source files from the repository. The docs gate compiles and runs those snippets and checks their output.

The published Beta SDK is v0.5.0-beta.1. Start with [release status](/en/project/release-status) and query the installed SDK with `sprig capabilities --json` for its exact feature set. The tutorial is tested against the published SDK.

## 1. Install and run a first program

Install an SDK from the releases page and use JDK 17 or newer. The SDK does not include a JDK. After creating a project, `sprig run` checks Sprig, generates Java, calls `javac`, and launches the JVM.

```sh
sprig init hello
cd hello
sprig resolve
sprig run
```

Expected output: `Hello, Sprig!`. Here is that program's actual source:

<<< @/snippets/tutorial/hello.spr

## 2. Bindings and types

Local variables can infer their type from the initializer. `let` cannot be rebound; `var` can. Conditions must be `Bool`: Sprig does not treat integers or strings as truthy values.

<<< @/snippets/variables.spr

Exercise: add an immutable `name` and build a greeting. Then assign a `String` to an `Int`, read the diagnostic code, and fix the program.

## 3. Explicit functions

Every function parameter and return type is written down. Callers can see inputs, outputs and possible failures in the signature.

<<< @/snippets/functions.spr

Exercise: add a function that accepts two `Int` values and returns the larger one. Add assertions for two boundary cases.

## 4. Organize data with classes

Class fields have explicit types, and constructors use field names. A field with a default can be omitted; unknown or missing required fields are errors.

<<< @/snippets/classes.spr

Exercise: add an area method to `Rectangle`. Check that an invalid field type is rejected before the program runs.

## 5. Collections and a small transformation

`List[T]` is read-only; use `MutableList[T]` for a phase that changes elements. Explicit conversion creates a new outer collection. This word counter shows a complete small transformation from text to ordered output.

<<< @/snippets/tutorial/word_count.spr

Exercise: make counting case-insensitive and explain how punctuation is handled. Full natural-language tokenization calls for a dedicated text library.

## 6. Put failures in the type

Functions that can fail declare `throws`. Callers must catch the error or declare that effect too; failure is not silently replaced by a default value.

<<< @/snippets/errors.spr

Exercise: represent a missing configuration key as an `Error`, then catch it at the command-line boundary and print useful context.

## 7. Variants, match and nullability

`variant` represents a finite set of cases. `match` must cover every case, and a case binding retains its payload's static type. Nullable values must be checked and narrowed before use.

<<< @/snippets/variants.spr

<<< @/snippets/nullable.spr

Exercise: add a `Negate` case to an expression AST, then check that every visitor handles it.

## 8. Build a local Task Tracker

Now combine the types, collections, errors, and file/JSON APIs from earlier chapters into a local command-line task list. It reads and writes UTF-8 JSON and uses no network service. Create a project:

```sh
sprig init task-tracker
cd task-tracker
# Save the source below as src/main.spr
sprig resolve
sprig run -- add "Read the Sprig tutorial"
sprig run -- add "Build a small tool"
sprig run -- list
sprig run -- done 1
sprig run -- list
```

Here is the complete source. It stores data in `tasks.json` in the current working directory; set `SPRIG_TASKS_FILE` to choose another local file. JSON decoding checks field types, and file operations use the standard library's UTF-8 API.

<<< @/snippets/tutorial/task_tracker.spr

This is suitable for learning and small single-process data, not coordinated concurrent writers. The repository's [matching source and independent runtime check](https://github.com/ColinHouse/Sprig/tree/main/examples/task-tracker) verify add, list, done and malformed JSON behavior.

## 9. Ask the compiler for evidence

When checking fails, read the stable diagnostic instead of guessing about types. This example deliberately puts text in an `Int`:

<<< @/snippets/tutorial/type_error.spr

The expected code is `SPR-TYPE-ASSIGN`, with expected type `Int` and actual type `String`. Use `sprig check --json <file>` for machine-readable locations and types, and `sprig explain SPR-TYPE-ASSIGN --json` for repair guidance. Fix the source and check again; do not let a tool insert a conversion that could change the program's meaning.

```sprig
let count: Int = 3
```

A concise agent workflow is: read `AGENTS.md` and the current reference → run `sprig capabilities --json` → make the smallest change → run `sprig check --json` / `sprig test` → review the diff. Whether a command is available depends on the installed version's capability output.

## 10. Call ordinary Java APIs

Sprig targets the JVM and can explicitly import supported Java classes and members. When a signature is unclear, query the local JDK rather than guessing an overload:

```sh
sprig api java.time.LocalDate --json
```

Then read the [real interop example](https://github.com/ColinHouse/Sprig/blob/main/website/snippets/jvm_interop.spr) and the [JVM interoperability guide](/en/guide/jvm-interop). Gradle, Maven or Loom still owns complex framework classpaths. Sprig owns application logic; a small Java adapter can bridge API shapes not yet supported directly.

## Where to go next

- [Language tour](/en/guide/language-tour): quick lookup for syntax and implementation boundaries.
- [Projects and dependencies](/en/guide/projects): local packages, Git/Maven dependencies and lockfiles.
- [Example projects](https://github.com/ColinHouse/Sprig/tree/main/examples): continue from Task Tracker to JSON CLI, SQLite, Web and Maven.
- [Agent workflow and diagnostics reference](/en/reference/tooling/agent-guide): query tools, stable diagnostics and reproducible repairs.
- [Feature status and known limitations](/en/reference/language/feature-status): review implemented capabilities and explicit limits.
