# Stage-1 self-hosting roadmap

The compiler is a Java seed, not self-hosted. The Sprig frontend probe in
`examples/stage1_frontend_probe/frontend.spr` reads UTF-8 through HostFiles,
lexes tokens/layout, parses bindings, arithmetic, printing, if blocks and typed
function declarations, records spans/scopes and prints variant ASTs through
exhaustive visitors. `tests/bootstrap` checks golden fixtures, malformed input,
duplicate parameters and added-case exhaustiveness. Calls, full return-type
checking and signature recovery remain outside this subset probe.

`tests/visitor/ast_visitor.spr` and `mini_pipeline.spr` are smaller feasibility
slices. None constitutes a stage-1 compiler.

## Incremental path

1. Keep stage-0 golden programs and independent diagnostics/output as the oracle.
2. Build explicit UTF-8/source-span, Unicode scanning, builder and collection
   utilities; HostFiles is the existing platform boundary, not a language intrinsic.
3. Expand the Sprig lexer/parser and compare token/AST fixtures with stage-0.
4. Add a checker subset, deterministic module/build driver and structured diagnostics.
5. Emit Java for the subset, then compare diagnostics/output with stage-0.
6. Rebuild stage-1 with itself and pass the same acceptance corpus before claiming self-hosting.

## Remaining engineering work

Compiler-grade source abstractions, Unicode iteration, efficient text assembly,
complete symbol/collection utilities, module discovery and recovery are incomplete.
Named recursive visitors work; single-expression lambdas and `var` plus `if`
provide current workarounds. No new syntax is justified without a measured problem.
User generics and file host services already exist, but a complete frontend and
backend still require substantial implementation. See [host services](HOST_SERVICES.md).
