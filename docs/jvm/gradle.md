# Gradle integration

The first-party `dev.sprig` Gradle plugin integrates an existing Sprig project
with an ordinary Java source set. It reads Gradle's resolved compile classpath,
compiles the optional Java bridge before Sprig checking, emits reviewable Java,
registers generated and runtime sources, and connects Sprig tests to `check`.

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

## Responsibilities

| Owner | Responsibilities |
|---|---|
| Gradle/Loom | Dependencies, source sets, `javac`, host tests, run tasks and final jar. |
| Sprig compiler | Language checking, Java generation, lock validation and Sprig tests. |
| `dev.sprig` | Toolchain discovery, Java bridge, classpath transfer, generated/runtime sources and lifecycle dependencies. |

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
