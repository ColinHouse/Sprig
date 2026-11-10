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

    # A function's local String var that a loop appends to is kept in a builder
    # (sprig.runtime.TextAccumulator), so the loop copies each part once.
    # Shared state, and a local that no loop appends to, stay plain Strings.
    # The generated Java shows which is which; 61_text_accumulator checks the text.
    loop = "    for i in range(3):\n"
    ACCUMULATORS = {
        "local appended in a loop": ('func build() -> String:\n    var out = ""\n' + loop
                                     + '        out += i\n    return out\nprint(build())\n', True, "012"),
        "local with text = text + part": ('func build() -> String:\n    var out = ""\n' + loop
                                          + '        out = out + i + ","\n    return out\nprint(build())\n',
                                          True, "0,1,2,"),
        "local of a top-level block": ('for round in range(2):\n    var out = ""\n' + loop
                                       + '        out += i\n    print(out)\n', True, "012\n012"),
        "local declared in an outer loop": ('func build() -> String:\n    var all = ""\n    for round in range(2):\n'
                                            '        var out = ""\n        for i in range(3):\n'
                                            '            out += i\n        all += out + ";"\n    return all\n'
                                            'print(build())\n', True, "012;012;"),
        "module-level var": ('var out = ""\nfor i in range(3):\n    out += i\nprint(out)\n', False, "012"),
        "field": ('class Log:\n    var text: String = ""\n\n    func add(count: Int) -> Unit:\n'
                  '        for i in range(count):\n            text += i\n\nlet log = Log()\nlog.add(3)\n'
                  'print(log.text)\n', False, "012"),
        "local without a loop": ('func build() -> String:\n    var out = "a"\n    out += "b"\n    return out\n'
                                 'print(build())\n', False, "ab"),
        "local declared in the loop that appends": ('func build() -> Int:\n    var total = 0\n' + loop
                                                    + '        var out = "a"\n        out += i\n'
                                                    '        total += out.length()\n    return total\n'
                                                    'print(build())\n', False, "6"),
        "local that a loop only prepends to": ('func build() -> String:\n    var out = ""\n' + loop
                                               + '        out = i + out\n    return out\nprint(build())\n',
                                               False, "210"),
    }
    for name, (source, kept_in_builder, expected) in ACCUMULATORS.items():
        path = directory / (name.replace(" ", "_").replace("=", "") + ".spr")
        path.write_text(source, encoding="utf-8")
        java = directory / (path.stem + " java")
        built = call("build", path, "--emit-java-only", "-d", java)
        generated = "".join(file.read_text(encoding="utf-8") for file in sorted(java.rglob("*.java")))
        result = call("run", path)
        verify(f"accumulator {name}",
               built.returncode == 0 and ("sprig.runtime.TextAccumulator" in generated) == kept_in_builder
               and result.returncode == 0 and result.stdout.rstrip("\n") == expected,
               f"build={built.returncode} run={result.returncode} {result.stdout!r} {built.stderr}{result.stderr}")

    # 400,000 appends of 25 characters build a text of 10 MB. As concatenations
    # they copy 25 * 400000^2 / 2 = 2 * 10^12 characters, which takes minutes;
    # appended to a builder they copy 10^7, so the run fits the usual time limit.
    growth = directory / "growth.spr"
    growth.write_text('func build(count: Int) -> Int:\n    var out = ""\n    for i in range(count):\n'
                      '        out += "0123456789012345678901234"\n    return out.length()\n'
                      'print(build(400000))\n', encoding="utf-8")
    try:
        result = call("run", growth)
        verify("accumulator grows in linear time", result.returncode == 0 and result.stdout.strip() == "10000000",
               f"exit={result.returncode} {result.stdout!r} {result.stderr}")
    except subprocess.TimeoutExpired:
        verify("accumulator grows in linear time", False, "400,000 appends did not finish in 60 s")

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
