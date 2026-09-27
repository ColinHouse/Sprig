# Sprig coding-agent task pack

Deterministic fixtures for later controlled Coding Agent evaluation. **No model
run has been performed**; only the fixtures, hidden acceptance scripts and
scoring runner exist here.

```sh
# Score a submission tree overlaid on each task's initial files:
python3 tests/agent_eval/run_tasks.py --submission /path/to/submission
# One task only:
python3 tests/agent_eval/run_tasks.py --task 02_nullability --submission /path/to/submission
# Fixture self-check: every initial state must fail its acceptance script:
python3 tests/agent_eval/run_tasks.py --expect-unsolved
# Evaluate an installed SDK instead of the checkout launcher:
python3 tests/agent_eval/run_tasks.py --sdk ~/.sprig/current --submission /path/to/submission
```

`run_tasks.py` copies each task's `initial/` directory to a temporary
directory, overlays the submission tree, sets `SPRIG` to the selected SDK
launcher and runs `accept.py`. Each task scores one point; a task passes only
when the acceptance script exits zero. The runner does not invoke an LLM and
does not claim model performance.

Each task directory contains:

- `task.json`: objective, acceptance, forbidden shortcuts, expected tooling;
- `initial/`: the starting files handed to the agent;
- `accept.py`: mechanical, hidden acceptance checks (compile, run, exact stdout
  and a small number of forbidden-shortcut scans).

Forbidden shortcuts are documented in `task.json` and only mechanically
checked where that is reliable (for example hardcoded expected stdout or Java
imports). Acceptance scripts never inspect the agent's reasoning.

| Task | Focus |
|---|---|
| 01_collections | basic List traversal and counting |
| 02_nullability | narrow a `String?` before use |
| 03_variants | exhaustive variant match |
| 04_generics | explicit `Type[Arg]` generic use |
| 05_json_transform | `@std/json`/`@std/process` file transformation |
| 06_unsupported_recovery | recover from an unsupported feature via composition |
| 07_module_repair | fix an unfamiliar multi-module project |
