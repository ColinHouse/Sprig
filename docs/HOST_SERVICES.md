# Explicit host services

The ordinary [standard modules](STANDARD_LIBRARY.md) now wrap files, paths,
process arguments/environment, text/time and recursive JSON. See that page
for the current public Sprig contract and `docs/SHOWCASES.md` for applications.

The following legacy frontend methods remain compatible.

`sprig.runtime.host.HostFiles` is a small Java platform boundary for a future
Sprig-written frontend. It contains no compiler language semantics. Methods:

| Java signature | Purpose | Sprig boundary |
|---|---|---|
| `readUtf8(String) throws IOException -> String` | UTF-8 source input | Reference result is treated nullable; check before use. |
| `writeUtf8(String, String) throws IOException -> void` | UTF-8 output | `Unit`; catch/declare `IOException`. |
| `fileExists(String) -> boolean` | Regular-file test | `Bool`. |
| `canonicalPath(String) throws IOException -> String` | Resolve a real path | Reference result is treated nullable. |
| `listFiles(String) throws IOException -> java.util.List<String>` | Sorted path snapshot | Opaque Java list; no implied Sprig `List[String]` adapter. |

These methods are queried with `sprig api sprig.runtime.host.HostFiles --json`.
They are not auto-imported or intrinsic. Source paths supplied by the caller
are explicit. This frontend boundary is limited to IO/path services; the stage-1 lexer,
layout, parser, AST, symbols, diagnostics and pretty printer belong in Sprig.
The `listFiles` generic boundary requires an explicit copy adapter before it
can become a typed Sprig collection. No new grammar was added for this API.
