# Sprig Fabric Counter

This is a minimal client-only Fabric/Loom project with Sprig-owned counter
state. Set `SPRIG_HOME` to the root of an extracted Sprig SDK, then run:

```sh
./gradlew check
./gradlew build
./gradlew runClient
```

`check` runs Sprig static checking and the tests in `tests/`. `build` compiles
the Sprig-generated Java, runtime and bridge as part of the Loom source set.
Generated Java is kept under `build/generated/sprig/client/java/`.

See the SDK's `libraries/sprig-fabric/README.md` for the verified version
combination and compatibility limits.
