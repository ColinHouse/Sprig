#!/usr/bin/env python3
"""The full test gate: every gate of tests/test-map.json, in parallel.

    python3 scripts/test.py [--jobs N]

--jobs defaults to the CPU count capped at 8 (SPRIG_TEST_JOBS overrides the
default); --jobs 1 runs everything one after another, as before. Gates that
share state with other gates (see the 'serial' reasons in the map) run alone
after the parallel group. Each gate's output is printed as one block when it
finishes; the final record keeps the map's order.
"""
import argparse
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent / "internal"))
import gate_runner  # noqa: E402


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--jobs", type=int, default=None,
                        help="parallel jobs (default: CPU count capped at 8, or SPRIG_TEST_JOBS); 1 is sequential")
    args = parser.parse_args(argv)
    jobs = args.jobs if args.jobs is not None else gate_runner.default_jobs()
    if jobs < 1:
        parser.error("--jobs must be at least 1")
    if not gate_runner.ensure_built():
        return 2
    test_map = gate_runner.load_map()
    parallel, serial = gate_runner.jobs_for(gate_runner.full_gates(test_map), test_map)
    return gate_runner.run_jobs(parallel, serial, jobs, label="test.py")


if __name__ == "__main__":
    raise SystemExit(main())
