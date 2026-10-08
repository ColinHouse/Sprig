# Bundles: `sprig build --bundle`

A bundle is a directory that runs a Sprig program on a machine with no Java
installed. `sprig build --bundle` writes it next to the ordinary build output:

```text
sprig build --bundle                       # the project entry, into sprig-build/<project>/
sprig build app.spr --bundle -d dist       # one file, into dist/app/
sprig build --bin server --bundle          # one bin of a project
sprig build --bundle --archive             # also sprig-build/<name>.zip
sprig build --bundle --json                # the bundle's paths, modules and sizes
```

`--bundle` takes the same inputs as `build`: a project (with `--bin NAME` when
the project declares several), or a single file; `-d OUT` chooses the output
directory as it does for `build`. The program is checked and compiled first;
nothing is written when that fails, and the program is never executed while
bundling.

## Layout

```text
OUT/<name>/
  bin/<name>          POSIX sh launcher (Linux, macOS)
  bin/<name>.cmd      Windows launcher
  lib/<name>.jar      the program's classes, Main-Class set
  lib/sprig-runtime.jar
  lib/*.jar           every JAR of the locked classpath, Maven dependencies included,
                      named by coordinate (commons-text-1.12.0.jar)
  runtime/            a Java runtime image built by jlink
  runtime/legal/      the JDK's license notices, kept intact
  README.txt
```

`<name>` is the `--bin`, the project name or the file name without `.spr`,
reduced to letters, digits, `.`, `_` and `-`. A directory passed with
`--classpath` is packed into a JAR under `lib/`.

The launcher runs the program from the caller's working directory, forwards
every argument unchanged (spaces and UTF-8 included) and exits with the
program's status. Standard output and error are UTF-8 on every platform: the
Windows launcher passes `-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8` like
the POSIX one, so output through a pipe or a redirect is UTF-8 rather than the
console code page; a legacy console that shows non-ASCII text garbled needs
`chcp 65001`. A program that starts `bin\<name>.cmd` passes the launcher path
and the arguments directly to the system (`subprocess.run([launcher, *args])`,
`ProcessBuilder`); wrapping them in `cmd /c` with every argument quoted makes
cmd.exe strip the first and the last quote of the command line, which is
cmd's own rule, not the launcher's. `java.exe` reads its command line in the
ANSI code page, so non-ASCII arguments are a Windows limit.

## The runtime image

1. `jdeps --print-module-deps --ignore-missing-deps --multi-release 21` runs
   over every JAR in `lib/` and names the Java modules they use. Each JAR is
   analyzed as a class-path archive: a dependency that declares a module with a
   `requires` Maven marked optional (sqlite-jdbc requires `org.slf4j`) would
   otherwise fail resolution.
2. `jlink --add-modules <modules> --strip-debug --no-header-files
   --no-man-pages --compress zip-6 --generate-cds-archive` builds `runtime/`
   (`--compress=2` on a JDK older than 21; without `--generate-cds-archive`
   when the platform cannot generate the JDK's class-data-sharing archive, which
   the `--json` report shows as `jlinkCdsArchive`).
3. The launcher adds `-XX:+AutoCreateSharedArchive
   -XX:SharedArchiveFile=<cache>/app.jsa` (JDK 19+) so the program's own classes
   are archived on the first run and mapped on the next ones. The archive lives
   under `sprig/bundles/` in the user's cache directory
   (`$XDG_CACHE_HOME/sprig/bundles/`, or `~/.cache/sprig/bundles/` when
   `XDG_CACHE_HOME` is unset, also on macOS; `%LOCALAPPDATA%\sprig\bundles\` on
   Windows), keyed by the bundle's location,
   so copies of a bundle do not share one; without a writable cache the program
   runs without an archive. In that mode the JVM reports only errors, on
   standard error, and none about class-data sharing, so a missing or stale
   archive is rebuilt silently (JDK 26 would otherwise report the archive a first
   run creates as an error). A bundle built by a JDK older than 19 gets launchers
   without these flags (the `--json` report shows `launcherCdsArchive`).

`runtime/legal/` holds the notices of every module in the image. OpenJDK is
licensed under the GPLv2 with the Classpath Exception, which allows
redistributing the image together with those notices; never strip them.

A runtime image runs only on the operating system and architecture that built
it: a bundle built on Linux x86-64 does not run on macOS or on an ARM Linux.
The command output and `README.txt` say which platform that is. Build on each
target platform; cross-target bundling is out of scope, as are GraalVM native
images, installers (`jpackage`) and cross-compiling.

## `--archive`

`--archive` additionally writes `OUT/<name>.zip` next to the bundle, with the
bundle directory as its top-level entry and Unix file modes recorded, so
`unzip` restores the executable bits of the launcher and of `runtime/bin/java`.

## Requirements and failures

Bundling needs the JDK that runs `sprig` to be a full JDK: `jdeps`, `jlink`,
and either the `jmods/` directory or a JDK 24+ runtime built as linkable
(Temurin's JDK 26 builds ship without `jmods/` and link from the runtime
image). Each failure has a stable code with a fix hint:

| Code | When | Fix |
|---|---|---|
| `SPR-BUNDLE-TOOLS` | `jdeps` or `jlink` is missing: `sprig` runs on a JRE or a jlinked image | Run `sprig` with a full JDK 21+ (`sprig doctor` shows the installation) |
| `SPR-BUNDLE-JDEPS` | `jdeps` could not analyze a JAR | Run the command from the diagnostic's `data` by hand; re-resolve a corrupt JAR |
| `SPR-BUNDLE-LAYOUT` | `jlink` cannot image the installation (no `jmods/` and not a JDK 24+ linkable runtime, as in a JRE), a `jlink` failure, a previous bundle that cannot be removed, or a classpath entry that does not exist | Use a full JDK distribution (one with `jmods/`, or a linkable runtime); run the printed `jlink` command for the full report |

`sprig explain SPR-BUNDLE-TOOLS` and `sprig help build` describe the command;
`sprig capabilities --json` reports `features.bundle`.

## Sizes and startup

Measured on Linux x86-64 with OpenJDK 21.0.12 (seven runs each, wall time of
the whole command, `min / median`; the machine was otherwise idle):

| Program | Before: `sprig run` (javac cache warm) | Before: `java -cp` on the host JDK | After: launcher, first run (writes the archive) | After: launcher, warm |
|---|---|---|---|---|
| `print("hello")` | 435 / 449 ms | 59 / 68 ms | 218 ms | 55 / 70 ms |
| `examples/showcases/maven_slug` (CLI, commons-text) | 803 / 856 ms | – | 322 ms | 109 / 130 ms |

| Bundle | `lib/` | `runtime/` | modules | `--archive` |
|---|---|---|---|---|
| hello | 76 KB | 68 MB | java.base, java.net.http, java.sql, jdk.httpserver (the Sprig runtime references them) | 32 MB |
| maven_slug | 975 KB | 80 MB | + java.desktop, java.scripting (commons-text) | – |

The launcher's warm start is the JVM's own start plus the program; `sprig run`
also starts the compiler JVM and checks the program each time.

Verification: `python3 tests/bundle/check_bundle.py` bundles five programs (a
hello program, a CLI with spaced and UTF-8 arguments, a project with a Maven
dependency, a SQLite program whose driver carries a native library, and a
`sprig-web` server) and runs every launcher with `JAVA_HOME` unset and no `java`
on `PATH`, checking exit codes, output and the working directory; it unpacks
the archive form and runs it, and it refuses a JRE-shaped installation and one
whose `jlink` cannot link from it with the stable codes. Windows runs the `.cmd` launcher in the
experimental lane only.
