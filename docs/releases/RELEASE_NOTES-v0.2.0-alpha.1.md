# Sprig v0.2.0-alpha.1 — development draft (NOT RELEASED)

This file is a draft for a future release. **No `v0.2.0-alpha.1` tag, release
or downloadable archive exists.** The latest published prerelease is
`v0.1.0-alpha.1`.

## Intended content

- v0.8 single-parameter user generics: `generic T:` blocks, explicit `[Type]`
  application, generic variants with expanded payloads, strict arity, block
  scope, Practical Strict nullable arguments, and erased/boxed JVM lowering.
- `sprig help generics` and capabilities flags reporting the generics,
  constraints, inference, variance and project-system status.
- Compiler metadata `0.2.0-alpha.1`, language `0.8-dev`.

## Known gaps that must close before a release

- The `sprig.toml` project system, `sprig.lock` reproducibility and
  local/Git/Maven dependency resolution are **not implemented**.
- Capability implications for `requires T: Comparable|Equatable` are parsed
  but not enforced.
- Multiple type parameters and inference are rejected by design.
- A v0.2.0-alpha.1 archive has not been packaged, checksummed and smoke-tested
  from this tree yet; `scripts/package-alpha.sh` builds a local candidate only.

See `docs/post-v0.7/GENERICS_IMPLEMENTATION_REPORT.md` for the implementation
and validation record.
