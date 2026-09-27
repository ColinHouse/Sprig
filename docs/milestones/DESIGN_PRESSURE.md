# Design pressure from the v0.3 showcases

This record measures current source; it does not authorize a language redesign.
The three projects are repository_audit (232 lines, five modules), maven_slug
(53 lines, Commons Text/Lang) and source_analyzer (636 lines, including the
existing 541-line frontend probe). They use ordinary checking and JVM execution.

| Pressure | Concrete evidence | Smallest response |
|---|---|---|
| Standard-library discovery | 11 relative std imports in the three showcase projects | Reserved `@std` package imports; no grammar change |
| Conservative JVM nullability | 11 explicit non-null guards in std and two in maven_slug | Keep guards and typed wrappers; no hidden non-null assumption |
| Java generic collection boundary | One directory-list indexed snapshot copied into a typed Sprig list in files.list | Small host adapter; retain explicit copy cost |
| Generics | Auditor uses explicit Bucket/String and FileStats abstractions | Working expressiveness; no evidence requiring inference/variance |
| AST traversal | Recursive variants and exhaustive match in the copied frontend plus analyzer driver | Useful today; probe remains a subset, not self-hosting |
| IO/effects | Filesystem wrappers expose java.io.IOException; command drivers declare/handle effects | Document actual boundaries before any effect redesign |

No showcase needs a user-supplied callback, shared behavioral interface,
async contract, matrix/array syntax, or a match expression to complete its task.
This is absence of evidence in these programs, not proof such abstractions are
unnecessary. A later mixed Java/Sprig mini-web experiment should measure SAM
callbacks, JVM generics and effects before proposing syntax. A small VS Code
extension is the next adoption project after release; formatter and stage-1
remain separate work. Do not add DSL routes, interfaces or inference in this gate.

The actual usability failure was moving a showcase outside the repository:
relative paths depended on repository depth. Installed `@std` fixes that boundary
and a standalone temporary-directory regression verifies it.
