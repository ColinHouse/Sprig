# `sprig test` dogfood project

From this directory in a source checkout that implements `sprig test`:

```sh
../../bin/sprig resolve
../../bin/sprig test
../../bin/sprig test --json
```

Five ordinary `.spr` files exercise assertion-based runtime tests,
table-driven cases, an isolated temporary file, argv process capture and an
expected nullability diagnostic. The string positions case mirrors the
existing compiler runtime oracle without replacing it. See the
[testing contract](../../docs/tooling/testing.md) for the runner and expectation rules.
