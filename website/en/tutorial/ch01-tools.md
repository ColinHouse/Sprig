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

The syntax and standard modules used by this book are included in the published [v0.8.0-beta.1 SDK](https://github.com/ColinHouse/Sprig/releases/tag/v0.8.0-beta.1). Install the SDK to begin; you do not need to build the compiler first.

### Linux / macOS: install the SDK

After installing JDK 21+, run:

```bash
curl -fsSL https://raw.githubusercontent.com/ColinHouse/Sprig/main/scripts/install-sprig.sh | sh
export PATH="$HOME/.local/bin:$PATH"
sprig version
```

The installer selects the newest published release, including Betas, checks the ZIP's SHA-256, installs the SDK under `~/.sprig/versions/` and places its launcher at `~/.local/bin/sprig`. It does not edit your shell configuration. The `export` above affects this terminal only; add it to `~/.zshrc` for zsh or `~/.bashrc` for bash if new terminals should find `sprig` too.

You can also download, verify and unpack the ZIP manually; see [installation](/en/guide/getting-started). Using the SDK does not require Python or Git. The first Maven dependency resolution may still need a network connection.

### Windows: unpack the SDK

Windows is an experimental preview and has no managed installer. Download the release ZIP and its matching `.sha256` file, verify the checksum, then unpack and use `bin\sprig.cmd`. Add the extracted SDK's `bin` directory to `Path` to type `sprig ...` directly. See [installing on Windows](/en/reference/projects/install#windows) for the full steps.

### Check the version

```bash
sprig version
```

The current SDK prints:

```text
sprig-compiler 0.8.0-beta.1
```

This is the **SDK/compiler release version**. If it shows an older version, run `sprig upgrade` for a managed installation, or download a new ZIP for a manual installation. If the old version persists, check that `PATH` and **Sprig: Compiler Path** do not point to another compiler.

Then run:

```bash
sprig capabilities
```

It lists the compiler version, `language 0.8-dev`, the minimum JDK, commands, types and implemented features. `0.8-dev` is the **language version**: the language is not frozen yet. It does not mean you lack a published SDK. Use `sprig capabilities --json` for a complete, machine-readable list for editors or coding agents.

::: tip What are the two versions for?
`sprig version` identifies the SDK you installed; `sprig capabilities` describes its features. This site follows `main` and may gain new features later. Include both version and capability information when reporting a problem; see [release status](/en/project/release-status).
:::

### Build from source (optional)

To modify the compiler or try unpublished changes, you need Git, JDK 21+ and Python 3.12+:

```bash
git clone https://github.com/ColinHouse/Sprig.git
cd Sprig
python3 scripts/build.py
```

On Windows replace the last line with `py -3 scripts/build.py`. The first build downloads pinned build tools. Use the repository's `bin/sprig` (`bin\sprig.cmd` on Windows), or add its `bin` directory to `PATH`. You can also replace every `sprig ...` command in this book with that launcher's full path.

## 1.4 Editor: VS Code

You can write Sprig in any text editor, but a good one saves you work. Use [VS Code](https://code.visualstudio.com/):

1. Install VS Code.
2. Install [Sprig from the Marketplace](https://marketplace.visualstudio.com/items?itemName=ColinHouse.sprig-language), checking that the publisher is **ColinHouse**. It uses the SDK you installed for diagnostics, parameter hints and runs. If it cannot find the compiler, set **Sprig: Compiler Path** to the SDK launcher (`bin/sprig`, or `bin\sprig.cmd` on Windows); see the [extension guide](/en/guide/editor).
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
compilerVersion: 0.8.0-beta.1
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
- Install the published v0.8.0-beta.1 SDK; building from source is optional.
- `sprig version` identifies the SDK release; `sprig capabilities` lists features and the separate language version.
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

2. Run `sprig version`, then `sprig capabilities`. Find the compiler and language versions.
   Hint: the SDK release version and the language version describe different things.

::: details Answer
For the current published SDK:

```text
sprig-compiler 0.8.0-beta.1
```

The capability list reports compiler `0.8.0-beta.1` and language `0.8-dev`. A newer SDK may have a different version; an older SDK should be upgraded as in 1.3.
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
