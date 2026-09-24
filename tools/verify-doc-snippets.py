#!/usr/bin/env python3
"""Runs the real Sprig programs that the documentation includes.

Every website/snippets/*.spr file with a sibling .spr -> .out expectation is
executed with `sprig run` and compared. Snippet files without a .out file are
treated as import-only modules and are checked with `sprig check`. The
repository examples are executed as well, so the Examples page cannot drift
from what the compiler accepts.
"""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
SPRIG = ROOT / "bin" / "sprig"


def run_sprig(*args: str) -> subprocess.CompletedProcess:
    return subprocess.run(
        [str(SPRIG), *args], capture_output=True, text=True, cwd=ROOT
    )


def main() -> int:
    if not SPRIG.exists():
        print("Build the compiler first: scripts/build.sh", file=sys.stderr)
        return 2

    passed = failed = 0
    snippets = sorted((ROOT / "website" / "snippets").rglob("*.spr"))
    for snippet in snippets:
        expected_path = snippet.with_suffix(".out")
        relative = snippet.relative_to(ROOT)
        if expected_path.exists():
            proc = run_sprig("run", str(snippet))
            actual = proc.stdout
            expected = expected_path.read_text(encoding="utf-8")
            if proc.returncode == 0 and actual == expected:
                passed += 1
                print(f"pass run  {relative}")
            else:
                failed += 1
                print(f"FAIL run  {relative} (exit {proc.returncode})")
                if actual != expected:
                    print("--- actual ---")
                    print(actual, end="")
                    print("--- expected ---")
                    print(expected, end="")
                if proc.stderr:
                    print(proc.stderr, end="")
        else:
            proc = run_sprig("check", str(snippet))
            if proc.returncode == 0:
                passed += 1
                print(f"pass check {relative} (import module)")
            else:
                failed += 1
                print(f"FAIL check {relative}")
                print(proc.stdout, end="")

    for example in sorted((ROOT / "examples").glob("*.spr")):
        proc = run_sprig("run", str(example))
        if proc.returncode == 0:
            passed += 1
            print(f"pass run  {example.relative_to(ROOT)}")
        else:
            failed += 1
            print(f"FAIL run  {example.relative_to(ROOT)}")
            print(proc.stdout, end="")

    print(f"documentation snippets: {passed} passed, {failed} failed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
