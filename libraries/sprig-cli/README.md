# sprig-cli

An experimental Sprig module for declarative CLI option parsing and stable
usage text. It does not interpret shell strings or guess value types.

```toml
[[dependency]]
name = "cli"
path = "../../libraries/sprig-cli"
```

Define `OptionSpec(name, short_name, kind, description)` values and pass them,
with `std.process.arguments()`, to `cli.parse(arguments, specs)`. A parsed
result exposes `value(name) -> String?`, `flag(name) -> Bool`, and
`positionals() -> List[String]`. The closed `OptionValue` variant distinguishes
flags from values. Long flags (`--verbose`), short flags (`-v`), separate and
equals values (`--output path`, `--output=path`), positionals, and `--` are
supported. Duplicate, unknown, missing, and malformed arguments fail with an
explicit `Error`. `cli.usage(program, summary, specs)` renders options in the
declared order.

See the multi-file [json-select example](../../examples/json_select/README.md)
and `python3 tests/cli_library/check_cli_library.py`.
