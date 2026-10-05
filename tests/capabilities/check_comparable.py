#!/usr/bin/env python3
"""The Comparable capability: each misuse reports exactly one diagnostic, at the use that breaks the rule."""
import json
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
SEMANTICS = ROOT / "tests" / "semantics"

# fixture -> exact (code, 1-based line) pairs
CASES = {
    "generic_constraint.spr": [("SPR-GENERIC-CONSTRAINT", 3)],
    "generic_comparable_argument.spr": [("SPR-GENERIC-CONSTRAINT", 8)],
    "generic_comparable_nullable.spr": [("SPR-GENERIC-CONSTRAINT", 8)],
    "generic_ordering_needs_comparable.spr": [("SPR-TYPE-OPERAND", 4)],
    "generic_equality_needs_equatable.spr": [("SPR-TYPE-OPERAND", 4)],
    "generic_comparable_method.spr": [("SPR-GENERIC-CONSTRAINT", 10)],
    "generic_comparable_forwarding.spr": [("SPR-GENERIC-CONSTRAINT", 10)],
    "generic_sort_needs_comparable.spr": [("SPR-TYPE-OPERAND", 4)],
}


def main():
    failures = 0
    for name, expected in CASES.items():
        proc = subprocess.run([str(SPRIG), "check", "--json", str(SEMANTICS / name)],
                              capture_output=True, text=True)
        data = json.loads(proc.stdout)
        actual = sorted((d["code"], d["range"]["start"]["line"] + 1) for d in data["diagnostics"])
        if proc.returncode == 1 and actual == sorted(expected):
            print(f"pass {name}")
        else:
            failures += 1
            print(f"FAIL {name}: expected {sorted(expected)}, got {actual}")
    print(f"comparable capability: {len(CASES) - failures} passed, {failures} failed")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
