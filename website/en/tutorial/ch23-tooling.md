# 23. Tools and AI assistants

`sprig` is not just a "run button". It is always ready to answer: what does this error mean? Does the language have a feature? How do I call this Java method from Sprig? This chapter is about asking the compiler instead of guessing, and about getting an AI coding assistant into the same habit.

In this chapter you will learn to:

- the division of labour between `check` and `run`, and how to read every part of an error;
- `explain`, `help`, `api`, `capabilities` and `doctor`;
- the `--json` output almost every command supports;
- `fmt` and the `sprig lsp` server behind editors;
- `build --bundle`, which ships a program with its own Java runtime;
- a few lessons for writing Sprig with an AI assistant.

## 23.1 check first, run later

**Why.** Before a program runs, the compiler can already find every static error. The loop is: edit, `sprig check`, read the errors, edit again; use `sprig run` only when you want to see the result.

Here is a deliberately broken program:

<<< @/snippets/book/ch23_broken.spr

```text
SPR-NUM-MIXED [TYPE] main.spr:1:7: Operator '+' has no implicit conversion between Int and Float (expected matching numeric families, actual Int and Float)
  hint: Convert the Int side: 1.toFloatExact().
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

An error has six parts, read top to bottom:

- `SPR-NUM-MIXED`: the **code**. It is stable, and can be searched for or handed to `sprig explain`.
- `[TYPE]`: the **phase**, telling you which step of compilation found it. Common ones: `SYNTAX`, `NAME`, `TYPE`, `FLOW`, `RUNTIME`, `JVM`.
- `main.spr:1:7`: **file:line:column**. Columns start at 1, so 1:7 is the seventh character of the first line, where the `+` is.
- A one-sentence message, followed by `expected` and `actual` in parentheses.
- `hint:`: a fix you can follow directly.
- The closing `1 error(s)` summary; `check` lists every error, not just the first. A file with two mistakes prints both reports.

## 23.2 Stuck? explain

**Why.** Behind every code is a full explanation: why the rule exists, common shapes, safe fixes, and good and bad examples. `sprig explain CODE` prints it:

```text
$ sprig explain SPR-FLOW-THROWS
SPR-FLOW-THROWS: A recoverable error must be declared with throws or caught.
Why it matters: Checked errors are visible at every call site; nothing fails silently.
Common causes:
  - A call may throw Error or a checked Java exception that this function neither catches nor declares.
Safe fixes:
  - Wrap the call in try/catch, or add throws to the function signature and propagate.
Good:
  func load(path: String) -> String throws Error, IOException:
      return files.read_utf8(path)
Bad:
  func load(path: String) -> String:
      return files.read_utf8(path)
```

`sprig codes` lists every code with a one-line description, and `explain --json` gives the same content in structured form.

## 23.3 Forgotten the syntax? help

**Why.** The language manual lives in the compiler and is tested alongside it. With no arguments, `sprig help` lists every command and topic (`language`, `types`, `strings`, `functions`, `classes`, `variants`, `match`, `nullability`, `errors`, `collections`, `numerics`, `modules`, `jvm`, `conform`, `generics`, `concurrency`, `projects`, `dependencies`, `agents`, `api`, `upgrade`, `fmt`, `testing`, `build`, `wrap`, `lsp`).

Pick any topic:

```text
$ sprig help fmt
Sprig fmt (language 0.8-dev)
Syntax:
  sprig fmt file.spr
  sprig fmt .
  sprig fmt --check file.spr --json
Rules:
  - Canonical deterministic comment-preserving formatting
  - four spaces per block
  - no configuration
  - no aggressive wrapping
  - explicit command only
  - parse failures preserve original files
Example (Sprig source repository): docs/tooling/formatter.md
```

Every topic has syntax, rules and an example from the repository that really compiles. Add `--json` for the version programs read.

## 23.4 Not sure about a Java call? api

**Why.** A single `?` in a Java signature changes how you write the call. `sprig api Class --member method` shows what Sprig sees:

```text
$ sprig api java.time.LocalDate --member parse
Java API: java.time.LocalDate
constructors:
staticMethods:
  public static java.time.LocalDate java.time.LocalDate.parse(java.lang.CharSequence) => parse(CharSequence) -> LocalDate?
  public static java.time.LocalDate java.time.LocalDate.parse(java.lang.CharSequence,java.time.format.DateTimeFormatter) => parse(CharSequence, DateTimeFormatter) -> LocalDate?
instanceMethods:
fields:
```

The left of the arrow is the Java declaration, the right the Sprig signature; the `?` after `LocalDate` says the result needs a null check (chapter 21).

Sprig modules can be inspected too:

```text
$ sprig api @std/lists.spr --member sort_by
Sprig module: @std/lists.spr
path: .../std/lists.spr
declarations:
  function sort_by
    # Stable: items with equal keys keep their input order. Keys use the same
    # order as MutableList.sort(), so Float keys put -0.0 before 0.0 and NaN last.
    sort_by(items: List[T], key: fn(T) -> K throws Error): List[T]
```

`api` only reads signatures; it never runs a program.

## 23.5 Everything has JSON

**Why.** People read prose; editors, scripts and AI assistants read structure. For the error in 23.1, `sprig check --json` gives (in a scratch directory where the file is called `main.spr`):

```json
{
  "schemaVersion": 1,
  "toolVersion": "sprig-compiler 0.8.0-beta.1",
  "command": "check",
  "exitCode": 1,
  "environment": {"classpath": []},
  "diagnostics": [
    {
      "code": "SPR-NUM-MIXED",
      "phase": "TYPE",
      "severity": "error",
      "uri": "file:///private/tmp/sprig-probe/ch23/main.spr",
      "range": {"start": {"line": 0, "character": 6}, "end": {"line": 0, "character": 13}},
      "message": "Operator '+' has no implicit conversion between Int and Float",
      "expectedType": "matching numeric families",
      "actualType": "Int and Float",
      "hint": "Convert the Int side: 1.toFloatExact().",
      "relatedHelp": "numerics",
      "repair": {"kind":"make-numeric-conversion-explicit","machineApplicable":false},
      "related": [],
      "suggestedEdits": []
    }
  ]
}
```

Everything the text has, the JSON has more precisely: `range` is the editor's underline position (note that JSON `line` and `character` start at 0 while `sprig check` prints 1-based positions, so the same spot is `1:7` and `"line": 0, "character": 6`), `relatedHelp` names a topic you can read with `sprig help numerics`, and `repair.kind` is a machine-readable fix category.

The other everyday command is `sprig capabilities`, which states once and for all what the language has and lacks:

```text
$ sprig capabilities
Sprig compiler 0.8.0-beta.1 / language 0.8-dev
JDK minimum: 21
Commands: help, version, check, build, run, test, codes, explain, capabilities, api, wrap, doctor, init, resolve, add, remove, search, publish, project, deps, upgrade, fmt, lsp
Types: Int, Int32, BigInt, Float, Float32, Decimal, Bool, String, Unit
Implemented: typed functions, one-line class declarations, contract classes (methods without bodies) with conform, function references (named, module and method functions, and print, as values), source function types fn(A) -> R, function types that throw Error, rethrows functions, classes, enums, variants, match statements, match expressions, if expressions, explicit declaration reexports, nullable types, typed catch, local modules, lambdas up to three parameters, generic blocks with one or more type parameters, explicit Type[Arg] generic uses, type arguments inferred from call arguments, Equatable capability, Comparable capability, Sprig function values passed as Java functional interfaces with up to three parameters, Java varargs calls, declared foreign JVM conformance (conform Class to ImportedInterface), Java class conformance with a parent view, error classes (conform C to Error(message))
Unsupported: inference from the expected type, variance, inheritance, Java functional interfaces with more than three parameters, Java type-variable varargs (T...), varargs declarations in Sprig, annotations, decorators, macros, reflection-based schemas, block lambdas, tuples, destructuring, string interpolation, Char type, async/await, wildcard match, pipeline, operator overloading, arrays
Use 'sprig help <topic>' for syntax and rules.
```

`capabilities --json` also attaches alternatives to every missing feature. Asking about tuples (an excerpt of the real output):

```json
"tuples": {
  "supported": false,
  "alternatives": [
    "a one-line class with named fields: class Pair(first: Int, second: Int), built as Pair(first=1, second=2)",
    "variants with named fields"
  ],
  "helpTopic": "language"
}
```

Two more commands inspect the environment:

```text
$ sprig doctor
schemaVersion: 1
compilerVersion: 0.8.0-beta.1
languageVersion: 0.8-dev
jdkMinimum: 21
javaVersion: 26.0.1
javaVendor: Oracle Corporation
javacAvailable: true
platform: Mac OS X aarch64
cacheRoot: /Users/wu/.sprig
...
```

`doctor` tells you whether the JDK can compile, whether the `jlink` tool needed for bundling is present, and where the cache lives. Inside a project there is also `sprig project --json` and `sprig deps --json`:

```text
$ sprig project --json
{"schemaVersion":1,"command":"project","exitCode":0,"project":{"schemaVersion":1,"root":".../demo","name":"demo","version":"0.1.0","language":"0.8","source":"src","entry":"src/main.spr","binaries":[],"exports":[],"manifest":".../demo/sprig.toml","lockfile":".../demo/sprig.lock","lockStatus":"current","sprigDependencies":[],"jvmDependencies":[],"dependencyResolution":"not-needed","cacheRoot":"/Users/wu/.sprig","gitAvailable":true}}
```

## 23.6 Formatting: fmt

**Why.** With one format, a diff shows only real change. `sprig fmt` has exactly one canonical format and no configuration.

```sprig
func   add( a :Int,b:Int )->Int:
      return a+b
print(add(1,  2))
```

```text
$ sprig fmt --check messy.spr
Would format /private/tmp/sprig-probe/fmt/messy.spr
$ sprig fmt messy.spr
Formatted /private/tmp/sprig-probe/fmt/messy.spr
```

Result:

```sprig
func add(a: Int, b: Int) -> Int:
    return a + b
print(add(1, 2))
```

The only "formatting" it never rewrites is the text inside comments. `--check` reports without modifying, `--json` lists the files that would change (`changedFiles`), and a whole directory works too: `sprig fmt .`.

## 23.7 Editors: sprig lsp

Diagnostics, hover, go-to-definition, completion and rename in an editor all come from the same compiler, served by `sprig lsp`. The rules in `sprig help lsp` are its feature list:

```text
$ sprig help lsp
Sprig lsp (language 0.8-dev)
Syntax:
  sprig lsp
  sprig lsp --stdio
  sprig lsp --classpath build/classes/java/main
Rules:
  - Language Server Protocol over standard input and output, for any LSP client
  - diagnostics, hover, go to definition, references, document symbols, completion, formatting, rename and quick fixes come from the same parser, resolver and checker as sprig check
  - a file inside a sprig.toml project uses that project's locked dependencies and classpath, and nothing is resolved or downloaded
  - unsaved buffers of open files are read instead of the files on disk
  - when the current text does not parse or resolve, requests return nothing rather than a guess, and the outline keeps the last version that parsed
  - rename covers local variables and parameters only and is checked by compiling the renamed text
  - a quick fix is a code action that applies one of the suggestedEdits of a diagnostic under the cursor, computed from the current text, never from diagnostics the client sends
  - references search the open files and the files of the same project that mention the name
  - positions use UTF-16 code units, as LSP requires
Example (Sprig source repository): docs/tooling/lsp.md
```

VS Code users get all of it from the repository's Sprig extension. Because the command line and the editor share one compiler, an editor error and `sprig check` always agree.

## 23.8 Shipping: build --bundle

**Why.** `sprig run` compiles on every start and expects Java on the machine. To hand a program to someone else, `sprig build --bundle` puts the program, the Sprig runtime, the locked dependencies and a trimmed Java runtime (built by `jlink`) into one directory.

Write a `hello.spr`, then:

```text
$ sprig build hello.spr --bundle --archive
Built hello.spr -> /private/tmp/sprig-probe/bundle/sprig-build
  Java sources: /private/tmp/sprig-probe/bundle/sprig-build/java
  Classes:      /private/tmp/sprig-probe/bundle/sprig-build/classes
  Main class:   sprig.user.$M_hello
  Bundle:       /private/tmp/sprig-probe/bundle/sprig-build/hello
    run it with /private/tmp/sprig-probe/bundle/sprig-build/hello/bin/hello (or hello.cmd on Windows)
    lib/ 87 KB; runtime/ 63 MB, modules java.base, java.net.http, java.sql, jdk.httpserver
    the bundle runs only on Mac OS X aarch64, the platform that built it
  Archive:      /private/tmp/sprig-probe/bundle/sprig-build/hello.zip
```

The output directory looks like this:

```text
sprig-build/hello/
  README.txt
  bin/hello           the Linux and macOS launcher
  bin/hello.cmd       the Windows launcher
  lib/hello.jar       the program's own classes
  lib/sprig-runtime.jar
  runtime/            the Java runtime built by jlink
```

A launcher runs the program from **your current directory**, forwards arguments and the exit status:

```text
$ ./sprig-build/hello/bin/hello
hello from a bundle
```

A few limits to know:

- The program is checked and compiled first; on an error nothing is written and the bundling process never runs it.
- A runtime image runs **only** on the OS and CPU architecture that built it (a bundle built on Linux x86-64 will not run on macOS or ARM Linux); build once per platform.
- Bundling needs a full JDK (with `jdeps`, `jlink` and `jmods/`), and `sprig doctor` tells you in advance if one is missing; without it you get `SPR-BUNDLE-TOOLS` with the fix.
- `--archive` also writes a zip for distribution (29 MB in the example above); without it you get the directory alone.
- Cross-platform bundling, GraalVM native images and installers are out of scope.

To look at the generated Java only, use `sprig build app.spr --emit-java-only`: it runs the static pipeline and writes Java without calling `javac`.

## 23.9 Writing with an AI assistant

Sprig was designed with AI coding assistants as one of its main users: errors have stable codes and exact positions, and every command speaks JSON. The compiler's `agents` topic is written for them:

```text
$ sprig help agents
Sprig agents (language 0.8-dev)
Syntax:
  sprig build file.spr --emit-java-only -d generated --json
  sprig capabilities --json
  sprig help match --json
  sprig api java.time.LocalDate --member of --json
  sprig api src/main.spr --json
  sprig api @pkg/module.spr --member Type.member --json
  sprig api . --json
  sprig check file.spr --json
  sprig explain SPR-CODE --json
  sprig upgrade --check
Rules:
  - Query current capabilities before generating syntax
  - use diagnostics to repair code
  - do not guess unsupported features
  - api inspects Java classes and checked Sprig modules/projects with resolved signatures and never executes application code
Example (Sprig source repository): docs/tooling/agent-guide.md
```

A few practical lessons:

- **Make the assistant ask the compiler first.** Run `sprig capabilities --json` and the relevant `sprig help topic --json` before writing; it will not smuggle in another language's syntax (an `async` or a tuple, say).
- **Repair with `check --json` output.** The code, `range` and `hint` are enough to locate and fix a problem; for a code you do not recognize, `explain --json`.
- **Write tests, compile-fail tests included.** Chapter 19 showed how "the compiler should reject this" becomes an assertion too; every deliberate mistake in this book is verified that way.
- **Know the upgrade command.** `sprig upgrade --check` asks whether a newer version exists; for a source checkout like this book's, it says plainly that it is not a managed install:

```text
$ sprig upgrade --check
sprig upgrade: this is a source checkout; update it with Git and rebuild with scripts/build.sh
```

## Summary

- The loop is `check` (lists all errors) → read → edit; `run` is only for seeing results.
- An error is: code `[phase] file:line:column` message `(expected, actual)` plus a `hint`.
- `explain` for codes, `help` for the language, `api` for signatures, `capabilities` for what exists and what does not.
- Almost every command takes `--json`; editors, scripts and AI assistants read the structured form.
- `fmt` unifies formatting (`--check` reports only); `sprig lsp` gives editors the same diagnostics as the command line.
- `build --bundle` produces a directory with its own Java runtime, one platform at a time; `--emit-java-only` writes Java alone.

## Exercises

**Exercise 1 (easy).** Use `sprig explain` on `SPR-TYPE-CAPTURE` from chapter 22 and write down the fix it suggests. Hint: the command is `sprig explain SPR-TYPE-CAPTURE`.

::: details Answer
```text
$ sprig explain SPR-TYPE-CAPTURE
SPR-TYPE-CAPTURE: A lambda captures a var local; copy it into a let binding first.
Why it matters: Lambdas capture immutable bindings, avoiding shared mutable state surprises.
Common causes:
  - A lambda body reads a var local.
Safe fixes:
  - Copy the var into a let binding before the lambda.
```

The fix is the last line: copy the `var` into a `let` first and let the lambda capture that.
:::

**Exercise 2 (easy).** Use `sprig api` to find what `java.lang.String.strip()` returns in Sprig. Does it carry a question mark? What does that mean?

::: details Answer
```text
$ sprig api java.lang.String --member strip
Java API: java.lang.String
constructors:
staticMethods:
instanceMethods:
  public java.lang.String java.lang.String.strip() => strip() -> String?
fields:
```

It returns `String?`. The question mark means a Java method's reference result is treated as possibly `null`, so check it before use (chapter 21).
:::

**Exercise 3 (medium).** Use `sprig capabilities --json` to answer: does this compiler support tuples? If not, what does it suggest instead? Hint: look at `unsupportedSyntax` and `featureGuidance`.

::: details Answer
`tuples` appears in `unsupportedSyntax`, so they are not supported. The two alternatives in `featureGuidance` are:

```json
"tuples": {
  "supported": false,
  "alternatives": [
    "a one-line class with named fields: class Pair(first: Int, second: Int), built as Pair(first=1, second=2)",
    "variants with named fields"
  ],
  "helpTopic": "language"
}
```

Use a one-line class or a variant with fields instead (chapters 11 and 12).
:::

**Exercise 4 (harder).** Bundle a program that only prints `"hi"` and run it without going through `sprig run`. Hint: `sprig build hi.spr --bundle`, then the `bin` launcher in the output directory.

::: details Answer
```text
$ sprig build hi.spr --bundle
...
  Bundle:       .../sprig-build/hi
    run it with .../sprig-build/hi/bin/hi (or hi.cmd on Windows)
    ...
$ ./sprig-build/hi/bin/hi
hi
```

The launcher runs the program from the current directory and needs no Java on `PATH`. It only runs on the platform that built it; build again per platform.
:::

Next chapter: [Project: an expense tracker](/en/tutorial/ch24-project-ledger) — putting the whole book together into one complete program.
