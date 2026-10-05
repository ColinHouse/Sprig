#!/usr/bin/env python3
"""JVM class-file limits must not surface as opaque javac failures.

A string literal longer than the 65,535-byte constant-pool limit is valid Sprig
and must run. A program whose generated method exceeds the 64 KB bytecode limit
cannot be lowered as written; it must fail with a diagnostic that names the
limit and the way out instead of javac's bare "code too large".
"""
import json
import os
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SPRIG = Path(os.environ.get("SPRIG") or ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig"))


def run(path, *extra):
    return subprocess.run([str(SPRIG), "run", *extra, str(path)], text=True, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE, timeout=300, encoding="utf-8")


def main():
    failures = []

    def verify(name, ok, detail=""):
        print(f"{'PASS' if ok else 'FAIL'} {name}")
        if not ok:
            failures.append(name)
            print("  " + detail[:1200])

    with tempfile.TemporaryDirectory(prefix="sprig-jvm-limits-") as work:
        work = Path(work)
        # 70,000 ASCII characters and 30,000 three-byte characters (90,000 bytes).
        strings = work / "long_strings.spr"
        strings.write_text(f'let ascii = "{"a" * 70000}"\nlet wide = "{"中" * 30000}"\n'
                           'print(ascii.length())\nprint(wide.length())\nprint(wide.substring(29999, 30000))\n',
                           encoding="utf-8")
        result = run(strings)
        verify("long string literals run", result.returncode == 0
               and result.stdout.splitlines() == ["70000", "30000", "中"],
               result.stdout + result.stderr)

        items = ", ".join(str(i) for i in range(20000))
        big = work / "big_list.spr"
        big.write_text(f"let values = [{items}]\nprint(values.size())\n", encoding="utf-8")
        result = run(big, "--json")
        try:
            diagnostic = json.loads(result.stdout)["diagnostics"][0]
        except (ValueError, KeyError, IndexError):
            diagnostic = {}
        hint = diagnostic.get("hint", "")
        verify("method size limit is explained", result.returncode != 0
               and diagnostic.get("code") == "SPR-JVM-COMPILE"
               and "64 KB" in hint and "file" in hint,
               result.stdout + result.stderr)
    print(f"jvm limits: {2 - len(failures)} of 2 checks passed")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
