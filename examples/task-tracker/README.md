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

`done` writes through the standard library's UTF-8 helper. The JSON
decoder rejects malformed JSON and the program validates each required field;
unexpected input is reported rather than silently replaced. This is a teaching
example, not a concurrent database: parallel writers and large data sets need a
database or a locking policy.
