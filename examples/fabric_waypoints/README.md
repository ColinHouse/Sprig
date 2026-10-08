# fabric_waypoints: a Fabric mod written in Sprig

A Waypoint Marker item for Minecraft 26.3. Use it, and it saves where you
stand under the marker's name. Sneak and use it, and it lists the world's
waypoints, nearest first:

```text
Saved Home at 120, 66, -310 (overworld).
3 waypoints, nearest first:
  Home: 14 blocks north-east, 2 up
  Mine: 340 blocks west, 41 down
  Waypoint 1: in the_nether
```

Rename the marker in an anvil to choose the name. An unnamed marker saves
`Waypoint 1`, `Waypoint 2` and so on, and a marker whose name is already taken
(in any case) moves that waypoint. Each world keeps its waypoints in
`sprig_waypoints.json` in its save folder, one waypoint per line, so you can
read the file or fix it by hand while the world is closed.

## Build the mod

The project is the [Fabric starter](../../libraries/sprig-fabric/README.md)
with a different mod in it: Loom plus the `dev.sprig` Gradle plugin, the same
pinned versions and the same Gradle wrapper. Point `SPRIG_HOME` at a Sprig SDK
or at a built checkout of this repository, then build:

```sh
export SPRIG_HOME="$(cd ../.. && pwd)"   # in a checkout, from this directory
./gradlew build                          # Sprig check and tests, javac, the mod jar
ls build/libs                            # sprig-waypoints-0.1.0.jar
./gradlew runClient                      # Minecraft with the mod; find the marker under Tools & Utilities
```

The plugin reads `sprig.lock` and never rewrites it. An extracted SDK leaves
example locks out, so run `sprig resolve` there first.

## Test the Sprig part without Minecraft

Everything except the glue runs on a plain JVM:

```sh
sprig resolve
sprig test                    # rules, compass, the JSON file, both actions
sprig check src/marker.spr    # marker.spr imports every module but the glue
```

`sprig check` without a file checks `src/main.spr`, which imports Minecraft
classes, so outside Gradle it stops at `SPR-JVM-CLASS`. Gradle's `sprigCheck`
and `sprigTest` pass Loom's classpath instead.

In this repository, `tests/examples/check_dogfood_programs.py` runs these
tests in the default gate, and `python3 tests/fabric/check_examples.py`
builds the mod with Loom (add `--offline` when `~/.gradle` already has
Minecraft and Fabric).

## What is Sprig and what is Java

| Path | Language | What it does |
|---|---|---|
| `src/waypoints.spr` | Sprig | The model and the rules: names, saving, moving |
| `src/compass.spr` | Sprig | Distance, eight-point direction and the list's lines |
| `src/store.spr` | Sprig | The JSON file: strict reading, atomic writes (`@std/json_codec`, `@std/files`) |
| `src/marker.spr` | Sprig | The two actions, over a file path and a position |
| `src/main.spr` | Sprig | The glue: the item class (it extends `Item`), registration, the creative tab entry |
| `src/main/java/dev/sprig/waypoints/SprigWaypoints.java` | Java | The Fabric entrypoint, one call into Sprig |
| `src/main/resources/` | JSON | `fabric.mod.json`, the item model, English name and tooltip |

Only `main.spr` imports Minecraft or Fabric classes, and the dogfood test
checks that it stays that way.

The entrypoint is the one Java class, because `fabric.mod.json` names it by
class and the classes Sprig generates (`sprig.user.$M_main` and so on) are
compiler output, not names to publish in mod metadata. `javac` checks its call
into Sprig at build time.

Three things you meet when you write the glue yourself:

- `Registry.register` with a `ResourceKey` or `Identifier` declares
  `<V, T extends V>`, a bound Sprig's Java interop rejects
  (`generic-bound-unsupported` in `sprig api`). The `String` overload works with
  a written type argument:
  `Registry.register[Item](BuiltInRegistries.ITEM, id.toString(), item)`.
- Fabric events take listeners through `Event<T>.register(T)`. A Sprig `fn`
  value is rejected there (`SPR-JVM-MEMBER`), so the creative tab listener is a
  small class with `conform TabEntry to ModifyOutput`.
- The tooltip is item lore set on the item's `Properties`.
  `Item.appendHoverText` would work as an override, but Minecraft 26.3 marks
  it deprecated, and only `javac` says so, in a note about the generated Java.

## Known limits

- Nothing here starts the game. The tests run the Sprig rules and the file
  format on a JVM, and `./gradlew build` shows the glue compiles against
  Minecraft 26.3; try the item itself with `./gradlew runClient`.
- It builds against one combination, the starter's: Minecraft 26.3, Fabric
  Loader 0.19.5, Fabric API 0.161.0+26.3, Loom 1.18.2 and Gradle 9.7.1.
- There is no remove action yet. Move a waypoint by reusing its name, or edit
  the file while the world is closed.
- Waypoints belong to the world, shared by every player on it.
- Chat lines are English text from Sprig; the item name and tooltip are
  translatable (`assets/sprig_waypoints/lang/en_us.json`).
- The marker borrows the vanilla empty map texture.
- Every Sprig mod jar carries the Sprig runtime (`sprig/runtime/`) and its
  generated classes in the shared `sprig.user` package. This mod and the
  starter both contain `sprig/user/$M_main.class`, so two Sprig mods in one
  game have classes with the same names. That combination is untested.
