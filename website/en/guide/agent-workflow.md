# Working with AI assistants

Sprig was designed with AI coding assistants in mind. Compiler errors carry a stable code, an exact location and a hint for the fix, and they're also available as JSON. An assistant can act on them directly instead of guessing what a paragraph of text means.

To be clear about what that does and doesn't buy you: it doesn't guarantee that AI-written code is correct. What it does give you is that when something goes wrong, both you and the assistant can see where and why, and every fix stays easy for a person to review.

## Ask the installed compiler first

Different SDK versions support different things. Have the assistant learn about the compiler it actually has, rather than writing code from memory:

```sh
sprig version
sprig capabilities --json
sprig help language --json
sprig api java.time.LocalDate --json
```

`capabilities` lists what this SDK really implements. Keep in mind that code which parses isn't necessarily fine at every later stage. Type checking, Java generation and running on the JVM each count only once you've actually run them, for every stage your change depends on.

If you (or the assistant) are new to Sprig, start with `sprig help language`. It's a complete small program that uses `if/elif/else`, loops, variables, functions and standard input. Each topic also lists the built-in methods and shows an example program. Habits from Python, Java or C, such as `else if`, `readLine()` or `List<Int>`, get an error that tells you how Sprig writes it.

## A loop for fixing errors

1. Read the relevant reference page and the existing tests nearby. If you're working in the Sprig repository, read `AGENTS.md` first.
2. Write a small program that shows the problem and run `sprig check --json path/to/file.spr`.
3. When there's an error, read its code, its location, the expected and actual types, and the hint. If it's still unclear, run `sprig explain <code> --json`.
4. Work out what the error means before changing anything. The compiler won't choose a lossy conversion for you, and it won't relax its rules for a Java API just because you need it.
5. Run `sprig test --json` (if your SDK has that command) and the relevant tests. When contributing to the Sprig repository, run the full contributor check as well. Finally, read through the change yourself and make sure no generated files are part of it.

The [agent tooling reference](/en/reference/tooling/agent-guide) explains each JSON field, and every error code is listed in [diagnostic codes](/en/reference/tooling/diagnostic-codes).

## Example: fixing a type error

Assign a string to an integer:

<<< @/snippets/tutorial/type_error.spr

The error from `sprig check --json` includes these fields (the rest are left out here):

```json
{
  "code": "SPR-TYPE-ASSIGN",
  "phase": "TYPE",
  "message": "Type mismatch in initializer",
  "expectedType": "Int",
  "actualType": "String",
  "relatedHelp": "types"
}
```

`expectedType` and `actualType` spell it out: an `Int` is needed here, but a `String` was given. How to fix it depends on what you meant: either change the value to an integer or declare the variable as a `String`. Don't add a conversion just to make the build pass.

The tutorial has several deliberate mistakes like this one, starting with [step 1](/en/tutorial#_1-values-and-types), and the docs check verifies every one of them. The same goes for new Sprig features: add programs that should pass and programs that should be rejected, so the tests prove that the compiler really refuses the wrong code.

## How well does AI write Sprig?

The maintainer has tried this workflow with a lower-cost coding model. It was an early experiment: no controlled comparison, no Java baseline, no productivity measurement. So it doesn't show that Sprig suits AI better than Java does, or that AI writes Sprig more correctly or faster.

The idea the project wants to test is narrower: stable error codes, a queryable Java API and a feature inventory make every step of fixing an error something you can check. You're welcome to try the workflow above yourself. When you send feedback, please include the SDK version, the smallest source that shows the problem, the command you ran and the full error output.

## Where to go next

- [Tutorial](/en/tutorial)
- [Feature status for the release and the source](/en/reference/language/feature-status)
- [Testing projects](/en/reference/tooling/testing)
- [Contributing](/en/project/contributing)
