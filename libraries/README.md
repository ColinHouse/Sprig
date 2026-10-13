# First-party libraries

`libraries/` contains first-party Sprig ecosystem packages. A package may be
pure Sprig, Sprig with a Java kernel, or host-tooling integration. Read the
package guide before adding a dependency; examples progress from the standard
library through application packages.

## Application building blocks

| Package | Purpose |
|---|---|
| [`sprig-csv`](sprig-csv/README.md) | Text-only CSV tables with explicit delimiters and headers, UTF-8 files and contextual errors. |
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

Packages are source distributions reached by path or Git. The repository's
`registry/` directory is the default package registry: `sprig search` lists
released libraries and `sprig add NAME` adds one as a Git dependency at its
released tag. Use Maven coordinates for third-party JVM dependencies.
The v0.8.0-beta.1 SDK includes the released first-party packages and their documented
examples. They remain experimental Beta APIs; check each package guide and the
installed SDK's capabilities before adopting them.

`sprig-csv` is available from this source checkout by path; it is not yet
in a published SDK or registry release. See its package guide and runnable
[`examples/csv`](../examples/csv/README.md) project.
