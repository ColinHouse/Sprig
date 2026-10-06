#!/usr/bin/env python3
"""Newcomer guidance: constructs from other languages get the Sprig spelling where they
are reported, and help is enough to write a first program without the repository.

Each case is a program a Python, Java or C programmer writes first. The check asserts
the stable code, the targeted message or hint, and that the first diagnostic carries it."""
from pathlib import Path
import json
import os
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
FAILURES = []
COUNT = 0

# (name, source, code, text expected in the first diagnostic's message or hint)
CASES = [
    ("else-if", "if true:\n    print(1)\nelse if false:\n    print(2)\n", "SPR-SYNTAX-ERROR", "elif"),
    ("declaration-without-value", "var label: String\nprint(1)\n", "SPR-SYNTAX-ERROR", "needs an initial value"),
    ("bare-var", "var count\nprint(1)\n", "SPR-SYNTAX-ERROR", "needs an initial value"),
    ("increment", "var count = 1\ncount++\n", "SPR-SYNTAX-ERROR", "count += 1"),
    ("angle-generics", "let xs: List<Int> = [1]\n", "SPR-SYNTAX-ERROR", "List[Int]"),
    ("angle-generics-parameter", "func f(xs: Map<String, Int>) -> Int:\n    return 1\n", "SPR-SYNTAX-ERROR", "square brackets"),
    ("braces", "if true {\n    print(1)\n}\n", "SPR-SYNTAX-ERROR", "not wrapped in braces"),
    ("missing-result-type", "func main():\n    print(1)\n", "SPR-SYNTAX-ERROR", "'func main() -> Unit:'"),
    ("missing-result-type-with-parameters", "func f(a: Int, b: String):\n    print(a)\n", "SPR-SYNTAX-ERROR",
     "'func f(a: Int, b: String) -> Unit:'"),
    ("undeclared-throw", "import \"@std/process.spr\" as process\nfunc main() -> Unit:\n    for line in process.read_lines():\n"
     "        print(line)\nmain()\n", "SPR-FLOW-THROWS", "'func main() -> Unit throws Error:'"),
    ("semicolon", "let x = 1;\n", "SPR-LEX-CHAR", "line break"),
    ("double-ampersand", "if true && false:\n    print(1)\n", "SPR-LEX-CHAR", "'and'"),
    ("double-bar", "if true || false:\n    print(1)\n", "SPR-LEX-CHAR", "'or'"),
    ("bang", "if !true:\n    print(1)\n", "SPR-LEX-CHAR", "'not'"),
    ("interpolation", "let n = 1\nprint($\"{n}\")\n", "SPR-LEX-CHAR", "interpolation"),
    ("read-line", "let line = readLine()\n", "SPR-NAME-UNRESOLVED", "@std/process.spr"),
    ("input", "let line = input()\n", "SPR-NAME-UNRESOLVED", "process.read_lines()"),
    ("println", "println(1)\n", "SPR-NAME-UNRESOLVED", "print(value)"),
    ("int-call", "let n = int(\"3\")\n", "SPR-NAME-UNRESOLVED", "toIntOrNull()"),
    ("str-call", "let s = str(3)\n", "SPR-NAME-UNRESOLVED", "toString()"),
    ("len-call", "print(len(\"ab\"))\n", "SPR-NAME-UNRESOLVED", "length()"),
    ("python-true", "let b = True\n", "SPR-NAME-UNRESOLVED", "lowercase"),
    ("python-none", "let b = None\n", "SPR-NAME-UNRESOLVED", "null"),
    ("self", "class A:\n    let x: Int = 1\n    func f() -> Int:\n        return self.x\n", "SPR-NAME-UNRESOLVED", "no self or this"),
    ("java-lang-without-import", "let m = Math.max(1, 2)\n", "SPR-NAME-UNRESOLVED", "import java.lang.Math as Math"),
    ("scanner", "let s = Scanner(1)\n", "SPR-NAME-UNRESOLVED", "@std/process.spr"),
    ("str-type", "let s: str = \"a\"\n", "SPR-NAME-UNRESOLVED", "Sprig spells this type String"),
    ("int-type", "func f(n: int) -> Int:\n    return n\n", "SPR-NAME-UNRESOLVED", "Sprig spells this type Int"),
    ("boolean-type", "let b: boolean = true\n", "SPR-NAME-UNRESOLVED", "Sprig spells this type Bool"),
    ("void-type", "func f() -> void:\n    print(1)\n", "SPR-NAME-UNRESOLVED", "Sprig spells this type Unit"),
    ("list-length", "let xs = [1, 2]\nprint(xs.length())\n", "SPR-NAME-UNRESOLVED", "items.size()"),
    ("string-size", "print(\"ab\".size())\n", "SPR-NAME-UNRESOLVED", "value.length()"),
    ("list-add", "let xs: MutableList[Int] = []\nxs.add(1)\n", "SPR-NAME-UNRESOLVED", "append"),
    ("unknown-string-method", "print(\"ab\".shout())\n", "SPR-NAME-UNRESOLVED", "String methods: length"),
    ("unknown-std-module", "import \"@std/io.spr\" as io\nprint(1)\n", "SPR-DEP-NOT-FOUND", "@std/process.spr"),
    ("java-class-main", "public class Main {\n    public static void main(String[] args) {\n    }\n}\n", "SPR-SYNTAX-ERROR", "no class Main"),
    ("python-def", "def main():\n    print(1)\n", "SPR-SYNTAX-ERROR", "declared with func"),
    ("javascript-function", "function f() {\n}\n", "SPR-SYNTAX-ERROR", "declared with func"),
    ("const", "const x = 1\n", "SPR-SYNTAX-ERROR", "let (cannot be reassigned)"),
    ("catch-underscore", "try:\n    print(1)\ncatch _:\n    pass\n", "SPR-SYNTAX-ERROR", "catch problem: Error:"),
    ("catch-java", "try:\n    print(1)\ncatch (e: Error):\n    pass\n", "SPR-SYNTAX-ERROR", "catch problem: Error:"),
    ("catch-as", "try:\n    print(1)\ncatch Error as e:\n    pass\n", "SPR-SYNTAX-ERROR", "catch problem: Error:"),
    ("python-except", "try:\n    print(1)\nexcept Exception as e:\n    pass\n", "SPR-SYNTAX-ERROR", "catch problem: Error:"),
    ("slash-comment", "// note\nprint(1)\n", "SPR-SYNTAX-ERROR", "Comments start with '#'"),
    ("block-comment", "/* note */\nprint(1)\n", "SPR-SYNTAX-ERROR", "Comments start with '#'"),
    ("c-style-if", "let s = \"ab\"\nif (s.length() < 3) print(s)\n", "SPR-SYNTAX-ERROR", "header ends with ':'"),
    ("one-line-if", "func f() -> Bool:\n    if true: return false\n    return true\n", "SPR-SYNTAX-ERROR", "no one-line if"),
    ("dot-dot-range", "for i in 0..3:\n    print(i)\n", "SPR-SYNTAX-ERROR", "range(start, stop)"),
    ("assignment-in-condition", "var line = \"a\"\nwhile (line = \"b\"):\n    print(1)\n", "SPR-SYNTAX-ERROR", "Compare with '=='"),
    ("float-int-mix", "let count = 2\nlet total = 3.0\nprint(total / count)\n", "SPR-NUM-MIXED", "count.toFloat()"),
    ("int-division", "let sum = 1\nlet count = 2\nlet average: Float = sum / count\n", "SPR-NUM-DIVISION",
     "sum.toFloat() / count.toFloat()"),
    ("nullable-operand", "let n = \"3\".toIntOrNull()\nprint(n + 1)\n", "SPR-NUM-MIXED", "check it first with 'if n != null:'"),
    ("nullable-ordering", "let n = \"3\".toIntOrNull()\nif n > 2:\n    print(1)\n", "SPR-TYPE-OPERAND", "'if n != null:'"),
    ("nullable-var", "var v = \"3\".toIntOrNull()\nprint(v + 1)\n", "SPR-NUM-MIXED", "a var never narrows"),
    ("module-member", "import \"@std/text.spr\" as text\nprint(text.format(\"a\"))\n", "SPR-NAME-UNRESOLVED",
     "Module 'text' has: join"),
    ("read-only-list", "let words: MutableList[String] = \"a b\".split(\" \")\n", "SPR-TYPE-ASSIGN", "toMutableList()"),
    ("star-import", "import java.io.*\nprint(1)\n", "SPR-SYNTAX-ERROR", "one at a time"),
    ("int-conversion", "let n = Int(\"3\")\n", "SPR-TYPE-NOT-CALLABLE", "toIntOrNull()"),
    ("string-conversion", "let s = String(3)\n", "SPR-TYPE-NOT-CALLABLE", "toString()"),
]

# Ordinary syntax errors keep the parser's own message: the targeted hints do not misfire.
UNTARGETED = [
    ("incomplete-initializer", "let x = 1 +\nprint(x)\n", "needs an initial value"),
    ("missing-colon", "if true\n    print(1)\n", "elif"),
]

# Programs that must keep compiling without any diagnostic: the hints never fire on valid code.
VALID = [
    ("comparison", "let a = 1\nlet b = 2\nprint(a < b)\n"),
    ("map-literal", "let m: Map[String, Int] = {\"a\": 1}\nprint(m[\"a\"])\n"),
    ("elif", "let n = 2\nif n == 1:\n    print(1)\nelif n == 2:\n    print(2)\nelse:\n    print(3)\n"),
    ("not-equal", "print(1 != 2)\n"),
    ("main-called", "func main() -> Unit:\n    print(\"hi\")\n\nmain()\n"),
]


def check(name, ok, detail=""):
    global COUNT
    COUNT += 1
    print(("pass " if ok else "FAIL ") + name)
    if not ok:
        FAILURES.append(f"{name}: {detail}")


def run(*args, cwd, stdin=None):
    return subprocess.run([str(SPRIG), *map(str, args)], cwd=cwd, input=stdin,
                          text=True, capture_output=True)


def main():
    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp)
        for name, source, code, expected in CASES:
            (work / "case.spr").write_text(source, encoding="utf-8")
            result = run("check", "case.spr", "--json", cwd=work)
            diagnostics = json.loads(result.stdout)["diagnostics"]
            first = diagnostics[0] if diagnostics else {}
            text = (first.get("message") or "") + " " + (first.get("hint") or "")
            check("hint-" + name, result.returncode == 1 and first.get("code") == code and expected in text,
                  json.dumps(first))
        for name, source, wrong in UNTARGETED:
            (work / "case.spr").write_text(source, encoding="utf-8")
            result = run("check", "case.spr", "--json", cwd=work)
            diagnostics = json.loads(result.stdout)["diagnostics"]
            text = " ".join((d.get("message") or "") + " " + (d.get("hint") or "") for d in diagnostics)
            check("no-misfire-" + name, result.returncode == 1 and diagnostics and wrong not in text, text)
        for name, source in VALID:
            (work / "case.spr").write_text(source, encoding="utf-8")
            result = run("run", "case.spr", "--json", cwd=work)
            data = json.loads(result.stdout)
            check("valid-" + name, result.returncode == 0 and not data["diagnostics"] and "note" not in data,
                  result.stdout)

        # After a broken header, the body's INDENT and DEDENT are not reported as new errors.
        (work / "case.spr").write_text("func main():\n    print(1)\n    print(2)\nprint(3)\n", encoding="utf-8")
        data = json.loads(run("check", "case.spr", "--json", cwd=work).stdout)["diagnostics"]
        check("no-layout-cascade", len(data) == 1 and "<INDENT>" not in json.dumps(data), json.dumps(data))

        # One error per line: ANTLR's follow-on errors on a broken line are not repeated.
        (work / "case.spr").write_text("print(1 2 3 4)\nlet y = = 2\n", encoding="utf-8")
        data = [d for d in json.loads(run("check", "case.spr", "--json", cwd=work).stdout)["diagnostics"]
                if d["code"] == "SPR-SYNTAX-ERROR"]
        lines = [d["range"]["start"]["line"] for d in data]
        check("one-syntax-error-per-line", sorted(lines) == [0, 1], json.dumps(data))

        # Checked-arithmetic failures at run time say how to handle them.
        (work / "case.spr").write_text("var total = 9223372036854775807\ntotal += 1\n", encoding="utf-8")
        overflow = run("run", "case.spr", cwd=work)
        check("runtime-overflow-hint", overflow.returncode == 1 and "ArithmeticException" in overflow.stderr
              and "9223372036854775807 - value" in overflow.stderr, overflow.stderr)
        (work / "case.spr").write_text("print(2.5.toInt())\n", encoding="utf-8")
        inexact = run("run", "case.spr", cwd=work)
        check("runtime-inexact-int-hint", inexact.returncode == 1 and "toIntTrunc()" in inexact.stderr,
              inexact.stderr)
        numerics = " ".join(json.loads(run("help", "numerics", "--json", cwd=work).stdout)["rules"])
        check("help-numerics-overflow-and-decimals", "ArithmeticException" in numerics and "text.fixed" in numerics,
              numerics)

        # Text output shows a repeated hint once; JSON keeps it on every diagnostic.
        (work / "case.spr").write_text("let a = 1;\nlet b = 2;\nlet c = 3;\n", encoding="utf-8")
        text = run("check", "case.spr", cwd=work).stderr
        data = json.loads(run("check", "case.spr", "--json", cwd=work).stdout)["diagnostics"]
        check("repeated-hint-shown-once", text.count("hint: Statements end at the line break") == 1
              and text.count("Invalid character ';'") == 3 and all(d.get("hint") for d in data), text)

        # A program that only declares main runs nothing; the run says why.
        (work / "main.spr").write_text("func main() -> Unit:\n    print(\"hi\")\n", encoding="utf-8")
        text_run = run("run", "main.spr", cwd=work)
        check("uncalled-main-note", text_run.returncode == 0 and text_run.stdout == ""
              and "does not call main" in text_run.stderr, text_run.stderr)
        json_run = json.loads(run("run", "main.spr", "--json", cwd=work).stdout)
        check("uncalled-main-note-json", "main()" in json_run.get("note", ""), json.dumps(json_run))
        (work / "main.spr").write_text("var total = 0\nfunc main() -> Unit:\n    print(total)\n", encoding="utf-8")
        with_globals = run("run", "main.spr", cwd=work)
        check("uncalled-main-note-with-top-level-variables", "does not call main" in with_globals.stderr,
              with_globals.stderr)

        # The language topic is a complete first program: it compiles and runs on its own.
        language = json.loads(run("help", "language", "--json", cwd=work).stdout)
        (work / "language.spr").write_text("\n".join(language["syntax"]) + "\n", encoding="utf-8")
        executed = run("run", "language.spr", cwd=work, stdin="25\n-3\nnot a number\n7\n")
        check("help-language-program-runs", executed.returncode == 0 and executed.stdout ==
              "25C is warm\n-3C is freezing\n7C is cool\nreadings: 3\n", executed.stdout + executed.stderr)
        rules = " ".join(language["rules"])
        check("help-language-rules", all(part in rules for part in
              ("elif", "initial value", "main is not called", "@std/process.spr", "no braces")), rules)
        check("help-language-builtins", language.get("methods", {}).get("built-in functions") == ["print", "range", "assert"],
              json.dumps(language.get("methods")))

        # Methods come from the checker's own tables: every listed String method type-checks.
        strings = json.loads(run("help", "strings", "--json", cwd=work).stdout)["methods"]["String"]
        calls = {"length": "length()", "isEmpty": "isEmpty()", "charAt": "charAt(0)", "codeAt": "codeAt(0)",
                 "substring": "substring(0, 1)", "indexOf": "indexOf(\"a\")", "contains": "contains(\"a\")",
                 "startsWith": "startsWith(\"a\")", "endsWith": "endsWith(\"a\")", "toUpperCase": "toUpperCase()",
                 "toLowerCase": "toLowerCase()", "trim": "trim()", "split": "split(\",\")",
                 "replace": "replace(\"a\", \"b\")", "repeat": "repeat(2)", "toInt": "toInt()",
                 "toIntOrNull": "toIntOrNull()", "toFloat": "toFloat()", "toString": "toString()"}
        check("help-strings-lists-every-method", set(strings) == set(calls), str(strings))
        program = "\n".join(f"let v{i} = \"a1\".{calls[name]}" for i, name in enumerate(strings) if name in calls)
        (work / "strings.spr").write_text(program + "\n", encoding="utf-8")
        checked = run("check", "strings.spr", "--json", cwd=work)
        check("help-strings-methods-type-check", checked.returncode == 0, checked.stdout)

        # The modules topic names every bundled module.
        modules = " ".join(json.loads(run("help", "modules", "--json", cwd=work).stdout)["rules"])
        bundled = sorted(path.stem for path in (ROOT / "std").glob("*.spr"))
        check("help-modules-lists-std", all(name in modules for name in bundled),
              [name for name in bundled if name not in modules])

        # Text help shows each example's source, not only its repository path.
        text_help = run("help", "language", cwd=work).stdout
        check("help-example-inline", "Example (website/snippets/tutorial/hello.spr):" in text_help
              and "print(greet(\"Ada\"))" in text_help, text_help[-400:])
        check("help-index-start-here", "sprig help language" in run("help", cwd=work).stdout)
        unknown = run("help", "process", cwd=work)
        check("help-module-name-points-at-api", unknown.returncode == 2
              and "sprig api @std/process.spr" in unknown.stderr and "Topics: language" in unknown.stderr,
              unknown.stderr)

    print(f"newcomer hints: {COUNT - len(FAILURES)} passed, {len(FAILURES)} failed")
    for failure in FAILURES:
        print("  " + failure)
    return 1 if FAILURES else 0


if __name__ == "__main__":
    sys.exit(main())
