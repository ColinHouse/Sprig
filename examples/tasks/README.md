# tasks: todo-list CLI over a JSON file

Subcommands over `tasks.json` (`{"tasks": [{"id": 1, "title": "...", "done": false}]}`),
parsed with `libraries/sprig-cli` (local path dependency) and read/written
with `@std/json` and `@std/json_codec`.

```sh
sprig resolve
sprig check
sprig run -- add Buy milk        # title words are joined
sprig run -- list                # open tasks; --all / -a includes done ones
sprig run -- done 1
sprig run -- remove 1
sprig run -- --file other.json list
sprig run -- --help
sprig test                       # tasks_test (model + JSON), app_test (subcommands)
```

Errors (unknown command, bad id, malformed JSON) print `tasks: <message>` and
exit with status 2; `sprig run` then also prints its own `SPR-PROGRAM-EXIT`
notice. Modules: `src/tasks.spr` (model, JSON codec, operations),
`src/app.spr` (`run(arguments) -> Int`, usable from tests), `src/main.spr`.
