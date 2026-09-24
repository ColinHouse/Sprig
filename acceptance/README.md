# Independent acceptance suite

These cases and scripts were written to check compiler behavior independently
of the project's own test corpus. They do not share fixtures with `tests/`;
a finding here should reproduce from the documented commands alone.

| Path | Purpose |
|---|---|
| `cases/*.spr` | Positive, negative, numeric, nullability, effect, `Unit`, generic and F1–F4 regression programs. |
| `scripts/run_acceptance.py` | Runs the must-pass subset and records findings; exits non-zero when a must-pass check fails. |
| `scripts/json_matrix.py` | Exercises `check`/`build`/`run --json` success and failure paths. |
| `scripts/consistency_matrix.py` | Verifies `check`, `build` and `run` agree and that rejected programs leave no class files. |
| `results/` | Generated on each run (gitignored): logs, JSON records and environment details. |

All scripts are invoked by `scripts/test.sh` from the repository root, and each
can be run directly with Python 3 after `scripts/build.sh`.
