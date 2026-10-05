# Example programs

Once you've done the [tutorial](/en/tutorial), these fuller programs are a good next step. They all live in the repository's `examples/` directory, and each one's README explains how to run it, what it depends on and what its limits are.

## Start with small local tools

- [Task Tracker](https://github.com/ColinHouse/Sprig/tree/main/examples/task-tracker): a command-line task list that keeps its data in a local JSON file. It shows file I/O and a typed data model, with no network access.
- [json_select](https://github.com/ColinHouse/Sprig/tree/main/examples/json_select): a command-line tool for picking fields out of JSON, split across several files, using the `sprig-cli` library for options.
- [config_summary](https://github.com/ColinHouse/Sprig/tree/main/examples/config_summary): reads a small JSON configuration file and prints a summary in a fixed format, with clear errors for bad input.
- [agent_tools](https://github.com/ColinHouse/Sprig/tree/main/examples/agent_tools): three small tools written in Sprig that read the compiler's JSON output to query Java and Sprig APIs, summarize errors and compare APIs.

## Applications that use Java libraries

- [application_foundation](https://github.com/ColinHouse/Sprig/tree/main/examples/application_foundation): HTTP, JSON encoding and decoding, UTC time and file handling.
- [sqlite](https://github.com/ColinHouse/Sprig/tree/main/examples/sqlite) and [sqlite_migrations](https://github.com/ColinHouse/Sprig/tree/main/examples/sqlite_migrations): JDBC through Maven, with transactions, persistence and database migrations.
- [ledger](https://github.com/ColinHouse/Sprig/tree/main/examples/ledger): a compact bookkeeping HTTP backend whose data survives a restart. See [Web and SQLite](/en/guide/web-sqlite).
- [mini_web](https://github.com/ColinHouse/Sprig/tree/main/examples/mini_web): typed routes, JSON and OpenAPI documentation.

Third-party Java libraries are added with ordinary Maven coordinates and pinned by the lock file; see [projects](/en/guide/projects) and [JVM interop](/en/guide/jvm-interop). The libraries Sprig maintains itself are in the [`libraries/`](https://github.com/ColinHouse/Sprig/tree/main/libraries) directory.

## Bigger programs

- [repository_audit](https://github.com/ColinHouse/Sprig/tree/main/examples/showcases/repository_audit): walks a code repository and writes a JSON report.
- [maven_slug](https://github.com/ColinHouse/Sprig/tree/main/examples/showcases/maven_slug): locks a Maven library, queries its API and reuses the cache offline.
- [source_analyzer](https://github.com/ColinHouse/Sprig/tree/main/examples/showcases/source_analyzer): a source analysis tool written in Sprig that walks a syntax tree and lists functions, variables and references. It covers part of the language only and isn't a full compiler API.
- [test_runner](https://github.com/ColinHouse/Sprig/tree/main/examples/test_runner): all kinds of tests in an ordinary Sprig project, including runtime tests, table tests, temporary files, subprocesses and tests that are expected to fail compilation.

[Fabric mods](/en/guide/fabric) is a separate case study: game logic in a Minecraft mod written in Sprig, with Gradle and Loom handling the build and only a thin layer of Java.

These examples cover the scenarios that have been tested. They don't mean every library API or production workload is supported.
