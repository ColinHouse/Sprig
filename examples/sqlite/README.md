# Persistent SQLite from Sprig

This project imports the [thin SQLite package](../../libraries/sprig-sqlite/README.md).
Its manifest resolves pinned SQLite JDBC transitively through that package.

From this directory, after building the compiler:

```bash
../../bin/sprig resolve
../../bin/sprig deps --json
../../bin/sprig api org.sqlite.JDBC --json --offline
../../bin/sprig check --offline
../../bin/sprig run --offline -- notes.sqlite
../../bin/sprig run --offline -- notes.sqlite
```

Output grows from `persisted notes=1` to `persisted notes=2` on a fresh file.
This is real file persistence across separate JVM processes. SQL is visible in
`src/main.spr`; user text uses a typed prepared-statement parameter. No driver
JAR is downloaded by check/run. Run resolve explicitly before offline reuse.
Generated database and local-path lock files are ignored. Delete only your own
example database when you intentionally want a fresh demonstration.
