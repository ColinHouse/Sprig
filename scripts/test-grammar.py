#!/usr/bin/env python3
"""Independent grammar harness with platform classpath separators."""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


# Negative fixtures whose shape the grammar accepts so that the front end can
# report the one error and still build the AST for the language server; the
# AST builder rejects them with SPR-SYNTAX-ERROR.
GRAMMAR_ACCEPTS = {"22_if_expression_missing_else.spr", "24_function_without_body.spr"}


def main():
    antlr = Path(os.environ.get("ANTLR_JAR", ROOT / "build/deps/antlr-4.13.2-complete.jar")).resolve()
    if not antlr.is_file():
        raise SystemExit("Build first, or set ANTLR_JAR to the pinned complete JAR.")
    with tempfile.TemporaryDirectory(prefix="sprig-antlr-") as temp:
        build = Path(temp)
        for grammar in ("SprigLexer.g4", "SprigParser.g4"):
            subprocess.run(["java", "-jar", str(antlr), "-Dlanguage=Java", "-visitor",
                            "-no-listener", "-lib", str(build), "-o", str(build), grammar],
                           cwd=ROOT / "grammar", check=True)
        sources = sorted(build.glob("*.java")) + sorted((ROOT / "tests/grammar/fixtures").glob("*.java"))
        subprocess.run(["javac", "-cp", str(antlr), "-d", str(build), *map(str, sources)], check=True)
        count = 0
        for category in ("positive", "negative"):
            for source in sorted((ROOT / "tests/syntax" / category).glob("*.spr")):
                result = subprocess.run(["java", "-cp", os.pathsep.join((str(build), str(antlr))),
                                         "ParseSmoke", str(source)], capture_output=True, text=True)
                # A negative fixture the grammar accepts on purpose is rejected by the
                # AST builder instead (sprig check --syntax-only still fails on it).
                accepted = category == "positive" or source.name in GRAMMAR_ACCEPTS
                if (result.returncode == 0) != accepted:
                    raise AssertionError(f"{category}: {source.name}\n{result.stdout}{result.stderr}")
                count += 1
    print(f"GRAMMAR CASES PASS: {count} (syntax only; not type/runtime tests)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
