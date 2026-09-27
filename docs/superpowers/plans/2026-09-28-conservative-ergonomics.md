# Conservative ergonomics implementation plan

**Goal:** deliver comment-preserving formatting, explicit declaration facades and exhaustive expression matches, in that order.
**Architecture:** retain trivia in lexer tokens, keep semantic layout/AST clean; resolve facades to original symbols; share match validation and lower expression branches without changing evaluation order.
**Spec:** user conservative ergonomics milestone attachment, 2026-09-28.
**Constraints:** no spec/ edits, no unrelated syntax, no config, preserve SDK/Unicode/API changes and existing diagnostics; three independent feature commits.

## Formatter
- [ ] Establish current d04a885 baseline using `./scripts/verify.sh`.
- [ ] Add failing CLI tests for comments, spacing, CRLF, malformed byte preservation and idempotence in `tests/formatter/check_formatter.py`.
- [ ] Preserve COMMENT/SPACE on hidden channel; filter semantic layout input without weakening tab/indent errors.
- [ ] Implement token-based canonical formatting and safe CLI replacement, with existing JSON envelope.
- [ ] Check parser token/AST structure, runtime equivalence and valid repository corpus; test comment-loss mutation.
- [ ] Document/tooling integration and commit `Add trivia-preserving formatter`.

## Re-exports
- [ ] Add failing facade fixtures covering declarations, chains, API, collisions and package boundaries.
- [ ] Add `export alias.Symbol` grammar/AST; resolve original symbols in dependency order before local signatures.
- [ ] Use originating declarations during generation; enrich API origin metadata without execution.
- [ ] Stable diagnostics, facade library dogfood, docs/capabilities, mutation checks, commit `Add explicit module re-exports`.

## Match expressions
- [ ] Add failing cases for strict result types, exhaustive cases, generic/nullability/effects and side-effect counts.
- [ ] Separate Expr.Match; reuse statement-match binder/case checks, contextual branch types, conservative inference.
- [ ] Lower scrutinee and selected branch once with typed Java result and checked effects.
- [ ] Adversarial/metamorphic/mutation checks, real library dogfood, docs/tooling, commit `Add match expressions`.

## Completion
- [ ] Run full verification and inspect diff; record exact evidence and remaining risks in CONSERVATIVE_ERGONOMICS_MILESTONE.md.
