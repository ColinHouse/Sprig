#!/usr/bin/env python3
"""Semantic expectation runner: asserts the expected diagnostic code is
reported (and that --json output is valid schema-shaped JSON)."""
import json
import os
import subprocess
import sys

SCHEMA_KEYS = {"code", "phase", "uri", "range", "message", "related", "suggestedEdits"}


def main():
    root = sys.argv[1]
    cases_path = os.path.join(root, "tests", "semantics", "cases.json")
    with open(cases_path, encoding="utf-8") as handle:
        cases = json.load(handle)
    sprig = os.path.join(root, "bin", "sprig")
    passed = failed = 0
    for case in cases:
        path = os.path.join(root, "tests", "semantics", case["file"])
        proc = subprocess.run([sprig, "check", "--json", path],
                              capture_output=True, text=True)
        try:
            data = json.loads(proc.stdout)
        except json.JSONDecodeError:
            print(f"FAIL {case['file']}: --json output is not valid JSON")
            failed += 1
            continue
        diagnostics = data.get("diagnostics", [])
        codes = {d.get("code") for d in diagnostics}
        schema_ok = True
        for diagnostic in diagnostics:
            if not SCHEMA_KEYS.issubset(diagnostic.keys()):
                schema_ok = False
                print(f"FAIL {case['file']}: diagnostic missing keys: "
                      f"{SCHEMA_KEYS - set(diagnostic.keys())}")
                break
            rng = diagnostic.get("range")
            if rng is not None:
                start = rng.get("start", {})
                if "line" not in start or "character" not in start:
                    schema_ok = False
                    print(f"FAIL {case['file']}: range is not zero-based start/end")
                    break
        if case["expected_diagnostic"] in codes and schema_ok:
            passed += 1
            print(f"pass {case['file']}: {case['expected_diagnostic']}")
        elif schema_ok:
            failed += 1
            print(f"FAIL {case['file']}: expected {case['expected_diagnostic']}, "
                  f"got {sorted(codes)}")
        else:
            failed += 1
    print(f"semantic cases: {passed} passed, {failed} failed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
