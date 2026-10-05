# Tools and JSON

Sprig comes as one command-line program, `sprig`, and everything it does is a subcommand. There's no background process: even the language server is a subcommand, `sprig lsp`, which your editor starts itself. For VS Code, see the [VS Code extension](/en/guide/editor).

Every command on this page except `sprig lsp` is in the published v0.5.0-beta.1. For exactly what your installed SDK supports, run `sprig capabilities --json`.

## The commands

The ones you'll use most while writing code:

| Command | What it does |
|---|---|
| `sprig check [file]` | Checks without running and lists every error at once |
| `sprig run [file] [-- args]` | Checks, compiles and runs; anything after `--` goes to your program |
| `sprig test [path]` | Runs the project's tests; `--filter` picks tests by name |
| `sprig build [file]` | Generates Java source and class files |
| `sprig fmt <file or directory>` | Formats code; with `--check` it only checks and changes nothing |

Projects and dependencies:

| Command | What it does |
|---|---|
| `sprig init [dir]` | Creates a new project |
| `sprig resolve` | Resolves dependencies and writes `sprig.lock` |
| `sprig add`, `sprig remove` | Adds or removes a dependency; see [projects](/en/guide/projects) |
| `sprig project` | Shows project information |
| `sprig deps` | Lists declared dependencies |

Looking things up:

| Command | What it does |
|---|---|
| `sprig explain <code>` | Explains an error code: why it happens, how to fix it, good and bad examples |
| `sprig codes` | Lists every error code |
| `sprig help [topic]` | Quick syntax reference; without a topic, it lists the topics |
| `sprig api <Java class>` | Shows a Java class's signatures as Sprig sees them |
| `sprig api <module.spr>` | Shows the declarations a Sprig module offers |
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
- **`run --stacktrace`**: when your program fails with an uncaught error, Sprig reports `SPR-RUNTIME-ERROR` or `SPR-RUNTIME-EXCEPTION` and points at the line in your source. Add this option when you also want the full JVM stack trace.
- **`build -d <dir>`**: `build` writes to `sprig-build/` by default, and `-d` picks another directory. If the check fails, no class files are written.
- **`build --emit-java-only`**: runs the static checks and generates Java without calling javac. With `--json`, the result includes `javaSources`, `mainClass` and `javacInvoked: false`.

`sprig explain` tells you what any error code means, and the full list is in [diagnostic codes](/en/reference/tooling/diagnostic-codes).

## JSON output

Almost every command accepts `--json`. With it, standard output holds exactly one JSON document, even when your program fails. Whatever the program printed goes in `programOutput` and errors go in `diagnostics`, so the two never mix.

A small greeting program that runs successfully:

```json
{
  "schemaVersion": 1,
  "toolVersion": "sprig-compiler 0.5.0-beta.1",
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
  "toolVersion": "sprig-compiler 0.5.0-beta.1",
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

Lines and columns in JSON count from 0, so `"line": 5, "character": 11` is line 6, column 12 in the normal output. `relatedHelp` tells you which `sprig help` topic to read.

Exit codes work like this:

- A mistake in the command-line arguments, or a failure in the tool itself, returns `2`.
- Errors in your source and runtime errors usually return `1`.
- `run` passes your program's own exit status through, so a program that exits on purpose can also return `2` or any other value. When a program exits with a nonzero status without a JVM exception, Sprig reports `SPR-PROGRAM-EXIT` and puts the program's status in the JSON field `data.programExitCode`.

## For AI assistants

- Run `sprig check --json` before running anything. It only parses and checks names and types, without generating or executing code, so it's the fastest reliable check you have.
- Every error comes with a stable code, the phase it belongs to (`LEX`, `SYNTAX`, `NAME`, `TYPE`, `FLOW`, `JVM` or `RUNTIME`) and an exact location. Many also include a `hint` that says what to change next.
- `run --json` keeps program output and errors apart, so even a failing program gives you a result you can parse.

The full approach is in [working with AI assistants](/en/guide/agent-workflow).

## Formatting

`sprig fmt file.spr` formats a file in place; `sprig fmt --check . --json` only checks and changes nothing, which suits CI. Formatting keeps your comments, always gives the same result, has no options and doesn't wrap lines aggressively. No command other than `fmt` ever changes your source. See [formatter](/en/reference/tooling/formatter).

## Language server

`sprig lsp` speaks the Language Server Protocol over standard input and output, so editors such as Neovim and Helix can use it directly. It's new in the development version, so the published v0.5.0-beta.1 doesn't have it yet.

It gives you errors as you type, hover, go to definition, references, an outline, completion, formatting, and rename for local variables and parameters. All of it comes from the same compiler as `sprig check`, so your editor and the command line never disagree. While your code doesn't parse, the server answers with nothing rather than a guess.

Editor setup and the details of each feature are in the [language server reference](/en/reference/tooling/lsp).

## Not there yet

These are planned but not implemented:

- publishing packages, and a module registry
- incremental checking

Early design proposals are in the [Agent tool protocol](https://github.com/ColinHouse/Sprig/blob/main/docs/history/design-kit/AGENT_TOOL_PROTOCOL.md). It's a historical proposal, not a description of what exists. For what `sprig api` can and can't tell you, see the [JVM interop reference](/en/reference/jvm/interop).
