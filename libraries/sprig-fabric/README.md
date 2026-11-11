# Sprig Fabric starter

Fabric/Loom is a useful host-framework stress test: it owns game mappings,
client/server source sets, dependency resolution, Java compilation and the mod
jar. `sprig-gradle` reads that real source-set classpath and handles Sprig
generation, the mod's own copy of the runtime, the narrow Java bridge and
`check` integration.

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
applies `dev.sprig` and selects `main`, the common source set, so the Sprig
code is visible to the server-side item registration and to the client entry
point alike; Fabric dependencies, Java release and source-set membership stay
visible as normal Loom configuration. There are no consumer-defined Sprig
tasks or manually assembled classpaths.

## Template layout

| Path | Role |
|---|---|
| `src/main.spr` | Sprig counter state implementing `CounterActions`, and the `Wand` item class that extends `Item` (`conform Wand to Item(properties) as parent`). |
| `tests/counter.spr` | Sprig unit test run by Gradle `check`. |
| `src/sprigBridge/java/` | Small Java interface consumed by Sprig. |
| `src/main/java/` | Fabric main initializer registering the Sprig wand (`Registry.register` stays in Java). |
| `src/client/java/` | Fabric client initializer and tick callback. |
| `src/main/resources/` | Mod metadata and the wand's model and name. |
| `sprig.toml`, `sprig.lock` | Explicit project and dependency state. |

`./gradlew check` statically checks the Sprig source and runs its tests. `build`
generates and compiles Java plus runtime and bridge sources before Loom packages
the mod. `runClient` uses the same generated source set. Generated Java stays
under `build/generated/sprig/main/java/` for inspection.

## Your mod's package, and other Sprig mods

Fabric loads every mod in one class loader, so two mods must not have a class
with the same name. Everything Sprig adds to your jar is therefore in a package
named after your project: the generated classes in `sprig.user.<project>`, and
the mod's own copy of the runtime in `sprig.user.<project>.runtime`. The
template's project is `sprig-fabric-counter`, so its Java entry points call
`sprig.user.sprig_fabric_counter.$M_main`.

- **Rename the project, then the package in Java.** After you change `name` in
  `sprig.toml`, `javac` reports the two calls in `SprigItems.java` and
  `SprigCounterClient.java` that still use the old package.
  `./gradlew sprigInfo` prints the new one.
- **Give each mod its own project name.** Two mods with the same `name` would
  get the same package.
- **Mods talk to each other through Java types.** Each mod has its own runtime
  classes, so one mod's Sprig `List` is not another's; pass `String`, numbers
  or a Java interface both mods know.

In the Sprig repository, `python3 tests/fabric/check_template.py --run-client
--with-example fabric_waypoints` starts a client with the starter and the
waypoint example installed together.

## Top-level `let` and `var` in a mod

The Java entry points call into Sprig without starting anything first:
`SprigItems` calls `$M_main.fn$new_wand(...)`, and later Minecraft calls the
wand's `use`. No generated `main` runs in the game. The first call from Java
runs the top-level statements of `main.spr`, after those of the modules it
imports, and once. A `let` or `var` at the top of a module therefore has its
value in every function and method of the mod, as it has under `sprig test`:

```sprig
let cooldown_ticks = 20
```

- **Call into Sprig from the mod's initializer**, as the template does. The
  statements then have run before the game's other threads call the mod; a
  call from another thread does not wait for statements that are still
  running.
- **A top-level statement that throws** fails the call that started it, and
  Fabric reports it for that entry point. The statements are not run again.
- **Read top-level state through a Sprig function**, not as a Java field such
  as `$M_main.cooldown_ticks`: reading a field does not run the statements.

[Gradle integration](../../docs/jvm/gradle.md#calling-sprig-code-from-java)
has the complete rules.

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
