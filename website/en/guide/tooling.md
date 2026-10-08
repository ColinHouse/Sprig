# Tools and JSON

Sprig comes as one command-line program, `sprig`, and everything it does is a subcommand. There's no background process: even the language server is a subcommand, `sprig lsp`, which your editor starts itself. For VS Code, see the [VS Code extension](/en/guide/editor).

This page follows the `main` branch. `sprig build --bundle`, `sprig search`, `sprig publish` and `run --no-cache` are newer than the published v0.7.1-beta.1; every other command is in it. For exactly what your installed SDK supports, run `sprig capabilities --json`.

## The commands

The ones you'll use most while writing code:

| Command | What it does |
|---|---|
| `sprig check [file]` | Checks without running and lists every error at once |
| `sprig run [file] [-- args]` | Checks, compiles and runs; anything after `--` goes to your program |
| `sprig test [path]` | Runs the project's tests; `--filter` picks tests by name |
| `sprig build [file]` | Generates Java source and class files |
| `sprig build --bundle [--archive]` | Writes a directory with its own Java runtime that runs on a machine without a JDK; see below |
| `sprig fmt <file or directory>` | Formats code; with `--check` it only checks and changes nothing |

Projects and dependencies:

| Command | What it does |
|---|---|
| `sprig init [dir]` | Creates a new project |
| `sprig resolve` | Resolves dependencies and writes `sprig.lock` |
| `sprig add`, `sprig remove` | Adds or removes a dependency; see [projects](/en/guide/projects) |
| `sprig project` | Shows project information |
| `sprig deps` | Lists declared dependencies |
| `sprig search [text]` | Lists the packages a registry offers |
| `sprig publish --registry DIR ...` | Writes a release entry into a local registry directory, see [Projects](/en/guide/projects) |

Looking things up:

| Command | What it does |
|---|---|
| `sprig explain <code>` | Explains an error code: why it happens, how to fix it, good and bad examples |
| `sprig codes` | Lists every error code |
| `sprig help [topic]` | Quick syntax reference; without a topic, it lists the topics |
| `sprig api <Java class>` | Shows a Java class's signatures as Sprig sees them |
| `sprig api <module.spr>` | Shows the declarations a Sprig module offers, with the comment written above each one |
| `sprig capabilities` | Shows which features this compiler implements |
| `sprig doctor` | Checks your environment, such as the JDK and the compiler |

Everything else:

| Command | What it does |
|---|---|
| `sprig wrap <Java class> --out <file>` | Generates Sprig wrapper code for a Java class; see [JVM interop](/en/guide/jvm-interop) |
| `sprig upgrade` | Upgrades the SDK; with `--check` it only looks for a newer version |
| `sprig lsp` | Runs the language server for your editor; see [language server](#language-server) |
| `sprig version` | Prints the version |

Inside a project, `check`, `run` and `build` don't need a file name; they use the project's entry point. `check`, `build`, `run`, `api`, `wrap` and `doctor` accept `--classpath` to add local JARs or directories, as many times as you need. The option only uses the paths you give it; it never downloads anything.

## Useful options

- **`check --syntax-only`**: `check` never generates code anyway; this option makes it faster still by checking only tokens, indentation and syntax.
- **`run --keep`**: keeps the generated Java files so you can look at them.
- **`run --no-cache`**: compiles with javac even when the same program ran before. By default `run` and `test` keep the compiled classes of each program under `~/.sprig/cache/javac` (the newest 64), keyed by the generated Java, the compiler and Java versions, the runtime and the classpath, so running an unchanged program again skips javac. When javac does run, it compiles only your program: the SDK ships the runtime already compiled, and its classes are copied next to yours. With a JDK other than the one that built the SDK, the first run compiles the runtime once into `~/.sprig/cache/runtime`. `SPRIG_JAVAC_CACHE=off` turns the program cache off; a directory path moves it.
- **`run --stacktrace`**: when your program fails with an uncaught error, Sprig reports `SPR-RUNTIME-ERROR` or `SPR-RUNTIME-EXCEPTION` and points at the line in your source; when the failure happens inside an `@std` function, that's your line that called it. Add this option when you also want the full JVM stack trace.
- **`build -d <dir>`**: `build` writes to `sprig-build/` by default, and `-d` picks another directory. If the check fails, no class files are written.
- **`build --emit-java-only`**: runs the static checks and generates Java without calling javac. With `--json`, the result includes `javaSources`, `mainClass` and `javacInvoked: false`.
- **`build --bundle`**: also writes `<name>/` inside the build output (the name is the `--bin`, the project name or the file name) that you can hand to someone with no Java installed: `bin/<name>` is a POSIX sh launcher and `bin/<name>.cmd` the Windows one; `lib/` holds the program's JAR, the Sprig runtime and every JAR from the lock file (Maven dependencies included, named by coordinate); `runtime/` is a Java runtime image that jlink builds from the modules those JARs actually use, with the JDK's license notices kept in `runtime/legal/` (OpenJDK's GPLv2 with the Classpath Exception allows redistributing it together with the notices). The launcher runs the program in your current directory, forwards arguments and the exit status unchanged, and keeps a class-data-sharing archive of the program's classes under your cache directory so the second start is faster. Both launchers write standard output and error as UTF-8; if an old Windows console shows the text garbled, run `chcp 65001` first. A program that starts `bin\<name>.cmd` passes the launcher path and the arguments directly; wrapping them in `cmd /c` with every argument quoted lets cmd.exe strip the outer quotes by its own rules. Your program is never run while bundling. **The image runs only on the operating system and CPU architecture that built it**; the command output says which, so build on each platform you ship to. `--archive` also writes `<name>.zip` next to the bundle, and the launchers stay executable after unzipping. You need a full JDK: `jdeps`, `jlink`, and either a `jmods/` directory or a JDK 24+ runtime that jlink can link from (Temurin's JDK 26 has no `jmods/` and works). A missing tool is `SPR-BUNDLE-TOOLS`, a `jdeps` failure `SPR-BUNDLE-JDEPS`, and an installation jlink can't image or a `jlink` failure `SPR-BUNDLE-LAYOUT`, each with a fix. Details: [bundles](/en/reference/projects/bundle).

If a program defines `func main` but writes no output and no top-level statement or nested block calls it, `run` prints a note. Calls inside nested `if`, `try` and similar blocks count.

`sprig explain` tells you what any error code means, and the full list is in [diagnostic codes](/en/reference/tooling/diagnostic-codes).

## JSON output

Almost every command accepts `--json`. With it, standard output holds exactly one JSON document, even when your program fails. Whatever the program printed goes in `programOutput` and errors go in `diagnostics`, so the two never mix. Since v0.6.0-beta.1, what the program wrote to standard error goes in `programErrorOutput`, and a program run with `--json` gets an empty standard input.

A small greeting program that runs successfully:

```json
{
  "schemaVersion": 1,
  "toolVersion": "sprig-compiler 0.7.1-beta.1",
  "command": "run",
  "exitCode": 0,
  "programOutput": "Hello, Ada!\n",
  "environment": {"classpath": []},
  "diagnostics": []
}
```

Now a failed check. This code forgets `Square`:

<<< @/snippets/guide/tooling_missing_case.spr

The normal output looks like this:

```text
SPR-MATCH-NONEXHAUSTIVE [FLOW] main.spr:6:12: Missing case: Shape.Square
  hint: Add 'case Shape.Square:' (there is no default case)
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

And with `--json` (the real `uri` is a full `file:` path, shortened here):

```json
{
  "schemaVersion": 1,
  "toolVersion": "sprig-compiler 0.7.1-beta.1",
  "command": "check",
  "exitCode": 1,
  "environment": {"classpath": []},
  "diagnostics": [
    {
      "code": "SPR-MATCH-NONEXHAUSTIVE",
      "phase": "FLOW",
      "severity": "error",
      "uri": "file:///.../main.spr",
      "range": {
        "start": {"line": 5, "character": 11},
        "end": {"line": 7, "character": 51}
      },
      "message": "Missing case: Shape.Square",
      "hint": "Add 'case Shape.Square:' (there is no default case)",
      "relatedHelp": "match",
      "repair": {
        "kind": "add-explicit-case-for-every-missing-case",
        "machineApplicable": false
      },
      "related": [],
      "suggestedEdits": []
    }
  ]
}
```

Lines and columns in JSON count from 0, so `"line": 5, "character": 11` is line 6, column 12 in the normal output. `relatedHelp` tells you which `sprig help` topic to read. When the hint comes down to one mechanical rewrite, such as a missing `import "@std/files.spr" as files` line or `throws Error` on a function header, `suggestedEdits` holds it as a range plus replacement text, ready to apply. In your editor, the [language server](#language-server) offers the same rewrite as a quick fix.

Exit codes work like this:

- A mistake in the command-line arguments, or a failure in the tool itself, returns `2`.
- Errors in your source and runtime errors usually return `1`.
- `run` passes your program's own exit status through, so a program that exits on purpose can also return `2` or any other value. Since v0.6.0-beta.1, a program chooses its status with `process.exit` from `@std/process`, which also has `print_error` for standard error and `read_line`, `read_lines` and `read_all` for standard input. Text mode forwards the status without adding a compiler-error message; JSON mode reports a nonzero exit as `SPR-PROGRAM-EXIT` and puts the program's status in `data.programExitCode`.

## For AI assistants

- Run `sprig check --json` before running anything. It only parses and checks names and types, without generating or executing code, so it's the fastest reliable check you have.
- Every error comes with a stable code and the phase it belongs to: `CLI` for command, project and dependency problems, otherwise `LEX`, `SYNTAX`, `NAME`, `TYPE`, `FLOW`, `JVM` or `RUNTIME`. An error in your source has an exact location, and many also include a `hint` that says what to change next.
- `run --json` keeps program output and errors apart, so even a failing program gives you a result you can parse.

The full approach is in [working with AI assistants](/en/guide/agent-workflow).

## Formatting

`sprig fmt file.spr` formats a file in place; `sprig fmt --check . --json` only checks and changes nothing, which suits CI. Formatting keeps your comments, always gives the same result, has no options and doesn't wrap lines aggressively. No command other than `fmt` ever changes your source. See [formatter](/en/reference/tooling/formatter).

## Language server

`sprig lsp` speaks the Language Server Protocol over standard input and output, so editors such as Neovim and Helix can use it directly, and the [VS Code extension](/en/guide/editor) starts it for you. It's new in v0.6.0-beta.1.

It gives you errors as you type, hover, go to definition, references, an outline, completion, formatting, rename for local variables and parameters, and quick fixes: when an error's fix is one mechanical rewrite, your editor applies it in one click. All of it comes from the same compiler as `sprig check`, so your editor and the command line never disagree. While your code doesn't parse, the server answers with nothing rather than a guess. Quick fixes are newer than v0.7.1-beta.1 and come with the next release.

Editor setup and the details of each feature are in the [language server reference](/en/reference/tooling/lsp).

## Not there yet

These are planned but not implemented:

- a hosted central package registry with accounts and upload: today's registries are Git or local directory indexes, and publishing is a pull request to `registry/`, see [Projects](/en/guide/projects)
- incremental checking

Early design proposals are in the [Agent tool protocol](https://github.com/ColinHouse/Sprig/blob/main/docs/history/design-kit/AGENT_TOOL_PROTOCOL.md). It's a historical proposal, not a description of what exists. For what `sprig api` can and can't tell you, see the [JVM interop reference](/en/reference/jvm/interop).
