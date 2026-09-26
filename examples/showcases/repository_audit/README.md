# Repository audit

A five-module Sprig CLI for source-tree inventory. From this directory, use the
SDK launcher (absolute path or PATH):

```sh
sprig resolve
sprig check
sprig run
sprig run -- fixtures/tree report.json
sprig run -- /path/to/repository /path/to/report.json
```

Default output:

```text
Sprig repository audit
files=3 directories=2 lines=11 code=6 skipped=0
.md: files=1 lines=3
.spr: files=1 lines=4
.java: files=1 lines=4
findings=0
```

The JSON report includes ordered per-file metrics and advisory findings.
Whitespace, tab indentation and files above 400 physical lines are reported.
Supported extensions: `.spr`, `.java`, `.md`, `.txt`, `.toml`, `.json`, `.py`, `.sh`.
Generated directories and symlinks are skipped. IO errors fail execution.
Line/comment detection is deliberately heuristic; see `docs/SHOWCASES.md`.

Modules separate measurements, generic buckets/data, tree walking, exhaustive
finding/JSON reporting and CLI arguments. Try adding a new extension or a
fixture-backed finding without changing language syntax.
