# Useful SDK showcases

Run each project from its own directory with the SDK's absolute launcher path.
All three have `sprig.toml`, generated reproducible `sprig.lock`, and a default
fixture, so `sprig resolve` followed by `sprig run` produces a useful result.

| Project | What it does | Main boundary |
|---|---|---|
| `examples/showcases/repository_audit` | Sorted source-tree inventory, physical/code/comment/blank line counts by extension, whitespace/large-file findings and optional JSON output | UTF-8 files and paths |
| `examples/showcases/maven_slug` | Creates an HTML article with a title and stable ASCII slug; Commons Text supplies capitalization and HTML entity escaping | Exact Maven Commons Text 1.12.0, transitive Commons Lang |
| `examples/showcases/source_analyzer` | Lexes/parses the frontend probe subset, traverses closed recursive AST variants, emits function/binding outline, name references and diagnostics | UTF-8 source input |

For example, inside `repository_audit`:

```sh
sprig resolve
sprig check
sprig run -- fixtures/tree report.json
sprig run -- /path/to/your/repository
```

The auditor's five source modules contain over 200 lines of application code,
using explicit generics, snapshots, recursive walking, checked Int aggregation,
closed findings/match, and recursive JSON. It counts recognized text extensions,
skips generated/dependency directories and symlinks, caps directory depth at 64,
and propagates IO failures. Comment counting is a line-based heuristic rather
than a parser; `characters` counts UTF-16 code units. Output paths are relative
with `/` separators for portable reports. Exit failure means IO/usage failure;
findings are advisory rather than a CI policy engine.

Inside `maven_slug`:

```sh
sprig resolve
sprig api org.apache.commons.text.StringEscapeUtils --json
sprig check
sprig run -- 'sprig & THE jvm' article.html
sprig run --offline
```

No manually managed classpath or Maven CLI is needed. `resolve` downloads the
locked artifacts; subsequent offline run verifies cache bytes. Capitalization
and HTML escaping come from a real mature library; the intentionally simple
ASCII slug algorithm is Sprig. Third-party String return values are explicitly
narrowed for null before constructing output.

`source_analyzer` defaults to `fixtures/function.spr` or accepts one source path
after `--`. Its frontend module is copied from the tested stage-1 probe with the
fixture-driving top level removed. It recognizes that probe's documented small
subset, **not the complete production Sprig grammar**. The new outline walker
covers every Expr/Stmt case exhaustively. Diagnostics are displayed as analysis
results; they do not change the utility exit status.

`python3 scripts/test-showcases.py` checks defaults, JSON output, whitespace
findings, malformed source diagnostics and Maven API/offline reuse. The same
script accepts `--sdk /path/to/extracted-sdk` to exercise the actual archive.
See each project's README for exact default output and extension ideas.
