#!/usr/bin/env python3
"""Failure modes for the explicit documentation-snippet manifest."""
from pathlib import Path
import json
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / ("sprig.cmd" if sys.platform == "win32" else "sprig")
VERIFY = ROOT / "tools" / "verify-doc-snippets.py"

FAILURES = []


def check(name, ok, detail=""):
    print(("PASS " if ok else "FAIL ") + name)
    if not ok:
        FAILURES.append(f"{name}: {detail}")


def inventory(root, executable=("a.spr",), import_only=("mod.spr",), files=None):
    """Write a tiny disposable inventory and its manifest."""
    root.mkdir(parents=True, exist_ok=True)
    (root / "a.spr").write_text("print(1)\n", encoding="utf-8")
    (root / "a.out").write_text("1\n", encoding="utf-8")
    (root / "mod.spr").write_text("func answer() -> Int:\n    return 42\n", encoding="utf-8")
    manifest = {"schemaVersion": 1,
                "executable": list(executable),
                "import-only": list(import_only)}
    for name, content in (files or {}).items():
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
    (root / "snippets.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")


def run(root):
    result = subprocess.run(
        [sys.executable, str(VERIFY), "--snippets", str(root),
         "--manifest", str(root / "snippets.json")],
        cwd=ROOT, capture_output=True, text=True, timeout=120)
    return result.returncode, result.stdout + result.stderr


def main():
    assert SPRIG.is_file(), "build Sprig before running the documentation checks"
    with tempfile.TemporaryDirectory(prefix="sprig-doc-roles-") as temp:
        base = Path(temp)

        good = base / "good"
        inventory(good)
        code, output = run(good)
        check("explicit-roles-pass", code == 0 and "2 passed, 0 failed" in output, output)

        missing = base / "missing-golden"
        inventory(missing)
        (missing / "a.out").unlink()
        code, output = run(missing)
        check("deleted-oracle-fails", code == 1 and "missing oracle" in output, output)

        unclassified = base / "unclassified"
        inventory(unclassified, files={"extra.spr": "print(2)\n"})
        code, output = run(unclassified)
        check("unclassified-snippet-fails", code == 1 and "unclassified snippet: extra.spr" in output, output)

        stale = base / "stale"
        inventory(stale)
        (stale / "mod.spr").unlink()
        code, output = run(stale)
        check("stale-manifest-entry-fails", code == 1 and "stale manifest entry: mod.spr" in output, output)

        stray_oracle = base / "stray-oracle"
        inventory(stray_oracle, files={"mod.out": "42\n"})
        code, output = run(stray_oracle)
        check("import-only-oracle-fails", code == 1 and "import-only snippet has an oracle" in output, output)

        orphan = base / "orphan-oracle"
        inventory(orphan, files={"lonely.out": "1\n"})
        code, output = run(orphan)
        check("orphan-oracle-fails", code == 1 and "oracle without an executable snippet" in output, output)

        duplicate = base / "duplicate"
        inventory(duplicate, executable=("a.spr",), import_only=("a.spr",))
        code, output = run(duplicate)
        check("duplicate-role-fails", code == 2 and "classified twice" in output, output)

    if FAILURES:
        for failure in FAILURES:
            print(f"FAIL {failure}")
        return 1
    print("documentation roles: 7 checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
