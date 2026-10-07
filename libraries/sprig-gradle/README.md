# Sprig Gradle plugin

`dev.sprig` connects an existing Sprig project to an ordinary Gradle Java
source set. Gradle remains responsible for dependencies, source sets, `javac`,
tests, packaging and host frameworks. The plugin owns the repeated bridge,
classpath, generated-source, runtime-source and lifecycle wiring.

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

The resolved compiler must provide `build --emit-java-only`, `sprig test`, a
complete runtime source tree and `javac`. Missing/incompatible toolchains fail
during project configuration with setup guidance.

## Source layout and extension options

| Setting | Default | Meaning |
|---|---|---|
| `projectDirectory` | Gradle project directory | Directory containing `sprig.toml` and `sprig.lock`. |
| `targetSourceSet` | `main` | Existing Java source set receiving generated code and runtime sources. |
| `testDirectory` | `<projectDirectory>/tests` | Sprig test programs passed to `sprig test`. |
| `executable` | discovered | Explicit Sprig launcher override. |

Java bridge sources use the convention `src/sprigBridge/java/`. The plugin
compiles them against the selected source set's actual compile classpath,
adds the resulting classes to both compiler visibility and host runtime output,
and includes them in the final artifact. The compiler receives that classpath
through a file (`build/tmp/<task>/classpath.txt`, one entry per line, passed as
`--classpath-file`), so a Loom classpath of several hundred JARs also works
within the Windows command-line limit.

Generated Java is placed under
`build/generated/sprig/<sourceSet>/java/`; the compiler runtime sources are
registered with the same source set. Generation clears its owned output before
writing, so removed modules cannot leave stale classes in the build. Output is
ordinary Java that can be inspected and debugged.

## Tasks and Gradle lifecycle

| Task | Purpose |
|---|---|
| `sprigCheck` | Statically checks the project against the selected Java classpath. |
| `sprigTest` | Runs the configured Sprig test directory. |
| `sprigGenerate` | Emits checked Java into the owned build directory. |
| `compileSprigBridge` | Compiles the conventional Java boundary sources. |
| `sprigResolve` | Explicitly resolves dependencies and updates `sprig.lock`. |
| `sprigInfo` | Reports compiler, manifest, source set, paths, classpath count and lock status. |

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

This release is verified with Sprig 0.7.1-beta.1, Gradle 9.7.1 and a JDK 26
host running the compiler's Java 21 bytecode. The Fabric starter additionally
uses Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3 and Loom
1.18.2. This is a tested combination, not a promise of compatibility with
every Gradle, JDK, Loom or Minecraft release. A host Gradle build can use any
Java source set supplied by its Java plugin.

## Non-goals

The plugin does not replace Gradle/Maven or Sprig dependency resolution, manage
Fabric mappings, package jars itself, replace `javac`, transform Java APIs, or
hide unsupported JVM signatures. It adds no Sprig syntax or language semantics.
