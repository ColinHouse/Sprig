# agent-tools: Sprig-written consumers of compiler JSON

Three small command-line tools written in Sprig that consume the compiler's own
machine-readable output. They depend only on `@std` and the Sprig CLI library.

```sh
sprig resolve
sprig run --bin api-report -- snapshot.json
sprig run --bin diag-summary -- check.json
sprig run --bin api-diff -- before.json after.json
```

- `api-report` renders deterministic Markdown from `sprig api <module|.> --json`.
- `diag-summary` groups saved `check/build/run --json` diagnostics by severity,
  phase and stable code.
- `api-diff` compares two saved API snapshots and reports added, removed and
  changed public signatures. The comparison is intentionally textual and simple.

All three accept `-o FILE` to write their output and `-h` for usage. The shared
`src/meta.spr` module contains the explicit JSON walking helpers.
