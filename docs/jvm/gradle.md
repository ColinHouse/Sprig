# Gradle integration

The first-party `dev.sprig` Gradle plugin integrates an existing Sprig project
with an ordinary Java source set. It reads Gradle's resolved compile classpath,
compiles the optional Java bridge before Sprig checking, emits reviewable Java
with the project's own copy of the runtime, registers it with the source set,
and connects Sprig tests to `check`.

See the full [plugin guide](../../libraries/sprig-gradle/README.md) for the
extension, tasks, toolchain discovery, lock behavior and compatibility
contract. New Fabric projects should start from the
[`sprig-fabric` template](../../libraries/sprig-fabric/README.md).

## Consumer configuration

The SDK carries the plugin as a Gradle included build. Set `SPRIG_HOME` to an
extracted SDK and include its plugin build in `settings.gradle`:

```groovy
pluginManagement {
    def home = providers.gradleProperty('sprigHome')
        .orElse(providers.environmentVariable('SPRIG_HOME')).get()
    includeBuild(new File(home, 'libraries/sprig-gradle'))
    repositories { gradlePluginPortal(); mavenCentral() }
}
```

A regular Java project then applies `dev.sprig` and chooses the target source
set. Fabric/Loom projects usually choose `client` after enabling split
environment source sets:

```groovy
plugins {
    id 'net.fabricmc.fabric-loom' version "${loom_version}"
    id 'dev.sprig'
}

sprig {
    targetSourceSet = 'client'
}
```

The host build still declares its own Java/Minecraft/Fabric dependencies and
mod membership. It does not compile a bridge, serialize a classpath, invoke an
`Exec` generation task, register runtime sources, or wire Sprig tests itself.

## Generated package

The classes generated for a project are in `sprig.user.<project>`, and the
project's copy of the runtime in `sprig.user.<project>.runtime`, where
`<project>` is the manifest's `name` with every character a Java package name
cannot hold replaced by `_`. Java code in the project names a generated class
with that package (`sprig.user.ordinary_project.$Counter`), and
`./gradlew sprigInfo` prints it. Two jars built this way share no class name,
so one class loader can hold both: two Fabric mods in one game, or two
Sprig-built libraries on one classpath. The
[plugin guide](../../libraries/sprig-gradle/README.md#generated-package-and-runtime)
has the details.

## Calling Sprig code from Java

Java code in the project calls a module's function as a static method of the
module's class, and makes a Sprig object with `new`:

```java
sprig.user.ordinary_project.$M_store.fn$remember("x");
long current = new sprig.user.ordinary_project.$Counter().current();
```

Neither needs a start call first. A module's top-level statements, among them
the initializers of its top-level `let` and `var` bindings, run once before the
module is used. In a program started by `sprig run` or its generated `main`
that is at start-up. A Java host starts no such program, so they run when Java
code first calls one of the module's functions, or first makes an object of one
of its classes that has methods: the statements of the modules it imports
first, in import order, then its own, as on import. Sprig code entered this way
finds its top-level bindings initialized, as it does in a program started
through `main`, and not at their Java defaults (`null`, `0`, `false`).

- **A statement that throws** fails the call that started the statements. An
  unchecked failure, a Sprig `Error` among them, arrives as it is; a checked
  Java exception, which a top-level statement may leave uncaught, arrives as
  the cause of an `IllegalStateException` that names the module. The statements
  are not run again, and the bindings they did not reach stay without a value:
  treat the failure as fatal, as `sprig run` does.
- **Threads.** The statements run on the thread that makes the first call, and
  once, also when several threads make their first call at the same time. A
  call from another thread that arrives while they are still running does not
  wait for them and can find a binding that has no value yet. Make the first
  call before other threads use the code; a framework's initializer, such as a
  Fabric `ModInitializer`, is that place.
- **A binding read as a Java field.** `$M_store.names` read from Java before
  any call is still `null`: reading a field cannot start the module. Read
  top-level state through a function.
- **Cost.** Only a module that has a top-level statement, or imports a module
  that has one, carries the check: at the start of its functions and of the
  constructors of its classes with methods. In a program started through `main`
  the check is a constant, and the JIT removes it.

`tests/java_callers/check_java_callers.py` runs Java drivers against generated
classes for each of these rules.

## Responsibilities

| Owner | Responsibilities |
|---|---|
| Gradle/Loom | Dependencies, source sets, `javac`, host tests, run tasks and final jar. |
| Sprig compiler | Language checking, Java generation with the project's copy of the runtime, lock validation and Sprig tests. |
| `dev.sprig` | Toolchain discovery, Java bridge, classpath transfer, generated sources and lifecycle dependencies. |

`check` runs `sprigCheck` and `sprigTest`. Generation is a dependency of the
selected Java compile task, so `build` and host run tasks see current output.
All Sprig verification/generation consumes the committed `sprig.lock` offline;
`sprigResolve` is explicit and is not a dependency of `check` or `build`.

Run `./gradlew sprigInfo` to inspect the compiler, selected source set, bridge,
generated directory, runtime, test directory, lock status and classpath count.
`./gradlew sprigCheck --info` shows the complete classpath while preserving the
compiler's source diagnostics.

The plugin deliberately compiles a narrow handwritten Java bridge; it does not
generate typed Java adapters. Existing `sprig api`, `sprig wrap` and `conform`
serve inspection, Java-to-Sprig wrapper generation and bounded interface
checking. See the [adapter design note](typed-boundary-adapters.md) for why a
general adapter generator is deferred until more independent host examples
show the same remaining boilerplate.

## Verified evidence and limits

The ordinary Java fixture and Fabric starter run outside the Sprig checkout,
including from paths with spaces and Unicode. The tested versions and known
limits are maintained in
[`libraries/sprig-gradle`](../../libraries/sprig-gradle/README.md) and
[`libraries/sprig-fabric`](../../libraries/sprig-fabric/README.md). This is a
narrow verified matrix, not universal Gradle/Loom compatibility.

The Quest Board comparison is a single-project observation, not a controlled
productivity benchmark. First-party integration removes repeated build
plumbing that was a confounder; it does not show that Sprig beats Java.
