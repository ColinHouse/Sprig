# Getting started: install Sprig

This page gets Sprig installed and your first project running. When you're done, continue with the [tutorial](/en/tutorial/).

## Install a JDK first

Sprig compiles your code to Java and runs it on the JVM, so you need **JDK 21 or newer**. It has to be a full JDK, not just a JRE. Check with:

```bash
java -version
javac -version
```

If both commands print a version, you're good. The SDK doesn't include a JDK.

## Install the SDK (Linux / macOS)

The easiest way is the install script. It downloads the official release, checks its SHA-256 checksum and puts the `sprig` command in `~/.local/bin`:

```bash
curl -fsSL https://raw.githubusercontent.com/ColinHouse/Sprig/main/scripts/install-sprig.sh | sh
export PATH="$HOME/.local/bin:$PATH"
sprig version
```

To upgrade later, run `sprig upgrade --check` to see whether there's a new version, then `sprig upgrade`.

### Manual download

You can also download the ZIP from the [release page](https://github.com/ColinHouse/Sprig/releases/tag/v0.7.1-beta.1), verify it and unpack it yourself:

```bash
curl -LO https://github.com/ColinHouse/Sprig/releases/download/v0.7.1-beta.1/sprig-v0.7.1-beta.1-jdk.zip
curl -LO https://github.com/ColinHouse/Sprig/releases/download/v0.7.1-beta.1/sprig-v0.7.1-beta.1-jdk.zip.sha256
shasum -a 256 -c sprig-v0.7.1-beta.1-jdk.zip.sha256
unzip sprig-v0.7.1-beta.1-jdk.zip
```

On Linux, `sha256sum -c` works too. If the checksum doesn't match, download the file again; don't skip the check. Then add the unpacked directory's `bin` to your `PATH`.

## Your first project

```bash
sprig init my-tool
cd my-tool
sprig resolve
sprig run
```

You should see `Hello, Sprig!`. Here's what each command did:

- `sprig init` creates `sprig.toml` (the project file) and `src/main.spr` (the entry point). It never overwrites existing files.
- `sprig resolve` resolves dependencies and writes the lock file `sprig.lock`. Editing code doesn't require it; changing `sprig.toml` or your dependencies does.
- `sprig run` checks your code, generates Java, compiles it and runs it.

## Everyday commands

| Command | What it does |
|---|---|
| `sprig check` | Checks without running and lists every error at once |
| `sprig run` | Checks and runs |
| `sprig test` | Runs the tests in `tests/` |
| `sprig fmt .` | Formats your code in the standard style |
| `sprig explain <code>` | Explains an error code: causes, fixes, examples |
| `sprig help <topic>` | Quick syntax reference, e.g. `sprig help nullability` |
| `sprig api <Java class>` | Shows a Java class's methods as Sprig sees them |

Almost all of them accept `--json` and return structured output that editors, scripts and AI coding assistants can read. See [tools and JSON](/en/guide/tooling).

For editing, there's a [VS Code extension](/en/guide/editor) (a local preview for now) with syntax highlighting, checks on save and one-click run.

## Windows and building from source

Windows support is still experimental, and the install script only covers Linux and macOS. The release ZIP includes a Windows launcher, `bin\sprig.cmd`: check the ZIP's SHA-256, unpack it and run that launcher (see [installing on Windows](/en/reference/projects/install#windows)). You can also build from source with Git, JDK 21+ and Python 3.12+; you don't need Bash or Maven. In PowerShell:

```powershell
git clone https://github.com/ColinHouse/Sprig.git
Set-Location Sprig
py -3 scripts/build.py
$Sprig = (Resolve-Path .\bin\sprig.cmd).Path
& $Sprig init my-tool
Set-Location my-tool
& $Sprig resolve
& $Sprig run
```

Building from source on Linux or macOS works the same way: clone the repository, run `python3 scripts/build.py` and use the repository's `bin/sprig`. The first build downloads pinned versions of ANTLR and Maven Resolver.

## If something goes wrong

- **`javac` not found**: you probably have a JRE; install a JDK.
- **Missing or stale lock file**: run `sprig resolve` in the project directory.
- **Incomplete dependency cache when offline**: run `sprig resolve` once while online.
- **`SPR-LEX-TAB`**: Sprig indents with spaces only, never tabs.
- **A Java method's result can't be used directly**: Sprig treats it as possibly `null`; check it with `if x != null:` first.
- **Checksum mismatch**: download the file again; don't turn the check off.

Still stuck? [Open an issue](https://github.com/ColinHouse/Sprig/issues) with the command you ran and its full output.
