#!/usr/bin/env python3
"""Exact program arguments through the official Windows launcher, bin/sprig.cmd.

Each value is written the way a user quotes it at a cmd.exe prompt, and a Sprig
program reports the code points the JVM received. The launcher is not given a
Python argument list: subprocess quotes for the C runtime, not for cmd.exe, so
'&', '|' or '^' would reach cmd unquoted. Quoting for cmd is the caller's (or
the outer shell's) obligation; this suite checks that the launcher forwards
correctly quoted input unchanged, with delayed expansion off and inherited on.
"""
import argparse
import ctypes
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]

PROGRAM = '''import "@std/process.spr" as process

for value in process.arguments():
    var line = value.length().toString() + ":"
    for element in value:
        line = line + " " + element.codeAt(0).toString()
    print(line)
'''

# With delayed expansion off, the outer cmd parse leaves quoted values unchanged.
LITERALS = ["two words", " padded ", "", "a&b", "x&&y||z", "(parenthesized)", "x)y(",
            "wow!", "!PATH!", "a!b!c", "a^b", "100%", "pipe|and<redirect>",
            "semi;colon,comma=equals", r"C:\dir with\backslashes"]
# With delayed expansion on, the outer command-line parse itself expands a
# defined !NAME! and consumes '^'. It leaves these values unchanged, so any
# difference comes from the launcher inheriting that state.
DELAYED = ["wow!", "!", "x!y", "a!b!c", "two words", "a&b", "(parenthesized)", "100%"]
# java.exe reads its command line in the ANSI code page and replaces text
# outside it before the JVM starts, so only representable samples are exact.
UNICODE = ["café", "naïve", "Ωμέγα", "Привет", "中文", "日本語", "😀"]


def expected(value):
    return f"{len(value)}:" + "".join(f" {ord(char)}" for char in value)


def representable(value, code_page):
    try:
        value.encode(f"cp{code_page}")
        return True
    except (LookupError, UnicodeEncodeError):
        return False


def forwarded(launcher, program, values, delayed):
    """Runs the launcher from cmd.exe and returns the values whose code points changed."""
    line = " ".join([f'"{launcher}"', "run", f'"{program}"', "--", *(f'"{value}"' for value in values)])
    result = subprocess.run(f'cmd.exe /d /v:{delayed} /s /c "{line}"', capture_output=True,
                            text=True, encoding="utf-8", errors="replace", timeout=300)
    lines = result.stdout.splitlines()
    lines += [None] * (len(values) - len(lines))
    changed = [value for value, line in zip(values, lines) if line != expected(value)]
    if result.returncode != 0 or len(lines) != len(values) or changed:
        return f"exit {result.returncode}, changed {changed!r}\n{result.stdout}{result.stderr}"
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--launcher", type=Path, default=ROOT / "bin" / "sprig.cmd")
    options = parser.parse_args()
    if os.name != "nt":
        print("SKIP Windows launcher arguments: bin/sprig.cmd is the Windows launcher")
        return 0
    code_page = ctypes.windll.kernel32.GetACP()
    unicode = [value for value in UNICODE if representable(value, code_page)]
    if all(value.isascii() for value in unicode):
        raise AssertionError(f"ANSI code page {code_page} carries none of {UNICODE}")
    failures = []
    with tempfile.TemporaryDirectory(prefix="sprig launcher arguments ") as temp:
        program = Path(temp) / "arguments.spr"
        program.write_text(PROGRAM, encoding="utf-8")
        for delayed, values in (("off", LITERALS + unicode), ("on", DELAYED + unicode)):
            failure = forwarded(options.launcher.resolve(), program, values, delayed)
            print(f"{'FAIL' if failure else 'PASS'} delayed expansion {delayed}: {len(values)} arguments")
            if failure:
                failures.append(f"delayed expansion {delayed}: {failure}")
    outside = [value for value in UNICODE if value not in unicode]
    print(f"note: ANSI code page {code_page} cannot carry {outside}; java.exe replaces that text "
          "before the JVM starts (JDK launcher limitation)")
    for failure in failures:
        print(failure)
    print(f"Windows launcher arguments: {len(failures)} failures")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
