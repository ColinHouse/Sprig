# Web and SQLite development milestone

The current source checkout contains `libraries/sprig-web`,
`libraries/sprig-sqlite`, `examples/mini_web`, `examples/sqlite` and
`examples/ledger`. This is development work beyond the published SDK, not a
production framework or a newly published release.

## Run the backend

From the repository root after building:

```bash
cd examples/ledger
../../bin/sprig resolve
../../bin/sprig check --offline
../../bin/sprig run --offline -- ledger.sqlite 8080
```

Open `http://localhost:8080/docs` for Swagger UI and `/openapi.json` for typed
OpenAPI metadata. The Swagger page uses pinned CDN assets and needs network
access. The backend itself binds loopback and needs no network after dependencies
are resolved and cached. Stop with Ctrl+C. VS Code offers **Sprig: Run in Terminal**
for persistent programs; JSON run buffers until completion.

Use GET/POST `/api/accounts`, GET/POST `/api/categories`, GET/POST
`/api/transactions`, DELETE `/api/transactions/{id}`, and GET
`/api/statistics/monthly?month=2026-09`. Money is signed Int minor units.
The project README specifies request fields, persistence and validation.

## Ordinary typed Sprig APIs

Route handlers have source type `fn(web.Request) -> web.Response`. `App.get`,
`post` and `delete` register handlers; `App.route` accepts an ordinary named
`Route` constructor with explicit summary and schemas. There are no decorators.
Use module functions `web.text(body,status)` and `web.json_response(value,status)`;
`Response.text` is not a static Sprig API.

`req.path_param(name)`, `query(name)` and `header(name)` return String?, preserving
absence versus empty strings. `req.body` is UTF-8 text; `req.json()` produces the
closed JSON Value variant. Malformed input becomes400, missing route404 and
unhandled application failure a controlled500. `json.find_member` distinguishes
Missing, Found(value) and NotObject without truthiness or Any.

Schemas use explicit Schema/FieldSchema/QueryParameter values, not arbitrary
class reflection. They describe the API; handlers still validate request values.
Registration rejects duplicate query metadata and conflicting template paths.
See the repository `libraries/sprig-web/README.md` for behavior policy; resolved
signatures come from `sprig api @web/app.spr --json` after `sprig resolve`.

## SQL stays visible

The SQLite package resolves `org.xerial:sqlite-jdbc:3.46.1.0` through the existing
Maven Resolver and schema-4 lock. Data parameters are closed Integer/Text/Boolean/
Null cases. SQL structure is trusted application code; values use prepared
parameters. Queries return typed detached snapshots and adapters close JDBC
resources and roll back failed query snapshots or batches. No ORM, arrays,
implicit REAL-to-Int conversion, BLOB or Decimal binding is introduced.

## Limits and verification

This synchronous single-user example has no auth, sessions, migrations, pooling,
async or public deployment. Bodies and result snapshots are held in memory.
Swagger CDN availability is separate from offline backend behavior.
The repository tests use actual HTTP, temporary SQLite files, restart persistence
and structural OpenAPI assertions:

```bash
python3 tests/callables/check_callables.py
python3 tests/web/check_web.py
python3 tests/sqlite/check_sqlite.py
./scripts/verify.sh
```

The source report lives at `docs/history/milestones/WEB_SQLITE_ENGINEERING_REPORT.md`.
Compiler query surfaces explain [function types](/en/guide/language-tour) and
[JVM callable boundaries](/en/reference/jvm/interop).
