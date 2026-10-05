# Agent workflow

Sprig is for people and coding agents. The goal is to make implementation evidence, failure locations and repair decisions inspectable, not to promise that an agent always writes correct code. **Agent-friendly should also mean review-friendly.**

## Query the compiler you have

SDK versions and capabilities change. Ask the installed tools first:

```sh
sprig version
sprig capabilities --json
sprig help language --json
sprig api java.time.LocalDate --json
```

`capabilities` is the feature inventory for that SDK. Parser acceptance alone does not establish static semantics, Java generation or JVM execution; test every stage your change relies on.

## Start from a small failure

1. Read `AGENTS.md`, the relevant reference page and nearby tests.
2. Write a small positive program and run `sprig check --json path/to/file.spr`.
3. On failure, read the stable code, source range, expected/actual types and repair hint. Then run `sprig explain SPR-CODE --json`.
4. Change source only after deciding what the repair means. The compiler will not choose a lossy conversion or expand API support for the agent.
5. Run `sprig test --json` when the installed SDK reports it, focused independent tests and the contributor gate. Review the diff and generated-file status.

See the [agent tooling reference](/en/reference/tooling/agent-guide) for JSON fields and version boundaries, and the [diagnostic catalog](/en/reference/tooling/diagnostic-codes) for stable codes.

## Repair a type error

Assigning a string to an integer reports `SPR-TYPE-ASSIGN` with `expectedType: Int` and `actualType: String`. Keep that machine-readable evidence. Depending on intent, change the value to an integer or declare the binding as `String`; do not add a conversion solely to make the build pass.

The tutorial's [expected-failure snippet](/en/tutorial#_1-values-and-types) is checked by the docs gate. New semantics should have both positive and negative programs so tests prove rejection as well as acceptance.

## Limits of early dogfooding

The maintainer reports trying a Sprig workflow with a lower-cost coding model. This is an early anecdote, not a controlled benchmark: there is no equivalent Java control, public task set or productivity measurement. It does not show that Sprig beats Java, makes agent output more correct or produces a measured speed-up.

The narrower product hypothesis is that stable type errors, Java API queries and a capability inventory give repair work checkable information. External users can reproduce the workflow above. Please include the SDK version, minimal source, command and full diagnostic with feedback.

## Continue

- [Beginner tutorial](/en/tutorial)
- [Published release and source feature status](/en/reference/language/feature-status)
- [Project tests](/en/reference/tooling/testing)
- [Sprig contribution guide](/en/project/contributing)
