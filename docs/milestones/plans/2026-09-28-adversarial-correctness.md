# Adversarial correctness audit plan

**Goal:** Find and repair reproducible violations of current contracts, with independent evidence.
**Architecture:** Keep the existing frontend, typed AST, Java backend and runtime. Derive attacks from contracts, preserve minimized failures, fix root causes and verify surrounding cases.
**Stack:** Java 17 source target, ANTLR 4.13.2, Python harnesses, existing SDK/Node tooling.
**Scope:** User's adversarial audit request of 2026-09-28; no features, releases, tags, pushes or merges. No spec changes, weakened tests, disabled checks or generated artifacts committed.

- [x] Fetch current default branch, inspect open issues/PRs, record starting SHA.
- [x] Finish baseline canonical verification; distinguish any environmental failure. (Baseline green at 95 gates/31 grammar cases; Windows preview failures are non-blocking and pre-existing, tracked in #33.)
- [x] Audit types/generics, nullable flow, callable erasure, match and generated Java. Preserve baseline failures; add regression before each repair. Run focused and related tests and commit by bug class. (A01, A02, A06, A07, A10, A11; commits `52e53c6`, `222ca11`, `e5d02e2`.)
- [x] Audit modules/reexports, formatter and layout with identity, idempotence, failure-preservation and execution comparisons. (`check_type_names.py`, `check_layout_lines.py`.)
- [x] Audit JVM metadata and overload behavior against independent Java fixtures. (`check_jvm_bridges.py`, independent javac/java oracle.)
- [x] Audit resolver/locks and SDK installation with disposable hostile metadata, cache corruption and failure-preservation checks. (A12 `check_install_failures.py`; existing deps/maven suites cover locks, offline cache and artifact corruption.)
- [x] Exercise library composition and fresh SDK discovery outside the checkout. (`check_sdk_composition.py`.)
- [x] Add bounded seeded generators, metamorphic tests and restored realistic mutations. (`check_properties.py`, identity chains in `check_type_names.py`.)
- [x] Review diagnostics, machine-readable claims, docs, termination and current CI evidence. (`api-no-init`, JSON envelopes, docs updates for A11.)
- [x] Run final canonical verification, inspect patch and working tree, complete report/ledger, commit locally. (`./scripts/verify.sh` 102 gates; `check-sdk-archive.py` passed.)

Evidence lives in `tests/adversarial/regressions/` and the final report at
`docs/milestones/ADVERSARIAL_CORRECTNESS_AUDIT.md`. Temporary reproductions/logs live outside tracked source. A confirmed bug gets an explicit expected value or rejection code; tests must fail on the baseline for the intended reason. Each ledger row records status, severity, subsystem, reproducer, expected/actual behavior, cause, fix, regression, verification and commit. Ambiguous contracts stay design-required.
