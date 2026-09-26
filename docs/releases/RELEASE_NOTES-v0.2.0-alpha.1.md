# Sprig v0.2.0-alpha.1 — development draft (NOT RELEASED)

This file is a draft for a future release. **No `v0.2.0-alpha.1` tag, release
or downloadable archive exists.** The latest published prerelease is
`v0.1.0-alpha.1`.

## Intended content

- v0.8 multi-parameter user generics: `generic K, V:` blocks, explicit `[Type]`
  application, generic variants with expanded payloads, strict arity, block
  scope, Practical Strict nullable arguments, and erased/boxed JVM lowering.
- `sprig help generics` and capabilities flags reporting the generics,
  constraints, inference, variance and project-system status.
- Project dependency system: deterministic `sprig resolve`/`sprig.lock`,
  local path and Git dependencies locked to exact revisions, package-local
  `@alias/module.spr` imports with `exports` enforcement, cycle and traversal
  protection, `--offline` builds for cached dependencies.
- Compiler metadata `0.2.0-alpha.1`, language `0.8-dev`.

## Known gaps that must close before a release

- Maven/JVM dependency resolution is **not implemented**: declaring `[[jvm]]`
  fails with `SPR-DEP-MAVEN`, and third-party jars need explicit
  `--classpath`. There is no project-aware JVM classpath yet.
- Leading `requires T: Equatable` is implemented; Comparable and user-defined
  capabilities remain unsupported.
- Generic inference and variance are rejected by design.
- A v0.2.0-alpha.1 archive has not been packaged, checksummed and smoke-tested
  from this tree yet; `scripts/package-alpha.sh` builds a local candidate only.

See `docs/post-v0.7/V08_VALIDATION_REPORT.md` for the implementation
and validation record.
