# Sprig Gradle plugin

`dev.sprig` connects an existing Sprig project to an ordinary Gradle Java
source set. Gradle remains responsible for dependencies, source sets, `javac`,
tests, packaging and host frameworks. The plugin owns the repeated bridge,
classpath, generated-source and lifecycle wiring.

The plugin ships as source inside the Sprig SDK at
`libraries/sprig-gradle/`. This repository does not publish it to the Gradle
Plugin Portal. Consumers use Gradle's included-build mechanism against their
installed SDK; no sibling source checkout or plugin credentials are needed.

## Install and use

Set `SPRIG_HOME` to the extracted SDK root (or pass `-PsprigHome=<path>`).
The SDK contains both the `bin/sprig` compiler and this plugin build. In a
project's `settings.gradle`, include the SDK build in `pluginManagement`:

```groovy
pluginManagement {
    def home = providers.gradleProperty('sprigHome')
        .orElse(providers.environmentVariable('SPRIG_HOME')).get()
    includeBuild(new File(home, 'libraries/sprig-gradle'))
    repositories { gradlePluginPortal(); mavenCentral() }
}
```

Apply the Java plugin (or a host plugin that applies it) and the Sprig plugin.
The consumer-facing block is intentionally small:

```groovy
plugins {
    id 'java'
    id 'dev.sprig'
}

sprig {
    targetSourceSet = 'main'
}
```

For Fabric/Loom, target `main` for content both sides need (items, blocks) and `client` for a client-only mod; see
[`sprig-fabric`](../sprig-fabric/README.md) for a runnable starter.

## Toolchain discovery

The plugin asks `sprig doctor --json` and `sprig capabilities --json` for
compiler version, compiler home, compiler classpath, runtime source location,
and supported commands. It validates these reported paths instead of
guessing the SDK layout. Toolchain selection order is:

1. `sprig { executable = file(...) }`, `-Psprig.executable=...`, or
   `SPRIG_EXECUTABLE` (in that order).
2. `-PsprigHome=...` or `SPRIG_HOME`, resolved to `bin/sprig`.
3. `sprig` on `PATH`.
4. The managed `~/.sprig/current/bin/sprig` or `~/.sprig/bin/sprig` install.

The resolved compiler must provide `build --emit-java-only --with-runtime`,
`sprig test`, a complete runtime source tree and `javac`. Missing/incompatible
toolchains fail during project configuration with setup guidance.

## Source layout and extension options

| Setting | Default | Meaning |
|---|---|---|
| `projectDirectory` | Gradle project directory | Directory containing `sprig.toml` and `sprig.lock`. |
| `targetSourceSet` | `main` | Existing Java source set receiving the generated code and the project's copy of the runtime. |
| `testDirectory` | `<projectDirectory>/tests` | Sprig test programs passed to `sprig test`. |
| `executable` | discovered | Explicit Sprig launcher override. |

Java bridge sources use the convention `src/sprigBridge/java/`. The plugin
compiles them against the selected source set's actual compile classpath,
adds the resulting classes to both compiler visibility and host runtime output,
and includes them in the final artifact. The compiler receives that classpath
through a file (`build/tmp/<task>/classpath.txt`, one entry per line, passed as
`--classpath-file`), so a Loom classpath of several hundred JARs also works
within the Windows command-line limit.

Generated Java is placed under `build/generated/sprig/<sourceSet>/java/` and
registered with the source set. Generation clears its owned output before
writing, so removed modules cannot leave stale classes in the build. Output is
ordinary Java that can be inspected and debugged.

## Generated package and runtime

Everything Sprig adds to your jar is in one package, named after the project:

| Package | What is in it |
|---|---|
| `sprig.user.<project>` | The classes generated from your `.spr` modules: `$M_main`, `$Counter` and so on. |
| `sprig.user.<project>.runtime` | The project's own copy of the Sprig runtime. |

`<project>` is the `name` in `sprig.toml`, with every character a Java package
name cannot hold replaced by `_`: `ordinary-project` gives
`sprig.user.ordinary_project`. `./gradlew sprigInfo` prints the package. Java
code in the project names a generated class with it:

```java
import sprig.user.ordinary_project.$Counter;
```

Two jars built this way have no class name in common. That matters wherever
one class loader holds several of them, such as two Fabric mods in one game or
two Sprig-built libraries on one classpath: each runs its own classes and its
own runtime, whichever SDK built the other.

Two things follow from it:

- **The package is part of your Java source.** Rename the project and `javac`
  reports every reference to the old package.
- **Values cross between two such jars as Java types** (`String`, `long`, your
  own interfaces). One project's `List` and another's are different classes.

`sprig build --emit-java-only --with-runtime -d <dir>` writes the same tree
without Gradle, for another build tool to compile.

### Calling in from Java

Java calls a module's function as a static method of the module's class and
makes a Sprig object with `new`. No start call comes first:

```java
sprig.user.ordinary_project.$M_store.fn$remember("x");
long current = new sprig.user.ordinary_project.$Counter().current();
```

The first function Java calls, or the first object with methods it makes, runs
the module's top-level statements first, after those of the modules it
imports, and once. A top-level `let` or `var` has its value whenever Sprig
code reads it, as it has in a program started by `sprig run`.

- **A failing statement fails that first call**: an unchecked failure as it
  is, a checked Java exception as the cause of an `IllegalStateException` that
  names the module. The statements are not run again; treat it as fatal.
- **Make the first call before other threads use the code.** The statements
  run once, on the thread of the first call, and a call from another thread
  does not wait for them to finish.
- **Read top-level state through a function.** A binding's static field, such
  as `$M_store.names`, read from Java before any call is still `null`.

[Gradle integration](../../docs/jvm/gradle.md#calling-sprig-code-from-java)
has the complete rules and what the check costs.

## Tasks and Gradle lifecycle

| Task | Purpose |
|---|---|
| `sprigCheck` | Statically checks the project against the selected Java classpath. |
| `sprigTest` | Runs the configured Sprig test directory. |
| `sprigGenerate` | Emits checked Java and the project's copy of the runtime into the owned build directory. |
| `compileSprigBridge` | Compiles the conventional Java boundary sources. |
| `sprigResolve` | Explicitly resolves dependencies and updates `sprig.lock`. |
| `sprigInfo` | Reports compiler, manifest, source set, generated package, paths, classpath count and lock status. |

`check` depends on `sprigCheck` and `sprigTest`. The selected Java compile task
depends on bridge compilation and Sprig generation, so `build`, `jar` and host
run tasks receive current output without consumer-authored task wiring.

Project checking, generation and testing always consume the current lock in
offline mode. They never resolve or update dependencies. A missing or stale
lock preserves the compiler's diagnostic and directs the user to run
`sprigResolve` or the CLI's `sprig resolve`; only that explicit task can access
the network. When Gradle itself runs with `--offline`, `sprigResolve` also
respects that choice.

Tasks declare source, manifest, lock, bridge, compiler and classpath inputs.
Unchanged generation is Gradle `UP-TO-DATE`; tasks are not marked cacheable
until the compiler's output is proven relocatable.

## Debugging and compatibility

Run `./gradlew sprigInfo` for concise machine-readable build context. Use
`./gradlew sprigCheck --info` to see the complete classpath and direct Sprig
diagnostics. `javac` errors identify the generated source directory and Java
boundary source as usual.

This plugin is verified with the compiler built from this source (Java 21
bytecode), Gradle 9.7.1 and a JDK 26 host. It needs the compiler it ships
with: v0.8.0-beta.1 and earlier have no `build --emit-java-only
--with-runtime`, and the plugin stops with setup guidance when the compiler
lacks it. Use the plugin from the same SDK as the compiler.

The Fabric starter additionally uses Minecraft 26.3, Fabric Loader 0.19.5,
Fabric API 0.161.0+26.3 and Loom 1.18.2. This is a tested combination, not a
promise of compatibility with every Gradle, JDK, Loom or Minecraft release. A
host Gradle build can use any Java source set supplied by its Java plugin.

## Non-goals

The plugin does not replace Gradle/Maven or Sprig dependency resolution, manage
Fabric mappings, package jars itself, replace `javac`, transform Java APIs, or
hide unsupported JVM signatures. It adds no Sprig syntax or language semantics.
