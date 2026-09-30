# Fabric / JVM framework case study

This is a demanding JVM framework integration case study, not the whole identity
of Sprig. Start with the [beginner tutorial](/en/tutorial) and [ordinary Java
interop](/en/guide/jvm-interop). This page distills a real Fabric/Loom mod
dogfood into a reusable method:
**the host build system is the dependency and classpath authority, Sprig owns
business semantics, and only JVM shapes Sprig cannot express today get a narrow
Java adapter.**

Fabric is the validated case; the method is not Fabric-specific. Gradle plugins,
Maven libraries and in-house JVM frameworks follow the same split: export the
host classpath → `sprig api`/`sprig wrap` → return generated Java to the host
build → `conform` callbacks → verify the package.

## Verified versions (dogfood facts)

| Component | Measured version |
|---|---|
| Minecraft | 26.3 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.161.0+26.3 |
| Fabric Loom | 1.18.2 (build declares `1.18-SNAPSHOT`) |
| Gradle wrapper | 9.7.1 |
| Sprig dogfood toolchain | 0.4.0-alpha.1 source revision `ede71f7a` (historical Alpha-era integration) |
| Compile/runtime JDK | OpenJDK 26.0.1 (`javac --release 25`, classfile major 69) |

Explicitly **not** verified:

- **No JDK 25 runtime test.** JDK 26 ran the Java 25-targeted mod; that is not a
  separate JDK 25 runtime validation. The mod metadata requires Java 25+.
- The build declares Loom `1.18-SNAPSHOT`; the measured resolution was 1.18.2.
- These are not general Fabric conclusions: the Gradle wiring and
  `fabric.mod.json` entrypoint are Fabric-specific; the rest applies to other
  JVM frameworks.

## Three layers

```text
host build (Gradle/Loom/Maven)
  dependency resolution, classpath, final artifact — the authority
        │ exports the real classpath
Sprig source
  model, state, persistence, command policy, callback implementations
        │ only where a shape is inexpressible
narrow Java adapter
  third-party wildcard builders, inexpressible interfaces — mechanical only
```

Ask `sprig api` first, then decide which layer owns the code. In the dogfood the
model, tick accounting, JSON, file paths and lifecycle callbacks were all Sprig;
Java existed only at the Brigadier command-tree boundary.

## 1. Let the host build export its classpath

Do not hand-maintain Minecraft/Fabric jars. Have the host build write the exact
classpath it compiles with; Sprig commands consume that file:

```groovy
// build.gradle: hand the host-resolved compile classpath to Sprig.
def sprigCompiler = file('<path-to-sprig>/bin/sprig')  // built or installed SDK launcher
def sprigSourceDir = file('src/main/sprig')
def sprigBridgeSourceDir = file('src/bridge/java')
def sprigGeneratedDir = layout.buildDirectory.dir('generated/sprig')
def sprigBridgeClassesDir = layout.buildDirectory.dir('classes/java/sprigBridge')
def sprigClasspathOutput = layout.buildDirectory.file('sprig/compile-classpath.txt')
def sprigMainCompileClasspath = sourceSets.main.compileClasspath + files(sprigBridgeClassesDir)

tasks.register('compileSprigBridge', JavaCompile) {
    inputs.files(fileTree(sprigBridgeSourceDir) { include '**/*.java' })
    classpath = configurations.compileClasspath
    destinationDirectory = sprigBridgeClassesDir
    options.release = 25
    source(fileTree(sprigBridgeSourceDir) { include '**/*.java' })
}

tasks.register('sprigClasspath') {
    dependsOn('compileSprigBridge')
    inputs.files(sprigMainCompileClasspath)
    outputs.file(sprigClasspathOutput)
    doLast {
        def output = sprigClasspathOutput.get().asFile
        output.parentFile.mkdirs()
        output.text = sprigMainCompileClasspath.asPath
    }
}
```

In the Loom case that classpath is `sourceSets.main.compileClasspath` plus the
compiled bridge classes. For another framework the principle is identical:
**export what the host actually compiles with; never configure a second copy.**

## 2. Query the API with the real classpath

```bash
CP="$(cat build/sprig/compile-classpath.txt)"
bin/sprig api net.fabricmc.api.ModInitializer --json --classpath "$CP"
```

`--classpath` uses the same resolution path for `check`/`build`/`run`/`api`/
`wrap`/`doctor` and accepts repetitions or the platform path separator. The JSON
reports `interopLevel`, `interopReasonCodes`, `adaptation` and recursive generic
shapes. For framework callbacks, confirm the signature is concrete and callable
before writing Sprig.

## 3. Import ecosystem classes with `sprig wrap` when needed

Wrap a small surface, not an entire class:

```bash
CP="$(cat build/sprig/compile-classpath.txt)"
bin/sprig wrap <fully.qualified.JavaClass> --member <name> \
  --out src/main/sprig/wrapped.spr --classpath "$CP" --force --json
```

The output is ordinary, editable `.spr`. It refuses to overwrite without
`--force`, and it is checked under the same classpath before it is written;
`--json` reports `generatedMembers`/`skippedMembers` with stable reason codes.
Keep only the members you need and delete the rest: the file is a starting
point, not a runtime dependency. See the
[wrapper contract](/en/reference/jvm/wrap).

## 4. Return generated Java to the host build

`build --emit-java-only` stops after static checking and emits Java without
invoking `javac`; the host compiles generated sources, the Sprig runtime and the
bridge:

> The snippets below continue the same `build.gradle` variable scope as section 1.

```groovy
tasks.register('sprigGenerate', Exec) {
    dependsOn('compileSprigBridge')
    inputs.files(fileTree(sprigSourceDir) { include '**/*.spr' })
    inputs.files(sprigMainCompileClasspath)
    outputs.dir(sprigGeneratedDir)
    doFirst {
        commandLine([sprigCompiler, 'build', new File(sprigSourceDir, 'mod_entry.spr').absolutePath,
            '--emit-java-only', '-d', sprigGeneratedDir.get().asFile.absolutePath,
            '--classpath', sprigMainCompileClasspath.asPath])
    }
}

sourceSets.main.java.srcDir(sprigGeneratedDir.map { it.dir('java') })
sourceSets.main.output.dir(sprigBridgeClassesDir, builtBy: 'compileSprigBridge')

tasks.named('compileJava') { dependsOn('compileSprigBridge', 'sprigGenerate') }
tasks.matching { it.name == 'sourcesJar' }.configureEach {
    dependsOn('compileSprigBridge', 'sprigGenerate')
}
```

Three dogfood-verified wiring points:

1. The generated Java directory is a main source-set source directory compiled by
   the host's `compileJava`.
2. Bridge classes go into both the **compile classpath** and the **runtime
   output** (`sourceSets.main.output`). The first `runClient` failed with
   `NoClassDefFoundError` for exactly this reason: **compile-time visibility is
   not runtime packaging.**
3. Gradle 9 validates implicit dependencies: `sourcesJar` reading the generated
   directory must depend on the generation task explicitly.

Do not trust the first success; re-verify with the clean path (section 7).

## 5. Implement Java callbacks with `conform`

Concrete callback signatures need no adapter. Write a named Sprig class whose
methods match the Java interface, then declare conformance:

```sprig
import net.fabricmc.api.ModInitializer as ModInitializer

class ModEntry:
    func onInitialize() -> Unit:
        print("mod loaded")

conform ModEntry to ModInitializer
```

In the dogfood, the mod entrypoint, server started/stopping, end-tick and player
join/disconnect callbacks all conformed directly. Notes:

- Java reference results are conservatively nullable; event fields must be
  checked before `.register(...)`.
- Static enum-like fields (for example a world resource constant) are treated as
  nullable too.
- `conform` v1 supports Java interfaces only: non-generic source class and
  target interface, no overloaded abstract methods, no renaming or adapters;
  `Short`/`Byte`/`Character` slots are not expressible. See the
  [conformance contract](/en/reference/jvm/conformance).

The Fabric entrypoint class name is `sprig.user.$ModEntry` in
`fabric.mod.json`.

## 6. When to write a narrow Java adapter

Add an adapter only when `sprig api` says the needed member is unusable, and add
only that piece. The dogfood's single boundary was Brigadier:
`ArgumentBuilder.then(ArgumentBuilder<S, ?>)` contains a Java wildcard,
`api` marks it `wildcard-unsupported`, and Sprig cannot express the nested
builder tree. The pattern:

- Java side: a small command-tree builder plus a non-generic action interface.
- Sprig side: command decisions, state and messages stay in a Sprig class that
  `conform`s to that interface.
- Java only translates Brigadier callbacks into interface calls and delivers the
  returned text.

**Do not** rewrite business logic in Java because one shape is restricted. If
the adapter starts growing, go back to `sprig api` for a narrower boundary;
broad wildcard support is a future language-level question, not integration
work.

## 7. Packaging and acceptance checklist

For any host framework:

1. The host exports the classpath it actually compiles with.
2. Generated Java is compiled by the host and its directory is a source dir.
3. The final artifact contains the generated module classes (e.g.
   `sprig/user/**`), `sprig/runtime/**` and the bridge classes.
4. A clean rebuild from scratch succeeds.
5. A real run/package path was exercised once — a passing `javac` is not enough.

Fabric/Loom extras:

```bash
./gradlew clean
./gradlew compileJava          # regenerate and compile from scratch
./gradlew runClient            # enter a singleplayer world; verify callbacks and commands
./gradlew build                # remap the final mod jar
jar tf build/libs/<artifact>.jar | grep -E "sprig/user|sprig/runtime|your/bridge/"
```

The dogfood jar was confirmed to contain `sprig/user/$ModEntry.class`, generated
module classes, bridge classes and `sprig/runtime/**`.

## 8. Dogfood evidence (one project's local measurement)

A real singleplayer vertical slice (create/enter/autosave/quit/re-enter/normal
shutdown/build) passed:

| Metric | Value | Note |
|---|---:|---|
| Handwritten Sprig feature code | 440 lines | model, persistence, entrypoint |
| Handwritten Java glue | 52 lines | one Brigadier builder + one action interface |
| Directly conformed callbacks | lifecycle, tick, join/disconnect | no adapter |
| Narrow wildcard adapters | 1 | Brigadier `then` |
| Mod entrypoint + command registration conformance | direct | — |

These are **local measurements for that project, not a general ratio.** Larger
wildcard-heavy third-party APIs will raise the Java share; treat the numbers as
evidence that this shape works, not as a prediction for arbitrary mods.

## 9. Fabric-specific versus general patterns

| Item | Category |
|---|---|
| `fabric.mod.json` entrypoint, Loom `runClient`/`remapJar` | Fabric-specific |
| Minecraft/Fabric versions and the `--release 25` metadata | Fabric/this dogfood |
| Host build exporting its compile classpath to `sprig api`/`wrap` | General |
| `build --emit-java-only` plus host-compiled sources and runtime | General |
| Bridge classes must reach runtime output and the artifact | General (Gradle/source sets) |
| `conform` to concrete callback interfaces | General |
| Wildcard builder shapes handled by a thin adapter + non-generic interface | General pattern (Brigadier here) |
| `sourcesJar` must depend on generation | General on Gradle 9 |

## 10. Kept limitations

- **Java wildcards / Brigadier builders:** the current interop profile does not
  model wildcard shapes; use a thin adapter or a future, separately designed
  typed helper. `sprig api` tells you first.
- **Java nullable boundary:** Java reference results and static fields are
  conservatively nullable; check them explicitly. This is a safety boundary, not
  a defect.
- **The host build must include generated/runtime/bridge outputs:** compile
  classpath membership is not enough; runtime output and the artifact matter.
- **JDK facts:** this dogfood compiled and ran on JDK 26.0.1 with
  `javac --release 25`; **JDK 25 runtime was not tested.**

Next: read [JVM interoperability](/en/guide/jvm-interop) for type mapping,
adapters, arrays and concrete generics, and [Tooling and JSON](/en/guide/tooling)
for the `api`/`wrap`/`test` JSON envelopes.
