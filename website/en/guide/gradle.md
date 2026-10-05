# Gradle integration

Already have a Java project built with Gradle? The official `dev.sprig` plugin lets you add Sprig code to it. The plugin reads the project's real compile classpath, compiles the optional Java bridge code, runs Sprig's checks and tests, adds the generated Java and runtime sources to the project, and hooks all of it into Gradle's usual build.

::: warning Needs a version newer than v0.5.0-beta.1
The plugin was added after v0.5.0-beta.1 was released, so the published SDK doesn't include it yet. Until the next release, clone the Sprig repository, build it with `python3 scripts/build.py`, and point `SPRIG_HOME` below at that checkout.
:::

## Adding the plugin

The plugin ships as source in the SDK's `libraries/sprig-gradle/` directory and is used through Gradle's included-build mechanism, so you don't need a Gradle Plugin Portal account. Set the `SPRIG_HOME` environment variable to the SDK's root (or pass `-PsprigHome=<path>` to Gradle), then bring the plugin in from `settings.gradle`:

```groovy
pluginManagement {
    def home = providers.gradleProperty('sprigHome')
        .orElse(providers.environmentVariable('SPRIG_HOME')).get()
    includeBuild(new File(home, 'libraries/sprig-gradle'))
    repositories { gradlePluginPortal(); mavenCentral() }
}
```

Then apply it in `build.gradle`:

```groovy
plugins {
    id 'java'
    id 'dev.sprig'
}

sprig {
    targetSourceSet = 'main'
}
```

For a Minecraft mod built with Loom, set `targetSourceSet` to `client`; [Fabric mods](/en/guide/fabric) walks through a complete project.

## Everyday use

Use Gradle as usual:

```sh
./gradlew check
./gradlew build
```

- `check` runs `sprigCheck` and `sprigTest`.
- `build` generates Java from your checked Sprig code and compiles it along with the runtime sources.
- Java bridge code goes in `src/sprigBridge/java/` by default. The generated Java ends up in `build/generated/sprig/<sourceSet>/java/`, where you can read it.
- Sprig's tasks only read `sprig.lock` and never go online. To update dependencies, run `./gradlew sprigResolve` explicitly.
- `./gradlew sprigInfo` shows which compiler and runtime are in use, the target source set, the bridge code, the generated and test directories, the lock status and the size of the classpath.

The plugin looks for the `sprig` compiler in this order: an explicit setting such as `sprig { executable = ... }`, then `SPRIG_HOME`, then your `PATH`, and finally an SDK installed under `~/.sprig`.

The [`dev.sprig` plugin guide](https://github.com/ColinHouse/Sprig/blob/main/libraries/sprig-gradle/README.md) covers the remaining options, compatibility and every task in detail. The plugin saves you from writing the same build setup in every project. It doesn't replace Gradle, Loom or Java, and it says nothing about whether Sprig is more productive than Java.
