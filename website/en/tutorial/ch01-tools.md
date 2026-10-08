# 1. Getting set up

In this chapter you will:

- see what a terminal is and how to open one;
- install JDK 21 or newer;
- get the one Sprig compiler this book needs, and check that you have it;
- write Sprig in VS Code;
- use `sprig doctor` to check your setup.

Don't skip this chapter. Every later chapter assumes three things work: a terminal, a JDK, and the right `sprig` command.

## 1.1 The terminal

A program is a list of instructions for a computer. The Sprig compiler translates a Sprig program into Java, and the JVM runs the Java, so your computer needs a working Java setup. You type instructions into a **terminal**.

- macOS: press `Command + Space`, type `Terminal`, press Enter.
- Windows: open the Start menu, type `PowerShell`, press Enter.
- Linux: usually `Ctrl + Alt + T`, or search your applications for `Terminal`.

In the window that opens, the `$` (or `>` on Windows) is the prompt: type a command after it and press Enter. All of this book's command lines look like this:

```bash
java -version
```

The command is what you type; the text below it is what appears on screen. Don't type the `$` itself.

Three navigation commands, just to get around:

| Command | What it does |
|---|---|
| `cd DIR` | change into a directory |
| `pwd` | print the current directory (on Windows use `cd` with no arguments) |
| `ls` | list the files here (on Windows use `dir`) |

## 1.2 Install JDK 21 or newer

Sprig generates Java, so you need **JDK 21 or newer**. JDK means Java Development Kit. A JRE (Java Runtime Environment) can run Java but has no `javac` compiler, and Sprig needs one.

Once installed, check both in a terminal:

```bash
java -version
javac -version
```

On the machine this book was written on, that prints (your version numbers will differ):

```text
openjdk version "26.0.1" 2026-04-21
OpenJDK Runtime Environment (build 26.0.1+8-34)
OpenJDK 64-Bit Server VM (build 26.0.1+8-34, mixed mode, sharing)
javac 26.0.1
```

Both commands produce output, and the major version number is 21 or higher. That's all you need: the `"26.0.1"` in the first line, or the number after `javac`, must be ≥ 21.

If you don't have a JDK yet, download Temurin 21 from [Adoptium](https://adoptium.net/) (pick your operating system; on Windows grab the `.msi` or `.zip`). A system package manager, such as Homebrew on macOS, works too. **Open a new terminal** afterwards and run the checks again.

Common problems:

- `java -version` works but `javac -version` says "command not found": you installed a JRE, not a JDK.
- Neither is found: the JDK isn't installed properly, or its `bin` directory isn't on `PATH`. Try a fresh terminal first; otherwise reinstall, since installers normally set `PATH` for you.

## 1.3 Install Sprig, and check the version

This part matters most: **this book follows the 0.8 language on the `main` branch**. The official release and the install script currently give you `v0.7.1-beta.1`, which does not have some of the syntax the book uses, so later programs won't run on it. Until 0.8 is released, build from source.

### Build from source (Linux, macOS, Windows)

You need Git, JDK 21+, and Python 3.12+. In a terminal:

```bash
git clone https://github.com/ColinHouse/Sprig.git
cd Sprig
python3 scripts/build.py
```

On Windows, use this for the last line:

```powershell
py -3 scripts/build.py
```

The first build downloads pinned build tools, so it needs a network connection and a few minutes. When it finishes, `bin/sprig` (on Windows, `bin\sprig.cmd`) in that checkout is the compiler.

### Make `sprig` typeable (optional)

The rest of the book writes commands as `sprig`. To avoid typing the full path, add the checkout's `bin` directory to `PATH`. The default macOS terminal is zsh:

```bash
echo 'export PATH="/replace/this/with/the/checkout/Sprig/bin:$PATH"' >> ~/.zshrc
```

Then open a new terminal. For bash use `~/.bashrc`; Linux is the same idea. On Windows, search Settings for "environment variables" and add `...\Sprig\bin` to Path.

You can also skip this: every `sprig ...` in the book can be `/path/to/Sprig/bin/sprig ...`, or `...\Sprig\bin\sprig.cmd ...` on Windows.

### Checking the version: `sprig version` is not enough

Look at the version command first:

```bash
sprig version
```

```text
sprig-compiler 0.7.1-beta.1
```

**This does not tell the new compiler from the old one.** The published SDK also calls itself `0.7.1-beta.1`, and so does the 0.8 compiler built from `main`. The first line of `sprig capabilities` is no help either: both say "language 0.8-dev". Don't trust version strings; trust the feature list:

```bash
sprig capabilities
```

```text
Sprig compiler 0.7.1-beta.1 / language 0.8-dev
JDK minimum: 21
Commands: help, version, check, build, run, test, codes, explain, capabilities, api, wrap, doctor, init, resolve, add, remove, search, publish, project, deps, upgrade, fmt, lsp
Types: Int, Int32, BigInt, Float, Float32, Decimal, Bool, String, Unit
Implemented: typed functions, one-line class declarations, contract classes (methods without bodies) with conform, function references (named, module and method functions, and print, as values), source function types fn(A) -> R, function types that throw Error, rethrows functions, classes, enums, variants, match statements, match expressions, if expressions, explicit declaration reexports, nullable types, typed catch, local modules, lambdas up to three parameters, generic blocks with one or more type parameters, explicit Type[Arg] generic uses, type arguments inferred from call arguments, Equatable capability, Comparable capability, Sprig function values passed as Java functional interfaces with up to three parameters, Java varargs calls, declared foreign JVM conformance (conform Class to ImportedInterface), Java class conformance with a parent view, error classes (conform C to Error(message))
Unsupported: inference from the expected type, variance, inheritance, Java functional interfaces with more than three parameters, Java type-variable varargs (T...), varargs declarations in Sprig, annotations, decorators, macros, reflection-based schemas, block lambdas, tuples, destructuring, string interpolation, Char type, async/await, wildcard match, pipeline, operator overloading, arrays
Use 'sprig help <topic>' for syntax and rules.
```

The long `Implemented:` line must contain **`if expressions`**. The old SDK's list doesn't have it. To check just that one item:

```bash
sprig capabilities | grep -o "if expressions"
```

```text
if expressions
```

On Windows PowerShell:

```powershell
sprig capabilities | Select-String "if expressions"
```

If you see `if expressions`, you have the right compiler. If you don't, you have the published SDK; build from source as above.

One more confirmation: `sprig doctor`, which you'll run below, prints `jdkMinimum: 21` on the right compiler. On the old SDK it is `jdkMinimum: 17`.

::: tip Coming from another language?
Static typing and compilers may be familiar already. The one counterintuitive thing here: **version strings lie, feature lists don't**. This is how the Sprig toolchain works everywhere. `sprig capabilities --json` and `sprig doctor --json` report machine-readable facts, and the editor extension and AI assistants read them instead of guessing from a version number.
:::

## 1.4 Editor: VS Code

You can write Sprig in any text editor, but a good one saves you work. Use [VS Code](https://code.visualstudio.com/):

1. Install VS Code.
2. Install the Sprig extension. It isn't on the Marketplace yet, so package it from source; see the [extension page](/en/guide/editor). Syntax highlighting, check-on-save and one-click run all use the compiler you just built. If the extension can't find it, set **Sprig: Compiler Path** to `bin/sprig`.
3. Save your files with the `.spr` extension, for example `hello.spr`.

### Deliberate mistake: indenting with a tab

One rule you must know: **Sprig indents with spaces, never tabs**. A file indented with tabs fails even the check, and the compiler says:

```text
SPR-LEX-TAB [LEX] main.spr:2:1: Tabs are not allowed for indentation or inline whitespace
  hint: Sprig code blocks use spaces only; replace the tab with spaces.
```

`[LEX]` means the error happened while reading characters, `2:1` points at the tab that starts the second line, and the `hint` says what to do. In VS Code, look at the status bar in the bottom right: `Spaces: 4` is what you want (the number can be anything, as long as you're consistent in one file). If it says `Tab Size`, click it and switch to spaces. Chapter 5 explains indentation and code blocks; for now, just remember "spaces, not tabs".

## 1.5 Check the environment: `sprig doctor`

Finally, run the check-up command:

```bash
sprig doctor
```

The first few lines on this machine are:

```text
schemaVersion: 1
compilerVersion: 0.7.1-beta.1
languageVersion: 0.8-dev
jdkMinimum: 21
javaVersion: 26.0.1
```

Many lines follow, listing `javaHome`, class paths, cache locations and more. The ones that matter:

- `jdkMinimum: 21`: the compiler this book needs; if it says 17, see 1.3.
- `javaVersion`: your Java version, which must be ≥ 21.
- `javacAvailable: true`: the Java compiler was found; `false` means the JDK isn't set up right.

## Summary

- The terminal is where you type commands; `cd`, `pwd` and `ls` get you around.
- You need JDK 21+; both `java -version` and `javac -version` must produce output.
- This book needs the 0.8 compiler from `main`. The official `v0.7.1-beta.1` SDK is not enough; until 0.8 is released, build from source with `python3 scripts/build.py` and use `bin/sprig`.
- `sprig version` and the first line of `capabilities` cannot tell the versions apart; look for `if expressions` in the `Implemented:` line of `sprig capabilities`.
- `sprig doctor` is the check-up; `jdkMinimum` should be 21.
- Indent with spaces, never tabs.

## Exercises

1. Open a terminal and run `java -version` and `javac -version`; read out both version numbers.
   Hint: both commands must print something, and both major versions must be ≥ 21.

::: details Answer
On the machine this book was written on:

```text
openjdk version "26.0.1" 2026-04-21
...
javac 26.0.1
```

Your numbers will differ (21, 22, 23, ... are all fine), as long as both major versions are ≥ 21. If `javac` isn't found, go back to 1.2.
:::

2. Run `sprig capabilities | grep -o "if expressions"` (on Windows use `Select-String`) and confirm you see `if expressions`.
   Hint: `grep -o` keeps only the matching text; on Windows use `sprig capabilities | Select-String "if expressions"`.

::: details Answer
The right compiler prints exactly:

```text
if expressions
```

No output means you have the old release; build from source as in 1.3.
:::

3. Run `sprig doctor`, find the `jdkMinimum` and `javacAvailable` lines, and say what they should be.
   Hint: the output has several lines; look for these two names near the top.

::: details Answer
On the machine this book was written on:

```text
jdkMinimum: 21
...
javacAvailable: true
```

`jdkMinimum` should be 21 (the old release says 17), and `javacAvailable` must be `true`.
:::

Ready? Chapter 2 writes your first real program: [Chapter 2: Your first program, and reading errors](/en/tutorial/ch02-first-program).
