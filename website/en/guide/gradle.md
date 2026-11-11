# Gradle integration

Already have a Java project built with Gradle? The official `dev.sprig` plugin lets you add Sprig code to it. The plugin reads the project's real compile classpath, compiles the optional Java bridge code, runs Sprig's checks and tests, adds the generated Java and the project's own copy of the runtime to the project, and hooks all of it into Gradle's usual build.

::: tip Needs v0.6.0-beta.1 or newer
The plugin ships with the SDK starting with v0.6.0-beta.1. If `sprig version` shows an older version, run `sprig upgrade` first.
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

For a Minecraft mod built with Loom, target `main` for content both sides need, such as items and blocks, and `client` only for a client-only mod. [Fabric mods](/en/guide/fabric) walks through a complete project.

## Everyday use

Use Gradle as usual:

```sh
./gradlew check
./gradlew build
```

- `check` runs `sprigCheck` and `sprigTest`.
- `build` generates Java from your checked Sprig code and compiles it along with the project's own copy of the runtime.
- Java bridge code goes in `src/sprigBridge/java/` by default. The generated Java ends up in `build/generated/sprig/<sourceSet>/java/`, where you can read it.
- Sprig's tasks only read `sprig.lock` and never go online. To update dependencies, run `./gradlew sprigResolve` explicitly.
- `./gradlew sprigInfo` shows which compiler and runtime are in use, the target source set, the bridge code, the generated directory and package, the test directory, the lock status and the size of the classpath.

The plugin looks for the `sprig` compiler in this order: an explicit setting such as `sprig { executable = ... }`, then `SPRIG_HOME`, then your `PATH`, and finally an SDK installed under `~/.sprig`.

## Using Sprig classes from Java

Sprig generates your classes into a package named after the project. With `name = "ordinary-project"` in `sprig.toml`, the package is `sprig.user.ordinary_project` (every character a Java package name can't hold becomes `_`). Java code in the project names the classes with that package:

```java
import sprig.user.ordinary_project.$Counter;

System.out.println(new $Counter().current());
```

If you forget the package, run `./gradlew sprigInfo` and look for the `Generated package` line.

The Sprig runtime your project uses sits below the same package (`sprig.user.ordinary_project.runtime`), one copy per project. So two jars built with Sprig have no class name in common, and they can share a JVM without getting in each other's way: two Fabric mods in one game, for example.

Two things to keep in mind:

- Rename the project and the package changes with it. `javac` points at every piece of Java code that still uses the old one.
- Pass values between two such jars as Java types (`String`, numbers, an interface of your own). Each has its own runtime, so one project's Sprig `List` isn't the other's.

::: tip Needs v0.9.0-beta.1 or newer
SDK v0.8.0-beta.1 and earlier generate every project's classes into `sprig.user`, with the runtime in `sprig.runtime`. When you move on from one of them, change `sprig.user.` in your Java code to `sprig.user.<project>.`.
:::

The [`dev.sprig` plugin guide](https://github.com/ColinHouse/Sprig/blob/main/libraries/sprig-gradle/README.md) covers the remaining options, compatibility and every task in detail. The plugin saves you from writing the same build setup in every project. It doesn't replace Gradle, Loom or Java, and it says nothing about whether Sprig is more productive than Java.
