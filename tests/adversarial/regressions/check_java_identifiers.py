#!/usr/bin/env python3
"""Sprig identifiers that Java reserves must survive Java generation.

A program that passes `sprig check` must not be rejected by javac. Every Java
keyword, literal and restricted identifier that is also a legal Sprig
identifier, every java.lang.Object member name and the package roots that
generated code spells in fully qualified names are used in each identifier
position: enum case, variant case and field, class field and method, function,
parameter, local and top-level binding. Outputs are written independently of
the compiler, so a renamed case must still print with its Sprig spelling.
"""
import os
import re
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SPRIG = Path(os.environ.get("SPRIG") or ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig"))

# JLS 17 §3.9 keywords and literals, plus the restricted identifiers that may
# not name a type. Listed here, not imported from the compiler, on purpose.
JAVA_WORDS = """abstract assert boolean break byte case catch char class const continue
default do double else enum extends final finally float for goto if implements import
instanceof int interface long native new package private protected public return short
static strictfp super switch synchronized this throw throws transient try void volatile
while true false null _ var yield record sealed permits""".split()
OBJECT_MEMBERS = "clone equals finalize getClass hashCode notify notifyAll toString wait".split()
PACKAGE_ROOTS = ["java", "javax", "sprig"]
# Module-scope names the language already declares (`sprig help functions`).
SPRIG_BUILTINS = {"print", "range", "assert"}


def sprig_keywords():
    grammar = (ROOT / "grammar" / "SprigLexer.g4").read_text(encoding="utf-8")
    return set(re.findall(r"^[A-Z_]+\s*:\s*'([a-z_]+)'\s*;", grammar, re.MULTILINE))


def names():
    keywords = sprig_keywords()
    ordered = []
    for name in JAVA_WORDS + OBJECT_MEMBERS + PACKAGE_ROOTS:
        if name not in keywords and name not in ordered:
            ordered.append(name)
    return ordered


def enum_program(words):
    lines = ["enum Word:"] + [f"    {w}" for w in words] + ["", "func label(word: Word) -> String:", "    match word:"]
    for w in words:
        lines += [f"        case Word.{w}:", f'            return "{w}!"']
    lines.append("")
    expected = []
    for w in words:
        lines += [f"print(Word.{w})", f"print(label(Word.{w}))"]
        expected += [w, f"{w}!"]
    return lines, expected


def variant_program(words):
    lines = ["variant Token:"] + [f"    {w}({w}: Int)" for w in words]
    lines += ["", "func value(token: Token) -> Int:", "    match token:"]
    for w in words:
        lines += [f"        case Token.{w} as item:", f"            return item.{w}"]
    lines.append("")
    expected = []
    for i, w in enumerate(words):
        lines += [f"print(value(Token.{w}({w}={i})))", f"print(Token.{w}({w}={i}))"]
        expected += [str(i), f"{w}({w}={i})"]
    return lines, expected


def class_field_program(words):
    lines = ["class Bag:"] + [f"    let {w}: Int" for w in words] + [""]
    args = ", ".join(f"{w}={i}" for i, w in enumerate(words))
    lines.append(f"let bag = Bag({args})")
    expected = []
    for i, w in enumerate(words):
        lines.append(f"print(bag.{w})")
        expected.append(str(i))
    lines.append("print(bag)")
    expected.append("Bag(" + ", ".join(f"{w}={i}" for i, w in enumerate(words)) + ")")
    return lines, expected


def class_method_program(words):
    lines = ["class Box:", "    let base: Int"]
    for i, w in enumerate(words):
        lines += ["", f"    func {w}() -> Int:", f"        return base + {i}"]
    lines += ["", "let box = Box(base=100)"]
    expected = []
    for i, w in enumerate(words):
        lines.append(f"print(box.{w}())")
        expected.append(str(100 + i))
    # A method named toString is an ordinary method: printing stays canonical.
    lines.append("print(box)")
    expected.append("Box(base=100)")
    return lines, expected


def function_program(words):
    lines, expected = [], []
    for i, w in enumerate(words):
        lines += [f"func {w}(value: Int) -> Int:", f"    return value + {i}", "",
                  f"func param{i}({w}: Int) -> Int:", f"    return {w} * 2", "",
                  f"func local{i}() -> Int:", f"    let {w} = {i}", f"    return {w} + 1", ""]
    for i, w in enumerate(words):
        lines += [f"print({w}(1))", f"print(param{i}({i}))", f"print(local{i}())"]
        expected += [str(1 + i), str(2 * i), str(i + 1)]
    return lines, expected


def top_level_program(words):
    lines, expected = [], []
    for i, w in enumerate(words):
        lines += [f"let {w}: Int = {i}"]
    lines.append("")
    for i, w in enumerate(words):
        lines += [f"func read{i}() -> Int:", f"    return {w}", ""]
    for i, w in enumerate(words):
        lines.append(f"print(read{i}())")
        expected.append(str(i))
    return lines, expected


# A root learned from the program's own JVM types must be protected like the
# built-in roots. Java resolves `org` as a variable before a package only in
# expression context, so the JVM name is used in a static call.
IMPORTED_ROOT = ("""import org.ietf.jgss.GSSManager as GSSManager

class Holder:
    let org: Int

    func describe() -> String:
        let manager = GSSManager.getInstance()
        if manager == null:
            return "none"
        return "field " + org.toString()

func check(org: Int) -> String:
    let manager = GSSManager.getInstance()
    if manager == null:
        return "none"
    return "param " + org.toString()

print(Holder(org=1).describe())
print(check(2))
""".splitlines(), ["field 1", "param 2"])


def run(path):
    return subprocess.run([str(SPRIG), "run", str(path)], text=True, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE, timeout=180)


def main():
    if not SPRIG.exists():
        print(f"Build first: python3 scripts/build.py ({SPRIG} missing)")
        return 1
    words = names()
    cases = {
        "enum-cases": enum_program(words),
        "variant-cases-and-fields": variant_program(words),
        "class-fields": class_field_program(words),
        "class-methods": class_method_program(words),
        "functions-parameters-locals": function_program([w for w in words if w not in SPRIG_BUILTINS]),
        "top-level-bindings": top_level_program([w for w in words if w not in SPRIG_BUILTINS]),
        "imported-package-root": IMPORTED_ROOT,
        # Java pre-processes \\uXXXX escapes everywhere, including comments
        # that quote the source file name.
        "unicode-escape-file-name": (['print("unicode-escape filename")'], ["unicode-escape filename"]),
    }
    failures = []
    with tempfile.TemporaryDirectory(prefix="sprig-java-identifiers-") as work:
        for name, (lines, expected) in cases.items():
            file_name = "uni\\u000aesc.spr" if name == "unicode-escape-file-name" else f"{name}.spr"
            path = Path(work) / file_name
            path.write_text("\n".join(lines) + "\n", encoding="utf-8")
            result = run(path)
            actual = result.stdout.splitlines()
            ok = result.returncode == 0 and actual == expected
            print(f"{'PASS' if ok else 'FAIL'} {name}")
            if not ok:
                failures.append(name)
                detail = (result.stdout + result.stderr).strip().splitlines()
                print("  " + "\n  ".join(detail[:12]))
    print(f"java identifiers: {len(cases) - len(failures)} of {len(cases)} cases passed "
          f"({len(words)} reserved spellings per position)")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
