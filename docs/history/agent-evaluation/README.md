# Historical acceptance evidence

This directory preserves the raw artifacts of the **blind alpha.2 trials**
(compiler `0.1.0-alpha.2`). They are historical evidence, not an active suite:

- `alpha2/` — ten scripted tasks with the original programs,
  first-check JSON diagnostics, run stdout/stderr, repaired variants and one
  extension-host run.
- `alpha2-round2/` — a second round with work fixtures (modules,
  Maven/JAR interop, match, numeric and IO), an API snapshot, `commands.log`
  and `discovery.log`.

The artifacts are intentionally unreferenced: no CI workflow, script, test
runner or release gate executes them, and their recorded paths and compiler
version are from the alpha.2 era. They are kept because they document actual
agent behavior at that time. Do not wire them into the active gates and do not
delete them without a maintainer decision; if they ever need to be trimmed,
keep at least the task programs and one representative log per round.

Current, runnable acceptance checks live in `../../../tests/acceptance/cases/` and `../../../tests/acceptance/scripts/`;
their generated output goes to `../../../tests/acceptance/results/` (gitignored).
