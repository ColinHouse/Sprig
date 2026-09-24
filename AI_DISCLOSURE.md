# AI-assisted development

Sprig is built with substantial AI assistance **and** human review. This
document states how that works, so contributors and users can judge the
project's claims for themselves.

## How AI is used

- AI coding agents have participated in language design discussions, the
  implementation of the Java stage-0 compiler and runtime, test authoring,
  documentation drafting, and multiple independent review rounds.
- AI agents can run the build, the test suite and the acceptance scripts and
  report the results. They do not hold merge, release or repository
  administration rights.
- Earlier review, acceptance and repair reports from those rounds are kept in
  the maintainer's local archive. They are historical working documents, not
  project policy, and they described issues that may since have been fixed.

## Maintainer responsibility

- A human maintainer sets the project direction, reviews changes, and decides
  what is merged and what is released.
- AI-generated code is treated exactly like any other contribution: it must
  pass the build, the test suite and human review before it enters the
  repository.
- Disagreements between the design kit (`spec/`) and the implemented compiler
  are resolved explicitly and recorded in `docs/`, not papered over.

## Quality gates

A change is not accepted because a model produced it or because it looks
plausible. At minimum:

```bash
./scripts/build.sh
./scripts/test.sh
ANTLR_JAR="$PWD/tools/antlr-4.13.2-complete.jar" ./tools/test-grammar.sh
```

must pass, and behavior claims must distinguish parser acceptance, static
checking, `javac` success and JVM runtime behavior. Documentation examples are
executed by `tools/verify-doc-snippets.py`; they are not review-by-eyeball
snippets.

## Contributing with AI assistance

AI-assisted contributions are welcome when they meet the same gates:

- Describe significant AI assistance in the pull request (which tool, what it
  generated, and what you changed).
- State what you personally verified — commands run, tests passed, cases
  checked by hand.
- Do not present generated prose or code as authority. Point to the grammar,
  the specification, the diagnostic codes or a regression test.
- Do not let an agent bypass a failing gate by weakening an assertion, deleting
  a case or editing a golden output. Fix the code or explain why the
  expectation was wrong.

## What this project does not claim

- No percentage of the code was authored by any model; the project does not
  track that number and will not invent one.
- No part of the project is developed fully automatically. Every merged change
  has human review.
- Passing tests is evidence, not proof. The
  [known limitations](/reference/KNOWN_LIMITATIONS) list states what is not
  covered.
