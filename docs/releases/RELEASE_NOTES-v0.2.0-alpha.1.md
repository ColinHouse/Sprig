# Sprig v0.2.0-alpha.1 — development draft (NOT RELEASED)

Compiler metadata is `0.2.0-alpha.1`, language metadata is `0.8-dev`.
The latest published prerelease remains `v0.1.0-alpha.1`. Local SDK archives
are development candidates, not published release assets.

## Implemented and independently exercised

- `generic T:` and `generic K, V:` around one class, variant or function;
  explicit complete arguments, invariant types, expanded variant payloads,
  exhaustive match, strict nullable positions and erased/boxed JVM lowering.
- Leading `requires T: Equatable` permits value equality; `Comparable` and
  user capabilities are unsupported. No inference, variance, traits or HKT.
- `sprig.toml`, init/discovery, named binaries, project/deps JSON metadata.
  Strict manifest validation and refusal of unresolved project dependencies.
- SDK generic/project/dependency docs and truthful Agent capability metadata.
- Independent repairs to variant argument soundness, nested generic collection
  retrieval/iteration, chained cast grouping, and module variant construction.

## Release blockers

No dependency resolver, lockfile reproducibility, local/Git dependency imports,
Maven resolution or offline dependency cache exists. Stage-1 remains a
single-file frontend probe plus a separate generic data-structure experiment;
it has not migrated into the requested multi-module project. Those required
release capabilities must be implemented and audited before language `0.8`
metadata, a `v0.2.0-alpha.1` tag or GitHub prerelease is appropriate.

See the independent audit report for commands, findings and exact candidate
CI evidence. No implicit Java collection conversion is provided.
