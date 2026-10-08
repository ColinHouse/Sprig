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

The English book shows the same programs as the Chinese one. A snippet whose
comments are Chinese has an English twin under website/snippets/book_en/ at the
same path: once full-line comments and blank lines are set aside, its code is
the same line for line, and its oracle and role are the same, so the two
editions cannot drift apart.
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


def code_lines(path: Path):
    """The program without its full-line comments and blank lines."""
    return [line.rstrip() for line in path.read_text(encoding="utf-8").splitlines()
            if line.strip() and not line.lstrip().startswith("#")]


def twin_failures(executable, compile_fail):
    failures = []
    english = SNIPPETS / "book_en"
    if not english.is_dir():
        return failures
    for twin in sorted(english.rglob("*.spr")):
        relative = twin.relative_to(english)
        original = SNIPPETS / "book" / relative
        name = f"book_en/{relative.as_posix()}"
        if not original.is_file():
            failures.append(f"website/snippets/{name}: no website/snippets/book/{relative.as_posix()} to mirror")
            continue
        if code_lines(twin) != code_lines(original):
            failures.append(f"website/snippets/{name}: its code differs from book/{relative.as_posix()}")
        original_name = f"book/{relative.as_posix()}"
        for role in (executable, compile_fail):
            if (name in role) != (original_name in role):
                failures.append(f"website/snippets/{name}: registered with a different role than {original_name}")
        for suffix in (".out", ".expect.json"):
            mine, theirs = twin.with_suffix(suffix), original.with_suffix(suffix)
            if mine.is_file() != theirs.is_file() or (
                    mine.is_file() and mine.read_bytes() != theirs.read_bytes()):
                failures.append(f"website/snippets/{name}: its {suffix} differs from book/{relative.as_posix()}")
    return failures


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
    twins = twin_failures(executable, compile_fail)
    failures += twins
    for failure in failures:
        print(failure)
    print(f"documented outputs: {checked} checked, {len(failures) - len(twins)} differ; "
          f"English snippet twins: {len(twins)} differ")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
