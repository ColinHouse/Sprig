# Gradle / JVM integration

The first-party `dev.sprig` plugin integrates a Sprig project with a Java source set. It reads the host's real compile classpath, compiles the optional Java bridge, runs Sprig checks and tests, registers generated Java and runtime sources, and connects them to normal Gradle lifecycle tasks.

The plugin ships inside the Sprig SDK as an included build. Set `SPRIG_HOME` to the extracted SDK root; a new project does not need a sibling Sprig source checkout or a Gradle Plugin Portal account.

```groovy
plugins {
    id 'java'
    id 'dev.sprig'
}

sprig {
    targetSourceSet = 'main'
}
```

For Loom, select `client`; the [Fabric starter guide](/guide/fabric) shows the complete project. Then use ordinary commands:

```sh
./gradlew check
./gradlew build
```

`check` includes `sprigCheck` and `sprigTest`. `build` generates and compiles checked Java and runtime sources. Java boundary code defaults to `src/sprigBridge/java/`; generated Java is reviewable under `build/generated/sprig/<sourceSet>/java/`.

Sprig operations consume `sprig.lock` offline. Dependency updates remain explicit through `./gradlew sprigResolve`. `./gradlew sprigInfo` reports compiler/runtime selection, target source set, bridge, generated directory, test directory, lock status and classpath size.

Read the canonical [`dev.sprig` guide](https://github.com/ColinHouse/Sprig/blob/main/libraries/sprig-gradle/README.md) for extension options, discovery order, compatibility and task details. This plugin removes repeated build plumbing; it does not replace Gradle, Loom or Java, and it is not evidence of a productivity advantage over Java.
