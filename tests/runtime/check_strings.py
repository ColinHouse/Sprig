#!/usr/bin/env python3
"""Focused Unicode code-point String semantics: golden values and bounds behavior."""
import os
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
HERE = Path(__file__).resolve().parent
passed = 0
failed = []


def call(*args):
    return subprocess.run([str(SPRIG), *map(str, args)], text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=60)


def verify(name, ok, detail=""):
    global passed
    if ok:
        passed += 1
    else:
        failed.append(name)
        print(f"FAIL {name}: {detail[:800]}")


golden = HERE / "20_string_codepoints.spr"
result = call("run", golden)
expected = (HERE / "20_string_codepoints.out").read_text(encoding="utf-8").rstrip("\n")
verify("code-point golden", result.returncode == 0 and result.stdout.rstrip("\n") == expected,
       f"exit={result.returncode} stdout={result.stdout!r} stderr={result.stderr!r}")

RUNTIME_ERRORS = {
    "index negative": ('let text = "A😀東"\nprint(text[-1])\n',
                       "String index -1 is out of bounds; the string has 3 code points"),
    "index equals length": ('let text = "A😀東"\nprint(text[3])\n',
                            "String index 3 is out of bounds; the string has 3 code points"),
    "charAt negative": ('print("😀".charAt(-1))\n',
                        "String index -1 is out of bounds; the string has 1 code point"),
    "charAt past end": ('print("😀".charAt(1))\n',
                        "String index 1 is out of bounds; the string has 1 code point"),
    "codeAt past end": ('print("A😀".codeAt(2))\n',
                        "String index 2 is out of bounds; the string has 2 code points"),
    "substring end past end": ('print("A😀東".substring(1, 4))\n',
                               "String slice [1, 4) is out of bounds; the string has 3 code points"),
    "substring start after end": ('print("A😀東".substring(2, 1))\n',
                                  "String slice start 2 is after end 1"),
    "substring negative": ('print("A😀東".substring(-1))\n',
                           "String index -1 is out of bounds; the string has 3 code points"),
    "int32 conversion": ('print("abc"[3000000000])\n', "outside Int32 range"),
    "fromCode negative": ("print(String.fromCode(-1))\n", "IllegalArgumentException"),
    "fromCode beyond max": ("print(String.fromCode(1114112))\n", "IllegalArgumentException"),
    # Texts of 32 UTF-16 units or more keep their count between calls; the
    # errors are the same, before and after the text was indexed.
    "long index equals length": ('let text = "東京😀".repeat(20)\nprint(text[60])\n',
                                 "String index 60 is out of bounds; the string has 60 code points"),
    "long codeAt after indexing": ('let text = "東京の空".repeat(10)\nprint(text.codeAt(39))\nprint(text.codeAt(40))\n',
                                   "String index 40 is out of bounds; the string has 40 code points"),
    "long substring end past end": ('let text = "A😀東".repeat(20)\nprint(text[0])\nprint(text.substring(59, 61))\n',
                                    "String slice [59, 61) is out of bounds; the string has 60 code points"),
    "long substring negative": ('print("東".repeat(40).substring(-1))\n',
                                "String index -1 is out of bounds; the string has 40 code points"),
}

POSITIVE = {
    "charAt first": ('print("A😀東".charAt(0))\n', "A"),
    "charAt last": ('print("A😀東".charAt(2))\n', "東"),
    "codeAt first": ('print("A😀東".codeAt(0))\n', "65"),
    "codeAt last": ('print("A😀東".codeAt(2))\n', "26481"),
    "indexOf supplementary": ('print("A😀東".indexOf("😀"))\n', "1"),
    "indexOf after supplementary": ('print("😀A".indexOf("A"))\n', "1"),
    "indexOf empty needle": ('print("abc".indexOf(""))\n', "0"),
    "indexOf not found": ('print("abc".indexOf("z"))\n', "-1"),
    "substring empty result": ('print("A😀東".substring(1, 1) == "")\n', "true"),
    "substring start only": ('print("A😀東".substring(2))\n', "東"),
    "fromCode supplementary": ("print(String.fromCode(128512))\n", "😀"),
    "split empty separator": ('let parts = "😀".split("")\nprint(parts.size())\nprint(parts[0])\n', "2\n😀"),
    "long supplementary positions": ('let text = "東京😀".repeat(20)\nprint(text.length())\nprint(text.codeAt(59))\n'
                                     'print(text[58])\nprint(text.substring(57, 59))\nprint(text.lastIndexOf("😀"))\n',
                                     "60\n128512\n京\n東京\n59"),
    "long BMP positions": ('let text = "東京の空".repeat(10)\nprint(text.indexOf("空"))\nprint(text.length())\n'
                           'print(text.codeAt(39))\nprint(text.charAt(38))\nprint(text.lastIndexOf("東京"))\n',
                           "3\n40\n31354\nの\n36"),
    "long alternating texts": ('let a = "東京の空".repeat(10)\nlet b = "A😀".repeat(20)\nvar out = ""\n'
                               'for i in range(3):\n    out += a[i] + b[i + 37]\nprint(out)\n', "東😀京Aの😀"),
}

with tempfile.TemporaryDirectory(prefix="sprig-strings-") as work:
    directory = Path(work)
    for name, (source, expected) in RUNTIME_ERRORS.items():
        path = directory / (name.replace(" ", "_") + ".spr")
        path.write_text(source, encoding="utf-8")
        result = call("run", path)
        verify(f"runtime {name}", result.returncode != 0 and expected in result.stdout + result.stderr,
               f"exit={result.returncode} {result.stdout}{result.stderr}")
    for name, (source, expected) in POSITIVE.items():
        path = directory / (name.replace(" ", "_") + ".spr")
        path.write_text(source, encoding="utf-8")
        result = call("run", path)
        verify(f"positive {name}", result.returncode == 0 and result.stdout.rstrip("\n") == expected,
               f"exit={result.returncode} {result.stdout!r} {result.stderr}")

    # Java char interop stays a UTF-16 code-unit boundary: a one-unit literal is
    # accepted; a two-unit literal or supplementary code point is rejected.
    interop_ok = directory / "interop_ok.spr"
    interop_ok.write_text('import java.lang.Character as Character\nprint(Character.isLetter("a"))\n',
                          encoding="utf-8")
    verify("interop char accepts one unit", call("run", interop_ok).returncode == 0)
    for name, literal in {"interop char two units": '"ab"', "interop char astral": '"😀"'}.items():
        path = directory / (name.replace(" ", "_") + ".spr")
        path.write_text(f'import java.lang.Character as Character\nprint(Character.isLetter({literal}))\n',
                        encoding="utf-8")
        result = call("check", path)
        verify(f"{name} rejected", result.returncode != 0 and "SPR-JVM-MEMBER" in result.stdout + result.stderr,
               f"exit={result.returncode} {result.stdout}{result.stderr}")

    # The runtime's String positions under concurrency: virtual threads index a
    # shared pool of texts at random against an oracle from String.codePoints().
    probe_classes = directory / "probe"
    compiled = subprocess.run(["javac", "--release", "21", "-encoding", "UTF-8", "-d", str(probe_classes),
                               str(ROOT / "runtime/src/main/java/sprig/runtime/StringOps.java"),
                               str(HERE / "StringOpsProbe.java")],
                              text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=120)
    verify("StringOps probe compiles", compiled.returncode == 0, compiled.stdout)
    if compiled.returncode == 0:
        probe = subprocess.run(["java", "-Dfile.encoding=UTF-8", "-cp", str(probe_classes), "StringOpsProbe"],
                               text=True, encoding="utf-8", errors="replace",
                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=300)
        verify("StringOps concurrent probe", probe.returncode == 0 and " 0 failures" in probe.stdout, probe.stdout)

print(f"String code-point semantics: {passed} checks passed, {len(failed)} failed")
for name in failed:
    print(f"failed: {name}")
raise SystemExit(1 if failed else 0)
