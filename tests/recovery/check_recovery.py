#!/usr/bin/env python3
"""Front-end recovery regression suite (release blocker for F1).

Two layers:
1. Explicit incomplete constructs (function, class, variant, match, call,
   type expression, try, for, ...) must produce structured SPR diagnostics.
2. Truncation fuzzing: every line prefix of a corpus of real programs is fed to
   the compiler. Each run must exit 0 (valid prefix) or 1 (rejected with
   diagnostics), the JSON must parse, and no run may report an internal
   compiler error.

Run from any directory: python3 tests/recovery/check_recovery.py
"""
import json
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / "sprig"

INCOMPLETE = [
    "let x =\n",
    "let x = 1 +\n",
    "let x = 1 *\n",
    "let x = 1 ==\n",
    "let x = not\n",
    "let x = -\n",
    "let x = (1 +\n",
    "let x: = 1\n",
    "let x: List[\n",
    "func f() ->\n",
    "func f() -> Int:\n",
    "func f() -> Int:\n    return 1 +\n",
    "func f(x: ) -> Int:\n    return x\n",
    "class C:\n",
    "class C:\n    let value: \n",
    "class C:\n    let value: Int\n    func m() ->\n",
    "enum E:\n",
    "variant V:\n",
    "variant V:\n    Case(value: )\n",
    "variant V:\n    Case(value: Int)\nfunc f(v: V) -> Int:\n    match v:\n        case V.Case as x:\n",
    "match x:\n",
    "match x:\n    case A.B:\n",
    "try:\n",
    "try:\n    pass\ncatch e:\n",
    "for item in\n",
    "while\n",
    "if true:\n    pass\nelif:\n",
    "print(\n",
    "let xs = [1, 2,\n",
    "let m = {\"a\":\n",
    "import\n",
    "func f() -> Int:\n    return\n    +\n",
]

# The five files behind the original 403-prefix fuzz in the acceptance report.
FUZZ_FILES = [
    "examples/shapes.spr",
    "examples/word_count.spr",
    "tests/visitor/ast_visitor.spr",
    "tests/review_cases/recursive_visitor.spr",
    "tests/runtime/09_collections.spr",
]

# A few more real programs, every fourth line, to widen shape coverage.
EXTRA_FUZZ_FILES = [
    "examples/fizzbuzz.spr",
    "examples/numeric_science.spr",
    "tests/runtime/08_errors.spr",
    "tests/runtime/15_jvm_interop.spr",
]

passed = 0
failed = []


def verify(name, okay, detail=""):
    global passed
    if okay:
        passed += 1
    else:
        failed.append(name)
        print(f"FAIL {name}: {detail[:400]}")


def check_source(name, text):
    with tempfile.NamedTemporaryFile("w", suffix=".spr", delete=False,
                                     dir=ROOT / "build", encoding="utf-8") as handle:
        handle.write(text)
        path = Path(handle.name)
    try:
        proc = subprocess.run([SPRIG, "check", "--json", path],
                              capture_output=True, text=True, timeout=60)
        if proc.returncode not in (0, 1):
            verify(name, False, f"exit={proc.returncode} stderr={proc.stderr[:200]}")
            return False
        try:
            data = json.loads(proc.stdout)
        except json.JSONDecodeError as exc:
            verify(name, False, f"invalid JSON ({exc}) stdout={proc.stdout[:200]}")
            return False
        codes = [d.get("code") for d in data.get("diagnostics", [])]
        if "SPR-JVM-INTERNAL" in codes or proc.returncode == 2:
            verify(name, False, f"internal compiler error: {codes}")
            return False
        if data.get("exitCode") != proc.returncode:
            verify(name, False, f"envelope exitCode {data.get('exitCode')} != {proc.returncode}")
            return False
        verify(name, True)
        return True
    finally:
        path.unlink(missing_ok=True)


def main():
    (ROOT / "build").mkdir(exist_ok=True)
    for index, source in enumerate(INCOMPLETE):
        check_source(f"incomplete-{index:02d}", source)

    prefixes = 0
    for relative in FUZZ_FILES:
        path = ROOT / relative
        lines = path.read_text(encoding="utf-8").splitlines()
        for end in range(1, len(lines) + 1):
            prefixes += 1
            if not check_source(f"fuzz-{relative}-{end}",
                                "\n".join(lines[:end]) + "\n"):
                pass
    for relative in EXTRA_FUZZ_FILES:
        path = ROOT / relative
        lines = path.read_text(encoding="utf-8").splitlines()
        for end in range(1, len(lines) + 1, 4):
            prefixes += 1
            check_source(f"fuzz-extra-{relative}-{end}", "\n".join(lines[:end]) + "\n")

    print(f"recovery checks: {passed} passed, {len(failed)} failed "
          f"({prefixes} truncation prefixes)")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
