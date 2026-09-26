# Sprig v0.2.0-alpha.1

Second public prerelease. Compiler `0.2.0-alpha.1`, language `0.8-dev`, JDK 17+.

## Highlights

Explicit multi-parameter generics, local/Git dependency resolution, and an
Agent SDK with offline language/JVM discovery. This remains a Java stage-0
compiler that emits Java and invokes javac, not a self-hosted compiler.

## Generics

```sprig
generic K, V:
    class Entry:
        let key: K
        let value: V
```

One or more explicit type parameters, explicit generic calls/constructors,
generic variants, Practical Strict nullable arguments, invariant generics,
and a leading `requires T: Equatable` capability. JVM lowering is erased/boxed.
No type argument inference, variance, Comparable or user-defined capabilities.
See [generics](../GENERICS.md).

## Projects and dependencies

```toml
[[dependency]]
name = "math"
git = "https://example.invalid/math.git"
branch = "main"
```

```sprig
import "@math/vector.spr" as vector
```

`sprig.toml`, `sprig resolve`, schema-2 `sprig.lock`, package-local aliases,
local paths, Git branch intent locked to exact SHA, exports enforcement and
offline cached builds. This illustrative URL must be replaced with a real
Sprig package. See [projects](../PROJECTS.md) and [dependencies](../DEPENDENCIES.md).

## Agent/JVM tooling

`help`, `capabilities`, `api`, `doctor`, structured `explain`, stable codes and
JSON diagnostics. Third-party JVM JARs use explicit `--classpath` on
api/check/build/run; Java reference results remain conservatively nullable.

## Reliability fixes

Generic JVM lowering, numeric widening in generic/collection positions and
generic variant soundness; scoped lockfile edge identity, symlink confinement,
Git checkout byte/mode integrity checks and concurrent atomic cache installation.
Tests exercise the parser, static checker, generated Java and JVM execution separately.

## Known limitations

Maven/JVM dependency resolution and project-aware Maven classpaths are not
implemented. The stage-1 frontend remains a probe. Comparable, inference,
variance, interfaces/traits, publishing/registry and LSP/IDE are not implemented.
Local locks use absolute paths; runtime/manifest diagnostic spans are limited.
Type safety does not guarantee numerical stability. See [limitations](../KNOWN_LIMITATIONS.md).

## Installation

Download `sprig-v0.2.0-alpha.1-jdk.zip` and its `.sha256` from the
[GitHub prerelease](https://github.com/ColinHouse/Sprig/releases/tag/v0.2.0-alpha.1).
Verify SHA-256, extract, then run `bin/sprig version`, `capabilities --json`,
`check examples/hello.spr` and `run examples/hello.spr`. JDK 17+ is required;
the archive includes compiler/runtime and ANTLR, not a JDK. The package includes
LICENSE/NOTICE and third-party provenance. See the
[validation record](https://github.com/ColinHouse/Sprig/blob/main/docs/post-v0.7/V08_VALIDATION_REPORT.md).
