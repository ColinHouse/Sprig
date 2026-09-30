# Application examples

Start with the [executable beginner tutorial](/en/tutorial). This page groups useful programs by learning progression. Tutorial source lives in `website/snippets/`, independent projects in `examples/`, and regression fixtures in `tests/`.

## Start with local programs

- [Task Tracker](https://github.com/ColinHouse/Sprig/tree/main/examples/task-tracker): local JSON CLI, file I/O and a typed model; no network service.
- [json_select](https://github.com/ColinHouse/Sprig/tree/main/examples/json_select): multi-file JSON CLI using `sprig-cli`.
- [config_summary](https://github.com/ColinHouse/Sprig/tree/main/examples/config_summary): summarizes a small JSON configuration with deterministic output and malformed-input evidence.
- [agent_tools](https://github.com/ColinHouse/Sprig/tree/main/examples/agent_tools): Java/Sprig API queries, diagnostic summaries and API diffs.

## JVM applications and libraries

- [application_foundation](https://github.com/ColinHouse/Sprig/tree/main/examples/application_foundation): HTTP, JSON codec, UTC time and files.
- [sqlite](https://github.com/ColinHouse/Sprig/tree/main/examples/sqlite) and [sqlite_migrations](https://github.com/ColinHouse/Sprig/tree/main/examples/sqlite_migrations): Maven JDBC, transactions, persistence and migrations.
- [ledger](https://github.com/ColinHouse/Sprig/tree/main/examples/ledger): a reduced accounts/transactions HTTP backend with restart persistence.
- [mini_web](https://github.com/ColinHouse/Sprig/tree/main/examples/mini_web): typed routes, JSON and OpenAPI.

Third-party libraries still use ordinary Maven coordinates and lockfiles. See the [first-party library directory](https://github.com/ColinHouse/Sprig/tree/main/libraries) and the [JVM interoperability guide](/en/guide/jvm-interop).

## Compiler and ecosystem cases

- [repository_audit](https://github.com/ColinHouse/Sprig/tree/main/examples/showcases/repository_audit): walks a repository and writes a JSON report.
- [maven_slug](https://github.com/ColinHouse/Sprig/tree/main/examples/showcases/maven_slug): locks a Maven library, queries its API and reuses the cache offline.
- [source_analyzer](https://github.com/ColinHouse/Sprig/tree/main/examples/showcases/source_analyzer): analyzes a supported source subset through the Sprig frontend API.
- [test_runner](https://github.com/ColinHouse/Sprig/tree/main/examples/test_runner): runtime, table, temporary-file, subprocess and expected-diagnostic tests in an ordinary Sprig project.

The [Fabric/Loom dogfood](/en/guide/fabric) is a separate, demanding framework integration case study showing host-owned classpaths and the narrow Java-adapter boundary.

Examples cover a finite set of tested scenarios; they do not imply support for every library API or production workload. Each project README lists commands, lockfiles and known boundaries.
