# Reduced single-user SQLite ledger

An experimental local backend, primarily ordinary Sprig modules. Java supplies
HTTP sockets and a narrow JDBC resource/binding adapter. Sprig owns routes,
validation, JSON, schemas, SQL, relationships and error responses.

From this directory, after building:

```bash
../../bin/sprig resolve
../../bin/sprig check --offline
../../bin/sprig run --offline -- ledger.sqlite 8080
```

The database schema initializes atomically on startup. `LEDGER_READY 8080` means
the localhost server is bound. Port `0` allocates an ephemeral port; its actual
value is printed. Stop the run with Ctrl+C. The database persists across restarts.
Use VS Code **Sprig: Run in Terminal** for the persistent program.

Open [Swagger UI](http://localhost:8080/docs) and
[OpenAPI](http://localhost:8080/openapi.json). Swagger assets require CDN network
access; API requests and OpenAPI itself remain local. Set
`cors_origin="http://localhost:5173"` explicitly for a local Vue development server;
this sample uses that origin. There is no authentication or remote listener.

## Routes and data

| Method | Path | Input/output |
| --- | --- | --- |
| GET/POST | `/api/accounts` | Array / create `{ "name": "Savings" }` |
| GET/POST | `/api/categories` | Array / create `{ "name": "Salary" }` |
| GET/POST | `/api/transactions` | Array / create the object below |
| DELETE | `/api/transactions/{id}` | Positive id; 204 deleted, 404 absent |
| GET | `/api/statistics/monthly?month=2026-09` | Count, signed net/income/expense minor units |

```json
{"account_id":1,"category_id":1,"amount_minor":125099,"occurred_on":"2026-09-15","memo":"income"}
```

Money is signed INTEGER minor units: positive income, negative expense; zero
is rejected. No fractional/exponent/coerced monetary input. Dates must be valid
ISO dates. Account/category foreign keys are enforced on every operation.
Required JSON fields use explicit typed lookup; missing, null and wrong kinds
produce 400. Malformed JSON, invalid ids/dates and relationship/uniqueness
violations also produce 400. Internal database failures produce controlled 500.
Monthly sums remain SQLite INTEGER operations; overflow produces 500.

This is deliberately a small single-user example: no users, budgets, currencies,
authentication, pagination, ORM, migrations or production concurrency promise.
Accounts/categories are create/list only. SQL and schemas are written explicitly.
Query snapshots have a 10,000-row adapter limit. Persisted files contain financial
example data under the invoking user's normal permissions.

## Real HTTP verification

`python3 tests/sqlite/check_sqlite.py` from the repository root resolves the
exact driver then uses temporary Unicode/spaced database paths and localhost
port 0. It verifies prepared parameters, NULL/type distinctions, atomic rollback,
create/list/delete, monthly totals, malformed input, OpenAPI, CORS, `/docs`, and a
second JVM reopening the same database. `--offline` requires a warm Maven cache.
The runner terminates the entire compiler/application process group on failures.
Browser interaction with CDN Swagger assets is a separate manual check; serving
its HTML and parsing OpenAPI are the automated evidence.
