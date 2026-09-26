# Stage-1 host services (v0.2.0-alpha.1)

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
are explicit. The host is limited to IO/path services; the stage-1 lexer,
layout, parser, AST, symbols, diagnostics and pretty printer belong in Sprig.
The `listFiles` generic boundary requires an explicit copy adapter before it
can become a typed Sprig collection. No new grammar was added for this API.
