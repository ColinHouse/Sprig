# json-select

A multi-file command-line tool written in Sprig. It reads a UTF-8 JSON file,
selects one top-level object member, and writes the JSON value to stdout or an
output file. `--input FILE` / `-i FILE` and `--key NAME` / `-k NAME` are
required. `--output FILE` / `-o FILE` and `--verbose` / `-v` are optional.

```sh
sprig resolve
sprig run -- --input sample.json --key account --output account.json
sprig run -- -i sample.json -k account -v
sprig run -- --help
```

The parser supports long flags, long valued options, `--name=value`, one-letter
short options, positional arguments, and `--` as the option terminator. It does
not parse shell strings. Any repeated option is an error, including one given
once by its short name and once by its long name. Unknown options and missing
values produce explicit errors. The parser preserves option strings as supplied
and never converts arbitrary values dynamically.
