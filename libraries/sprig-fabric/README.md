# Sprig Fabric starter

Fabric/Loom is a useful host-framework stress test: it owns game mappings,
client/server source sets, dependency resolution, Java compilation and the mod
jar. `sprig-gradle` reads that real source-set classpath and handles Sprig
generation, runtime sources, the narrow Java bridge and `check` integration.

The component is a starter and convention, not a replacement Fabric API or a
Sprig import package. It intentionally avoids UI, widget, inventory and
Minecraft API abstractions.

## Quick start

Extract the Sprig SDK and set `SPRIG_HOME` to its root. The template's
`settings.gradle` includes `libraries/sprig-gradle` from that SDK; it does not
reference this repository or a sibling checkout.

```sh
cp -R "$SPRIG_HOME/libraries/sprig-fabric/template" ./my-mod
cd my-mod
./gradlew check
./gradlew build
./gradlew runClient
```

The template is the complete, version-pinned project. Its `build.gradle`
applies `dev.sprig` and selects `client`; Fabric dependencies, Java release and
source-set membership stay visible as normal Loom configuration. There are no
consumer-defined Sprig tasks or manually assembled classpaths.

## Template layout

| Path | Role |
|---|---|
| `src/main.spr` | Sprig counter state and implementation of `CounterActions`. |
| `tests/counter.spr` | Sprig unit test run by Gradle `check`. |
| `src/sprigBridge/java/` | Small Java interface consumed by Sprig. |
| `src/client/java/` | Fabric client initializer and tick callback. |
| `src/main/resources/fabric.mod.json` | Client-only mod metadata. |
| `sprig.toml`, `sprig.lock` | Explicit project and dependency state. |

`./gradlew check` statically checks the Sprig source and runs its tests. `build`
generates and compiles Java plus runtime and bridge sources before Loom packages
the mod. `runClient` uses the same generated source set. Generated Java stays
under `build/generated/sprig/client/java/` for inspection.

## Verified version combination

The maintained starter is exercised with Minecraft 26.3, Fabric Loader 0.19.5,
Fabric API 0.161.0+26.3, Fabric Loom 1.18.2, Gradle 9.7.1, and OpenJDK 26.0.1
using Java release 25. The template centralizes these versions in
`gradle.properties`. This verifies one combination; it does not promise that
all Fabric or Loom versions are supported.

Sprig's lock is checked in and never rewritten by `check` or `build`. Loom may
download its normal host dependencies when Gradle is online; the plugin always
runs Sprig check, generation and tests offline against the current lock. Use
the explicit `sprigResolve` task only when the Sprig project dependencies need
resolution.

## Boundary and limits

Java owns Fabric callbacks and framework entrypoints; Sprig owns counter state
and behavior. The bridge is a small, explicit Java interface implemented with
Sprig `conform`. Arbitrary wildcard signatures or Minecraft UI APIs are not
automatically rewritten. Use `sprig api` to inspect a Java surface and retain a
narrow Java adapter for unsupported JVM shapes.

The earlier Quest Board Java-vs-hybrid A/B was one uninstrumented project, not a
controlled productivity benchmark; Java-only was preferred while the hybrid
build required manual Gradle/Loom wiring. This starter removes that identified
setup cost so the architecture can be reevaluated. It is not evidence that
Sprig is more productive than Java.
