# Fabric mods: writing Minecraft mod logic in Sprig

Sprig can take care of a mod's data model, state, saving and game logic, while the parts that talk to Minecraft and Fabric stay in Java. For a new project, start from the Fabric template that ships with the SDK; it already has the official `dev.sprig` Gradle plugin set up.

::: tip Needs v0.6.0-beta.1 or newer
The template and the Gradle plugin ship with the SDK starting with v0.6.0-beta.1. If `sprig version` shows an older version, run `sprig upgrade` first.
:::

## Start from the template

The SDK includes the template and the `libraries/sprig-gradle` plugin it uses. Set `SPRIG_HOME` to the SDK's root, then run:

```sh
cp -R "$SPRIG_HOME/libraries/sprig-fabric/template" ./my-mod
cd my-mod
./gradlew check
./gradlew build
./gradlew runClient
```

The template loads the plugin from the SDK, so you don't need to clone the Sprig repository or hard-code any path on your machine.

- `check` runs Sprig's static checks, the Sprig tests and the usual Java checks.
- `build` generates and compiles the Java for your Sprig code, the runtime and the bridge code.
- `runClient` starts the game client from that same generated output.

The only Sprig-specific setting in `build.gradle` picks the source set:

```groovy
plugins {
    id 'net.fabricmc.fabric-loom' version "${loom_version}"
    id 'dev.sprig'
}

sprig {
    targetSourceSet = 'client'
}
```

Fabric dependencies, split source sets, the mod entry point and the Java version are all ordinary Loom configuration. The Sprig plugin compiles the bridge code, supplies the real classpath, generates sources, adds the runtime sources and hooks all of it into Gradle's build.

## Who does what

| Path | What goes there |
|---|---|
| `src/main.spr` | State and logic written in Sprig |
| `tests/*.spr` | Project tests, run by `sprigTest` |
| `src/sprigBridge/java/` | A thin Java interface for Sprig to call |
| `src/client/java/` | The Fabric client entry point and callback registration |
| `build/generated/sprig/client/java/` | The generated Java, which you can read |

Gradle and Loom handle dependencies, source sets, javac and the jar. Sprig handles type checking, code generation, lock validation and the Sprig tests. The plugin connects the two. Dependencies never update by themselves: `check` and `build` only read the current `sprig.lock`, and only an explicit `./gradlew sprigResolve` updates it.

## Tested versions

The template is tested with Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Fabric Loom 1.18.2, Gradle 9.7.1 and OpenJDK 26.0.1 (targeting Java 25). That's one combination that has actually been tested, not a promise that every Fabric or Loom version works.

## Where this template came from

An earlier comparison used a Quest Board mod built twice with the same features: once in Java only, and once in Java plus Sprig. Both versions built and ran. The Java-only version had 707 lines of production code; the mixed version had 760, about 470 of them Sprig application logic.

What kept costing effort that time was the Gradle and Loom setup: compiling the Java bridge, exporting the client classpath, generating and registering Java code, adding the runtime and wiring up `check`. None of that was something missing from the Sprig language, so it became the plugin and this template.

That's the experience of a single project, with no time or token measurements, so it isn't a rigorous productivity comparison. And with the manual setup it needed back then, the Java-only version was the one picked in the end. The plugin removes the setup cost seen in that project. **It does not prove that Sprig is more productive than Java.** It only means a future comparison has fewer distractions that have nothing to do with the language.

For more details and limitations, see [`libraries/sprig-fabric/README.md`](https://github.com/ColinHouse/Sprig/blob/main/libraries/sprig-fabric/README.md) and [`libraries/sprig-gradle/README.md`](https://github.com/ColinHouse/Sprig/blob/main/libraries/sprig-gradle/README.md).
