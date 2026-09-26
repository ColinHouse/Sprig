# Scoped contribution issues

These public contracts target the v0.3 source milestone. Each states problem,
behavior, non-goals, areas, acceptance commands and semantic constraints. Check
current open/closed status before starting; pick an `agent-friendly` issue,
read AGENTS.md, run verify, review the patch, then submit a PR.

| Issue | Scope |
|---|---|
| [Point manifest semantic errors at the offending field](https://github.com/ColinHouse/Sprig/issues/13) | agent-friendly, tooling |
| [Make inherited JVM field metadata ordering deterministic](https://github.com/ColinHouse/Sprig/issues/14) | agent-friendly, compiler |
| [Add inherited and bridge-method JVM query fixtures](https://github.com/ColinHouse/Sprig/issues/15) | good first issue, agent-friendly, tests-only |
| [Require explicit executable versus import-only doc snippet roles](https://github.com/ColinHouse/Sprig/issues/16) | good first issue, agent-friendly, tooling |
| [Add a small ordinary Sprig text.join utility](https://github.com/ColinHouse/Sprig/issues/17) | good first issue, agent-friendly, stdlib |
| [Add explicit JSON object lookup helpers](https://github.com/ColinHouse/Sprig/issues/18) | agent-friendly, stdlib |
| [Add independent adversarial JSON boundary fixtures](https://github.com/ColinHouse/Sprig/issues/19) | good first issue, agent-friendly, tests-only |
| [Add explicit ISO UTC timestamp parsing and formatting](https://github.com/ColinHouse/Sprig/issues/20) | agent-friendly, stdlib |
| [Fail Git cache lock contention after a bounded wait](https://github.com/ColinHouse/Sprig/issues/21) | agent-friendly, tooling |
| [Attach generated arithmetic source spans to runtime numeric diagnostics](https://github.com/ColinHouse/Sprig/issues/22) | agent-friendly, compiler |
| [Add a fixture-driven configuration summary example](https://github.com/ColinHouse/Sprig/issues/23) | good first issue, agent-friendly, docs |
| [Decide a portable identity contract for local dependency locks](https://github.com/ColinHouse/Sprig/issues/24) | design-required, tooling |
| [Add exact native Windows SDK argument-forwarding regressions](https://github.com/ColinHouse/Sprig/issues/25) | agent-friendly, tests-only, tooling |
| [Cover classified transitive Maven artifact identities](https://github.com/ColinHouse/Sprig/issues/26) | good first issue, agent-friendly, tests-only |

`design-required` authorizes discussion, not an implementation by accident.
Only small reviewed fixtures/docs/utilities are marked `good first issue`.
