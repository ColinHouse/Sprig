# Sprig with Fabric/Loom: move build wiring into tooling

A Quest Board dogfood showed that Sprig could own the model, state, persistence and application logic. The repeated cost in the hybrid project came from Gradle/Loom: compiling a Java bridge, exporting the client classpath, generating and registering Java, adding the runtime, and connecting `check`. That was not a missing Sprig language feature. New projects should start with the first-party `dev.sprig` plugin and Fabric template.

The goal is to put repeated build wiring in reusable tooling while keeping the Java host boundary and Sprig application logic visible. See the [Gradle integration guide](/en/guide/gradle) for the plugin contract and [`libraries/sprig-fabric`](https://github.com/ColinHouse/Sprig/tree/main/libraries/sprig-fabric) for the starter files.

## Start from the SDK template

The Sprig SDK includes the template and the `libraries/sprig-gradle` included build. Set `SPRIG_HOME` to the extracted SDK root:

```sh
cp -R "$SPRIG_HOME/libraries/sprig-fabric/template" ./my-mod
cd my-mod
./gradlew check
./gradlew build
./gradlew runClient
```

The template settings load the plugin from the SDK. No Sprig repository clone or personal absolute path is required. `check` runs Sprig static checks, Sprig tests and normal Java checks; `build` generates and compiles Sprig Java, runtime and bridge code; `runClient` uses the same generated output.

The consumer's Sprig configuration only selects a source set:

```groovy
plugins {
    id 'net.fabricmc.fabric-loom' version "${loom_version}"
    id 'dev.sprig'
}

sprig {
    targetSourceSet = 'client'
}
```

Fabric dependencies, split source sets, mod entrypoint and Java release remain ordinary Loom configuration. The Sprig plugin owns bridge compilation, the real classpath, generated sources, runtime sources and Gradle lifecycle wiring.

## Project boundary

| Path | Responsibility |
|---|---|
| `src/main.spr` | Sprig domain state and logic |
| `tests/*.spr` | Project tests run by `sprigTest` |
| `src/sprigBridge/java/` | Narrow Java interface consumed by Sprig |
| `src/client/java/` | Fabric client initializer and callback registration |
| `build/generated/sprig/client/java/` | Inspectable generated Java |

Gradle/Loom owns dependencies, source sets, `javac` and the jar. Sprig owns type checking, generation, lock validation and Sprig tests. The plugin connects the two. Dependency resolution stays explicit: `check`/`build` consume the current `sprig.lock`; only `./gradlew sprigResolve` updates it.

## Verified scope

The starter is tested with Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Fabric Loom 1.18.2, Gradle 9.7.1 and OpenJDK 26.0.1 (`javac --release 25`). This is a tested combination, not a compatibility promise for all Fabric or Loom versions.

In the earlier Java-only / Sprig hybrid Quest Board comparison, both versions built and ran. Java-only production was 707 LOC; hybrid production was 760 LOC, including about 470 LOC of Sprig application code. This was one uninstrumented project, with no time or token measurement, and was not a controlled productivity benchmark; Java-only was selected under the manual wiring setup. The plugin removes the observed Gradle/Loom setup cost. **It does not prove that Sprig is more productive than Java.** It makes a future comparison possible with fewer integration confounders.

See [`libraries/sprig-fabric/README.md`](https://github.com/ColinHouse/Sprig/blob/main/libraries/sprig-fabric/README.md) and [`libraries/sprig-gradle/README.md`](https://github.com/ColinHouse/Sprig/blob/main/libraries/sprig-gradle/README.md) for details and limits.
