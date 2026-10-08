#!/usr/bin/env python3
"""The Equatable capability: every value type is Equatable except a function
value, at the call, in a written Map key and in an inferred one."""
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
    "equatable_function_argument.spr": [("SPR-GENERIC-CONSTRAINT", 7)],
    "function_map_key_written.spr": [("SPR-TYPE-OPERAND", 2)],
    "function_map_key_inferred.spr": [("SPR-TYPE-OPERAND", 2)],
    "function_map_key_generic.spr": [("SPR-TYPE-OPERAND", 3)],
    "nullable_generic_equatable.spr": [("SPR-GENERIC-CONSTRAINT", 8)],
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
    print(f"equatable capability: {len(CASES) - failures} passed, {failures} failed")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
