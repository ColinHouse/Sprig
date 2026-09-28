# Five-minute first project

Sprig is an experimental JVM language for CLI tools, automation and reliable
application code. Install **JDK 17+**; both `java` and `javac` must be on `PATH`.
The SDK contains the compiler/runtime libraries and launcher, not a JDK.

## Download and verify

The published SDK is [v0.3.0-alpha.1](https://github.com/ColinHouse/Sprig/releases/tag/v0.3.0-alpha.1).
Download the ZIP and its `.sha256` from that release. On Linux/macOS:

```bash
curl -LO https://github.com/ColinHouse/Sprig/releases/download/v0.3.0-alpha.1/sprig-v0.3.0-alpha.1-jdk.zip
curl -LO https://github.com/ColinHouse/Sprig/releases/download/v0.3.0-alpha.1/sprig-v0.3.0-alpha.1-jdk.zip.sha256
shasum -a 256 -c sprig-v0.3.0-alpha.1-jdk.zip.sha256
unzip sprig-v0.3.0-alpha.1-jdk.zip
cd sprig-v0.3.0-alpha.1-jdk
```

On Linux, `sha256sum -c` is also available. Stop if the checksum differs.
Use the release assets and capability output to check which features your SDK
actually includes. The v0.3 SDK supports local/Git/Maven dependencies and
explicit bundled @std imports; the v0.4.0-alpha.1 release candidate adds the
canonical formatter, explicit module re-exports and expression `match`.

## Initialize, resolve, run

Inside the extracted SDK, save an absolute launcher path so changing directory
keeps it available:

```bash
./bin/sprig version
SPRIG="$(pwd)/bin/sprig"
"$SPRIG" init my-tool
cd my-tool
"$SPRIG" resolve
"$SPRIG" run
```

Expected program output:

```text
Hello, Sprig!
```

`init` creates `sprig.toml` and `src/main.spr` without overwriting existing
files. `resolve` creates `sprig.lock`. `run` checks types, generates Java,
invokes `javac`, and executes on the JVM. Source edits do not require a new
lock; manifest/dependency changes require `resolve` again.

## Windows and source builds

Windows is an experimental preview, not a supported release platform. The portable source build is available for testing. Requires Git,
JDK 17+ and Python 3.12+; Maven CLI and Bash are not required. PowerShell:

```powershell
git clone https://github.com/ColinHouse/Sprig.git
Set-Location Sprig
py -3 scripts/build.py
$Sprig = (Resolve-Path .\bin\sprig.cmd).Path
& $Sprig version
& $Sprig init my-tool
Set-Location my-tool
& $Sprig resolve
& $Sprig run
```

On Linux/macOS, clone the same repository and use `python3 scripts/build.py`,
then the `bin/sprig` sequence above. The first build downloads pinned ANTLR and
Maven Resolver libraries. Node.js 20+/npm is needed only for documentation and
the full contributor gate. [Release status](/en/project/release-status) states
which OS/JDK combinations have actually passed.

## Ask the compiler, then edit

From your project (replace `sprig` with the absolute launcher if it is not on `PATH`):

```bash
sprig capabilities --json
sprig help generics --json
sprig api java.time.LocalDate --json
sprig check --json
sprig explain SPR-TYPE-NULLABLE --json
sprig run
```

JSON diagnostics carry stable codes and source positions. Do not guess from
another language: Java reference results are nullable, integer division is
explicit and generics use explicit type arguments.
[Tooling and JSON](/en/guide/tooling) explains the envelopes.

## Make it useful

Try the [showcase projects](https://github.com/ColinHouse/Sprig/tree/main/examples/showcases):
repository auditing with JSON output, a real Maven-library application and
source analysis. Each README specifies the entry, fixture and offline boundary.
Learn individual constructs in the [language tour](/en/guide/language-tour).

Want to contribute with your coding agent? Read
[Contributing](/en/project/contributing) and `AGENTS.md`, choose a scoped issue,
run `scripts/verify.sh` (`py -3 scripts/verify.py` on Windows), review and open a PR.

## First-run problems

- **JDK unavailable:** install a JDK and check both `java -version` and `javac -version`.
- **Missing or stale lock:** run `resolve` in the project after manifest edits.
- **Offline cache miss:** populate the cache with an online resolve first; a lock alone is insufficient.
- **`SPR-LEX-TAB`:** use spaces for indentation.
- **Nullable Java result:** narrow with `!= null` before use.
- **Checksum mismatch:** retain the evidence and redownload the affected artifact; never disable checksum verification.
