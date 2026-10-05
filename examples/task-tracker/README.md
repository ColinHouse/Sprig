# Task Tracker CLI

A small, local-first task list that demonstrates project layout, a typed task
model, command-line arguments, JSON decoding and encoding, and UTF-8 file I/O.
It uses no network service. Data lives in `tasks.json` in the current working
directory.
Set `SPRIG_TASKS_FILE` to use another local path (also useful for isolated
tests).

From this directory, with Sprig installed:

```sh
sprig resolve
sprig run -- add "Read the Sprig tutorial"
sprig run -- add "Build a small tool"
sprig run -- list
sprig run -- done 1
sprig run -- list
```

`done` writes through the standard library's UTF-8 helper. `@std/json.spr`
rejects malformed JSON, and `@std/json_codec.spr` reads each required field
with its type. Unexpected input is reported with the place it was found, such
as `$[0].id: expected integer, found string`, rather than silently replaced.
This is a teaching example, not a concurrent database: parallel writers and
large data sets need a database or a locking policy.
