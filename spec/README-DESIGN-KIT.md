# Historical Sprig v0.7 — language design kit

This directory is the **design kit** for the Sprig v0.7 language: the proposed
language contract, agent-facing design decisions, the planned JVM interop
rules, a quick reference, and the proposed agent tool protocol. The documents
here describe target semantics; they are not all implemented and are not the
implementation status.

The working implementation lives in the repository root: the Java stage-0
compiler in `compiler/`, the runtime in `runtime/`, the authoritative grammar
in `grammar/`, and the implementation status in
`docs/FEATURE_STATUS_IMPLEMENTED.md`. The grammar smoke harness is
`tools/test-grammar.sh` with sources in `tools/grammar-harness/`.

## Current implementation

Current implemented behavior is documented in the root [implementation status](../docs/FEATURE_STATUS_IMPLEMENTED.md)
and [quick reference](../docs/QUICK_REFERENCE.md). The sole v0.8 generic contract is
[docs/GENERICS.md](../docs/GENERICS.md): multiple explicit parameters and Equatable
are implemented; inference, variance and Comparable are not.

## What is new in v0.7

- `variant Expr: Literal(value: Int) ...` — a sealed sum type with immutable
  payloads, intended to express AST nodes without nullable field bags.
- Exhaustive `match` **statement** with `case Expr.Literal as node:`. The
  semantic checker must flag missing/duplicate/wrong cases; ANTLR only parses
  the shapes.
- Imports appear before declarations or statements; positional and named
  arguments are separate syntactic alternatives and never mix in one call.
- No pipeline, implicit truthiness, user-defined overloads, magic imports,
  reflection-driven codegen or automatic null-to-nonnull conversion.

## Read in this order

1. `docs/QUICK_REFERENCE.md` for a compact target example.
2. `docs/LANGUAGE_SPEC.md` for the proposed language contract.
3. `docs/AGENT_FRIENDLY_DECISIONS.md` and `docs/JVM_INTEROP.md` for design
   rationale and interop intent.
4. The root `grammar/*.g4` for syntax as implemented.
5. `docs/AGENT_TOOL_PROTOCOL.md` for proposed (not implemented) tooling.

## Status and compatibility

v0.7 is a proposal, not a backward-compatible release. The implementation may
support less than this kit describes, and where they differ the implemented
behavior is documented under the root `docs/`. Treat the design examples as
target-syntax samples, not as runnable programs, unless the implementation
status says otherwise.
