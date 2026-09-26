#!/usr/bin/env python3
"""Independent grammar harness with platform classpath separators."""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    antlr = Path(os.environ.get("ANTLR_JAR", ROOT / "tools/antlr-4.13.2-complete.jar")).resolve()
    if not antlr.is_file():
        raise SystemExit("Build first, or set ANTLR_JAR to the pinned complete JAR.")
    with tempfile.TemporaryDirectory(prefix="sprig-antlr-") as temp:
        build = Path(temp)
        for grammar in ("SprigLexer.g4", "SprigParser.g4"):
            subprocess.run(["java", "-jar", str(antlr), "-Dlanguage=Java", "-visitor",
                            "-no-listener", "-lib", str(build), "-o", str(build), grammar],
                           cwd=ROOT / "grammar", check=True)
        sources = sorted(build.glob("*.java")) + sorted((ROOT / "tools/grammar-harness").glob("*.java"))
        subprocess.run(["javac", "-cp", str(antlr), "-d", str(build), *map(str, sources)], check=True)
        count = 0
        for category in ("positive", "negative"):
            for source in sorted((ROOT / "tests/syntax" / category).glob("*.spr")):
                result = subprocess.run(["java", "-cp", os.pathsep.join((str(build), str(antlr))),
                                         "ParseSmoke", str(source)], capture_output=True, text=True)
                if (result.returncode == 0) != (category == "positive"):
                    raise AssertionError(f"{category}: {source.name}\n{result.stdout}{result.stderr}")
                count += 1
    print(f"GRAMMAR CASES PASS: {count} (syntax only; not type/runtime tests)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
