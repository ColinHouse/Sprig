# Sprig Fabric Counter

This is a minimal Fabric/Loom project with Sprig-owned state: a client tick
counter behind a narrow Java interface, and a wand item whose behaviour is a
Sprig class that extends `Item`. Set `SPRIG_HOME` to the root of an extracted
Sprig SDK, then run:

```sh
./gradlew check
./gradlew build
./gradlew runClient
```

`check` runs Sprig static checking and the tests in `tests/`. `build` compiles
the Sprig-generated Java, runtime and bridge as part of the Loom `main` source
set, so the client entry point and the item registration both see it.
Generated Java is kept under `build/generated/sprig/main/java/`.

The wand (`class Wand` in `src/main.spr`, `conform Wand to Item(properties) as
parent`) counts its uses, tells the player on the server side and then runs
the inherited `Item.use` through `parent.use(...)`. `SprigItems.java` registers
it; `Registry.register` stays in Java because its generic bound is outside
Sprig's interop profile.

See the SDK's `libraries/sprig-fabric/README.md` for the verified version
combination and compatibility limits.
