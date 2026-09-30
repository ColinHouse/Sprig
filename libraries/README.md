# First-party libraries

`libraries/` contains first-party Sprig ecosystem packages. A package may be
pure Sprig, Sprig with a Java kernel, or host-tooling integration. The tree is
intentionally flat; each package owns its API and focused verification.

| Package | Purpose |
|---|---|
| [`sprig-cli`](sprig-cli/README.md) | Explicit command-line option parsing and usage text. |
| [`sprig-http`](sprig-http/README.md) | Small synchronous JDK HTTP/HTTPS client. |
| [`sprig-json-codec`](sprig-json-codec/README.md) | Path-aware JSON decoding and encoding over `@std/json`. |
| [`sprig-sqlite`](sprig-sqlite/README.md) | Typed SQLite access with explicit SQL and transactions. |
| [`sprig-web`](sprig-web/README.md) | Synchronous localhost HTTP server and explicit OpenAPI metadata. |
