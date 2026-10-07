# todo: HTTP todo API over SQLite

A todo list stored in SQLite (`libraries/sprig-sqlite`) behind a small HTTP API
(`libraries/sprig-web`). Both are local path dependencies in `sprig.toml`;
they resolve from this examples/ directory.

```sh
sprig resolve                   # downloads sqlite-jdbc once
sprig check
sprig run                       # todos.sqlite, port 8080
sprig run -- /tmp/t.sqlite 9000 # explicit database and port
sprig test                      # store_test (SQLite), api_http_test (real HTTP requests through sprig-http)
```

Routes:

| Method | Path | Result |
|---|---|---|
| GET | `/todos` | JSON array of `{"id","title","done"}` |
| POST | `/todos` with `{"title": "..."}` | 201 and the new todo; 400 when the body has no text title or the title is blank |
| POST | `/todos/{id}/done` | 200 and the todo; 404 when unknown |
| DELETE | `/todos/{id}` | 204; 404 when unknown |

```sh
curl -s -X POST localhost:8080/todos -d '{"title":"milk"}'
curl -s localhost:8080/todos
curl -s -X POST localhost:8080/todos/1/done
curl -s -X DELETE localhost:8080/todos/1
```

Modules: `src/store.spr` (SQL), `src/api.spr` (routes and JSON),
`src/main.spr` (arguments, server start). `tests/api_http_test.spr` starts the
app on port 0 in-process and drives it with real HTTP requests through `sprig-http`.
