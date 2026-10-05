# First-party libraries

`libraries/` contains first-party Sprig ecosystem packages. A package may be
pure Sprig, Sprig with a Java kernel, or host-tooling integration. Read the
package guide before adding a dependency; examples progress from the standard
library through application packages.

## Application building blocks

| Package | Purpose |
|---|---|
| [`sprig-cli`](sprig-cli/README.md) | Explicit command-line option parsing and usage text. |
| [`sprig-json-codec`](sprig-json-codec/README.md) | Path-aware JSON decoding and encoding over `@std/json`. The implementation is the bundled `@std/json_codec.spr`; the package reexports it for existing dependents. |
| [`sprig-http`](sprig-http/README.md) | Small synchronous JDK HTTP/HTTPS client. |

## Persistence and server integration

| Package | Purpose |
|---|---|
| [`sprig-sqlite`](sprig-sqlite/README.md) | Typed SQLite access with explicit SQL and transactions. |
| [`sprig-web`](sprig-web/README.md) | Synchronous localhost HTTP server and explicit OpenAPI metadata. |

## Host build integration

| Component | Purpose |
|---|---|
| [`sprig-gradle`](sprig-gradle/README.md) | Java Gradle plugin for classpaths, bridge compilation, generated/runtime sources and Sprig tests. |
| [`sprig-fabric`](sprig-fabric/README.md) | Small Fabric/Loom starter using the SDK-bundled Gradle plugin. |

Packages are local source distributions today. Use Maven coordinates for
third-party JVM dependencies; Sprig does not yet provide a public registry.
The v0.5.0-beta.1 SDK includes these first-party packages and their documented
examples. They remain experimental Beta APIs; check each package guide and the
installed SDK's capabilities before adopting them.
