#!/usr/bin/env python3
"""The output a page shows under an included snippet is that snippet's real output.

A page includes a program with `<<< @/snippets/NAME.spr` and shows what it
prints in the ```text block right after it. verify-doc-snippets.py runs every
snippet against its oracle; this check covers what the reader sees:

- under an executable snippet, the text block is exactly the `.out` oracle;
- under a compile-fail snippet, the text block is what `sprig check` prints for
  it, line for line and in order. The page may leave out lines, such as the
  closing "1 error(s)" summary, and may call the file main.spr, the name a
  reader saves it under.

A text block that does not come right after an include is prose, not output,
and is not checked here.
"""
from pathlib import Path
import json
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / ("sprig.cmd" if sys.platform == "win32" else "sprig")
SNIPPETS = ROOT / "website" / "snippets"
INCLUDE = re.compile(r"^<<<\s+@/snippets/(\S+?\.spr)(?:[{\[#].*)?\s*$")
FENCE = re.compile(r"^(```|~~~)\s*(\S*)")


def shown_outputs(page: Path):
    """(line number, snippet, shown text) for each include that a text block follows."""
    lines = page.read_text(encoding="utf-8").splitlines()
    i = 0
    while i < len(lines):
        include = INCLUDE.match(lines[i].strip())
        i += 1
        if not include:
            continue
        j = i
        while j < len(lines) and not lines[j].strip():
            j += 1
        fence = FENCE.match(lines[j].strip()) if j < len(lines) else None
        if not fence or fence.group(2) != "text":
            continue
        body = []
        k = j + 1
        while k < len(lines) and not lines[k].strip().startswith(fence.group(1)):
            body.append(lines[k])
            k += 1
        yield j + 1, include.group(1), body
        i = k + 1


def normalized(lines, file_name):
    """The diagnostic lines with the snippet's file name written as main.spr."""
    return [line.rstrip().replace(file_name + ":", "main.spr:") for line in lines]


def main() -> int:
    manifest = json.loads((SNIPPETS / "snippets.json").read_text(encoding="utf-8"))
    executable = set(manifest["executable"])
    compile_fail = set(manifest.get("compile-fail", []))
    pages = [ROOT / p for p in subprocess.check_output(
        ["git", "ls-files", "website/*.md", "website/**/*.md"], cwd=ROOT, text=True).splitlines()]
    failures = []
    checked = 0
    checks_cache = {}
    for page in pages:
        if not page.is_file():
            continue
        where = page.relative_to(ROOT)
        for line, snippet, shown in shown_outputs(page):
            source = SNIPPETS / snippet
            if snippet in executable:
                expected = source.with_suffix(".out").read_text(encoding="utf-8").splitlines()
                checked += 1
                if [l.rstrip() for l in shown] != [l.rstrip() for l in expected]:
                    failures.append(f"{where}:{line}: the output shown for {snippet} is not its .out oracle")
            elif snippet in compile_fail:
                if snippet not in checks_cache:
                    result = subprocess.run([str(SPRIG), "check", source.name], cwd=source.parent,
                                            capture_output=True, text=True, encoding="utf-8")
                    checks_cache[snippet] = normalized((result.stdout + result.stderr).splitlines(),
                                                       source.name)
                actual = checks_cache[snippet]
                checked += 1
                position = 0
                for shown_line in normalized(shown, source.name):
                    if not shown_line.strip():
                        continue
                    while position < len(actual) and actual[position] != shown_line:
                        position += 1
                    if position == len(actual):
                        failures.append(f"{where}:{line}: {snippet} does not print {shown_line!r}")
                        break
                    position += 1
    for failure in failures:
        print(failure)
    print(f"documented outputs: {checked} checked, {len(failures)} differ")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
