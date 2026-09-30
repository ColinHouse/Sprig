# Explicit module facades

A manifest's `exports = ["public.spr"]` selects module paths that external
packages may import. A declaration export selects names exposed by a module:

```sprig
import "./internal.spr" as internal

export internal.Widget
export internal.create
```

Imports come first, then exports, then local declarations and statements.
`export` is contextual in this declaration form; existing identifiers named
export remain legal in variables, parameters, fields and other expressions.
Exported names retain their simple name. Classes, enums, variants, functions and
already-importable top-level values are supported. The underlying declaration
and symbol retain their identity: a facade does not copy a mutable value,
create another class, wrap a function or change initialization order.

Duplicate visible names, local/alias collisions, missing members and Java
member targets are errors. Cyclic imports/reexports are rejected by the existing
module graph loader. Chains follow the original declaration in dependency
order; API discovery never executes module code.

An exported public facade can expose selected declarations from its own internal
modules. Consumers still cannot import those internal paths directly. Neither
facades nor API queries bypass the manifest boundary.

`sprig api public.spr --json` includes reexported declarations and values with
`reexported: true` and `originModule` relative to the facade. `--member` uses the
same names and signatures as local declarations.

There are no wildcard exports, export-import statements, export renaming,
implicit reexports or new visibility modifiers:

```text
export *                              # unsupported
export import "./internal.spr"         # unsupported
export internal.Widget as PublicWidget # unsupported
```
