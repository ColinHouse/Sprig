# sprig-json-codec

Path-aware JSON decoding and encoding helpers for ordinary Sprig applications,
built directly on `@std/json.spr`. It removes the mechanical
`find_member`/`match` field extraction that every application otherwise
rewrites, while leaving all application rules explicit.

```sprig
import "@std/json.spr" as json
import "@json-codec/codec.spr" as codec

let root = codec.root(json.parse(text))
let schema = codec.required_int(root, "schema")
let name = codec.required_string(root, "name")
let enabled = codec.required_bool(root, "enabled")
let active = codec.optional_string(root, "active_project")
codec.reject_unknown_fields(root, ["schema", "name", "enabled", "active_project"])
```

## Why this exists

A real Fabric Build Board dogfood spent roughly 180 lines in `persistence.spr`
on the same shape: check the root is an object, look up each field, match every
`json.Value` variant, distinguish missing from `null`, report the field name,
then mirror the whole thing for encoding. None of that is application policy,
but none of it is expressible by `@std/json` alone either. This library is that
missing middle layer.

## What it does NOT do

- No implicit coercion: `"12"` is not an integer, `12.5` is not an integer, and
  no number becomes a string.
- No automatic codec derivation: there is no reflection, annotation, macro or
  compile-time generation.
- No business validation: positivity, id syntax, uniqueness, schema versions
  and cross-field rules stay in application code.
- No Any/dynamic objects, no JSON Schema language, no streaming, no
  YAML/TOML, no mapping/ORM layer.
- It does not change `@std/json.spr`; `Value`, `Lookup`, `parse` and
  `stringify` keep their existing semantics.

## Missing versus null

`field(reader, name)` returns a small variant:

```sprig
variant Field:
    Missing
    Null
    Value(value: json.Value)
```

- `Field.Missing`: the key is absent.
- `Field.Null`: the key is present with JSON `null`.
- `Field.Value`: the key is present with any other value.

`required_*` reports those states differently (`required field is missing` vs
`expected string, found null`). `optional_*` maps both missing and present
`null` to Sprig `null`; use `field` when the application must tell them apart.

## Numeric policy

- `required_int` accepts only a plain JSON integer lexeme
  (`-?(0|[1-9][0-9]*)`) that fits Sprig `Int`. `12.5`, `1e3`, `"12"`, `+12`,
  `007` and out-of-range values are errors with the field path.
- `required_decimal` accepts any valid JSON number lexeme and parses it exactly
  with `Decimal.parse`; it never rounds.
- `required_number_text` returns the exact source lexeme so the application can
  feed `Decimal`, `BigInt` or its own numeric model without a lossy hop.

## Unknown fields

Unknown fields are allowed unless the application asks otherwise:
`codec.reject_unknown_fields(reader, allowed)` rejects any key outside the
explicit list and also detects duplicate keys in manually constructed values.
(`json.parse` already rejects duplicate keys while parsing.)

## Error path format

Every diagnostic starts with the deterministic JSON path:

```text
$.name: expected string, found number
$.name: required field is missing
$.name: expected string, found null
$.projects[1].targets[0].amount: expected integer, found string
$.projects[0].targets[0].amount: required field is missing
$.meta: expected array, found object
$.items[0]: expected object to read field 'name', found number
$: unknown field 'extra'
$: duplicate field 'a'
```

Paths grow as `$.field`, `$.field[index]`, `$.a.b[0].c`; array element readers
carry their index path automatically. Errors are raised as Sprig `Error`
(`throws Error`), so ordinary `try`/`catch` handles them and messages are
stable for tests and agents.

## Reading nested data

There is no generic combinator (Sprig has no generic methods and lambdas cannot
carry checked effects). Arrays return a `List[Reader]` whose elements already
carry `$.field[index]` paths, so an explicit loop stays short and precise:

```sprig
let root = codec.root(json.parse(text))
for project in codec.required_array(root, "projects"):
    let id = codec.required_string(project, "id")
    for target in codec.required_array(project, "targets"):
        let amount = codec.required_int(target, "amount")
        # application rule with an exact path in its message:
        if amount <= 0:
            throw Error(target.path + ".amount: must be positive")
```

## Encoding

Small helpers keep construction uniform:

```sprig
codec.text("ada")            # json.Value.Text
codec.bool(true)             # json.Value.Boolean
codec.int(42)                # json.Value.Number with an exact integer lexeme
codec.number("1.25")         # exact number lexeme
codec.member("name", value)  # json.Member
codec.object(members)        # json.Value.Object
codec.array(values)          # json.Value.Array
```

Encoding adds no policy: the application builds `json.Value` and calls
`json.stringify`. `tests/encoding.spr` and `tests/build_board_config.spr`
verify encode -> stringify -> parse -> decode round trips.

## Simple example

```sprig
let root = codec.root(json.parse("{\"name\": \"ada\", \"enabled\": true}"))
print(codec.required_string(root, "name"))   # ada
print(codec.required_bool(root, "enabled"))  # true
```

## Nested example

```sprig
for project in codec.required_array(root, "projects"):
    let id = codec.required_string(project, "id")
    let targets = codec.required_array(project, "targets")
    print(id + ": " + targets.size().toString())
```

## Relationship to @std/json

`@std/json` is the exact low-level data model and parser: `Value`, `Lookup`,
`find_member`, `parse`, `stringify`. `sprig-json-codec` is higher-level
application policy that consumes those types; it deliberately does not live in
`@std/json` so it can evolve at library speed.

## Future source generation

The public surface is a set of small functions over `json.Value`, so a future
`sprig codec generate` tool could emit *calls to this same API* from an
application-owned data declaration without changing the API or the runtime
dependency. That tool is a speculative convenience: the library tests and the
Build Board fixture show the remaining repetition is loops plus application
rules, and nothing in this repository requires generation today. No automatic
derivation is promised.

## Package use

Consumer `sprig.toml`:

```toml
[[dependency]]
name = "json-codec"
path = "../libraries/sprig-json-codec"
```

Then `import "@json-codec/codec.spr" as codec`. The library is pure Sprig (no
Java), exports `codec.spr`, and its own tests run with `sprig test`:

```text
sprig resolve --offline
sprig test --json
```
