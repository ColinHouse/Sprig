#!/usr/bin/env python3
"""Canonical contributor gate; release archive/matrix gates remain separate."""
from pathlib import Path
import os
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def main():
    env = dict(os.environ, PYTHONUTF8="1")
    gates = ["scripts/build.py", "scripts/test.py", "scripts/test-grammar.py", "scripts/check-docs.py", "scripts/check-editor.py"]
    for gate in gates:
        print(f"\n== verify: {gate} ==", flush=True)
        result = subprocess.run([sys.executable, str(ROOT / gate)], cwd=ROOT, env=env)
        if result.returncode:
            return result.returncode
    print("Contributor verification passed (build, compiler/JVM, grammar, docs, editor).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
