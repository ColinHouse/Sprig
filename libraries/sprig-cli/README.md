# sprig-cli

An experimental Sprig module for declarative CLI option parsing and stable
usage text. It does not interpret shell strings or guess value types.

```toml
[[dependency]]
name = "cli"
path = "../../libraries/sprig-cli"
```

Define `OptionSpec(name, short_name, kind, description)` values, where `kind` is
`OptionKind.FLAG` or `OptionKind.VALUE`, and pass them, with
`process.arguments()` (after `import "@std/process.spr" as process`), to
`cli.parse(arguments, specs)`. A parsed result
exposes the methods `value(name) -> String? throws Error` and
`flag(name) -> Bool throws Error` and the field `positionals: List[String]`.
The closed `OptionValue` variant distinguishes
flags from values. Long flags (`--verbose`), short flags (`-v`), separate and
equals values (`--output path`, `--output=path`), positionals, and `--` are
supported. Duplicate, unknown, missing, and malformed arguments fail with an
explicit `Error`. `cli.usage(program, summary, specs)` renders options in the
declared order.

See the multi-file [json-select example](../../examples/json_select/README.md)
and `python3 tests/cli_library/check_cli_library.py`.
