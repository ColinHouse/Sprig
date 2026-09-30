#!/usr/bin/env python3
"""Runs the real Sprig programs that the documentation includes.

Every `website/snippets/**/*.spr` file has an explicit role in
`website/snippets/snippets.json`:

- `executable`  run with `sprig run` and compared byte-for-byte with the
                sibling `.out` oracle; a missing oracle fails the gate;
- `import-only` checked with `sprig check` and must not carry a stale `.out`.
- `compile-fail` checked with `sprig check --json`; a sibling `.expect.json`
                  lists the exact stable diagnostic codes expected.

An unclassified snippet, a stale manifest entry or an orphan `.out` fails the
gate instead of silently choosing a role. Deleting an executable oracle can
therefore never downgrade runtime coverage to a static check.
"""
import argparse
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / ("sprig.cmd" if sys.platform == "win32" else "sprig")
DEFAULT_SNIPPETS = ROOT / "website" / "snippets"
EXECUTABLE, IMPORT_ONLY, COMPILE_FAIL = "executable", "import-only", "compile-fail"


def run_sprig(*args: str) -> subprocess.CompletedProcess:
    return subprocess.run(
        [str(SPRIG), *args], capture_output=True, text=True, cwd=ROOT
    )


def display(path: Path) -> str:
    """Repository-relative when possible, so disposable inventories also print."""
    try:
        return str(path.relative_to(ROOT))
    except ValueError:
        return str(path)


def load_manifest(path: Path) -> tuple[list[str], list[str], list[str]]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("schemaVersion") != 1:
        raise ValueError(f"{path}: unsupported schemaVersion")
    executable = data.get(EXECUTABLE)
    import_only = data.get(IMPORT_ONLY)
    compile_fail = data.get(COMPILE_FAIL, [])
    if (not isinstance(executable, list) or not isinstance(import_only, list)
            or not isinstance(compile_fail, list)
            or any(not isinstance(entry, str) for entry in
                   [*executable, *import_only, *compile_fail])):
        raise ValueError(
            f"{path}: expected '{EXECUTABLE}', '{IMPORT_ONLY}' and optional "
            f"'{COMPILE_FAIL}' string lists"
        )
    both = (set(executable) & set(import_only)) | (set(executable) & set(compile_fail)) \
        | (set(import_only) & set(compile_fail))
    if both:
        raise ValueError(f"{path}: entries classified twice: {sorted(both)}")
    return executable, import_only, compile_fail


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--snippets", type=Path, default=DEFAULT_SNIPPETS,
                        help="snippet root (default: website/snippets)")
    parser.add_argument("--manifest", type=Path,
                        help="role manifest (default: <snippets>/snippets.json)")
    args = parser.parse_args()
    snippets_root = args.snippets.resolve()
    manifest_path = (args.manifest or (snippets_root / "snippets.json")).resolve()

    if not SPRIG.exists():
        print("Build the compiler first: scripts/build.sh", file=sys.stderr)
        return 2
    try:
        executable, import_only, compile_fail = load_manifest(manifest_path)
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"FAIL manifest {manifest_path}: {error}", file=sys.stderr)
        return 2

    actual = {path.relative_to(snippets_root).as_posix(): path
              for path in snippets_root.rglob("*.spr")}
    failures: list[str] = []
    for entry in sorted(set(executable) | set(import_only)):
        if entry not in actual:
            failures.append(f"stale manifest entry: {entry}")

    passed = 0
    for entry in sorted(executable):
        snippet = actual.get(entry)
        if snippet is None:
            continue
        expected_path = snippet.with_suffix(".out")
        relative = display(snippet)
        if not expected_path.is_file():
            failures.append(f"missing oracle: {relative} (executable snippet)")
            continue
        proc = run_sprig("run", str(snippet))
        actual_output = proc.stdout
        expected_output = expected_path.read_text(encoding="utf-8")
        if proc.returncode == 0 and actual_output == expected_output:
            passed += 1
            print(f"pass run  {relative}")
        else:
            failures.append(f"run {relative} (exit {proc.returncode})")
            if actual_output != expected_output:
                print("--- actual ---")
                print(actual_output, end="")
                print("--- expected ---")
                print(expected_output, end="")
            if proc.stderr:
                print(proc.stderr, end="")

    for entry in sorted(import_only):
        snippet = actual.get(entry)
        if snippet is None:
            continue
        relative = display(snippet)
        if snippet.with_suffix(".out").exists():
            failures.append(f"import-only snippet has an oracle: {relative}")
            continue
        proc = run_sprig("check", str(snippet))
        if proc.returncode == 0:
            passed += 1
            print(f"pass check {relative} (import-only)")
        else:
            failures.append(f"check {relative}")
            print(proc.stdout, end="")

    for entry in sorted(compile_fail):
        snippet = actual.get(entry)
        if snippet is None:
            continue
        relative = display(snippet)
        expected_path = snippet.with_suffix(".expect.json")
        if not expected_path.is_file():
            failures.append(f"missing diagnostic oracle: {relative} (compile-fail snippet)")
            continue
        try:
            expected = json.loads(expected_path.read_text(encoding="utf-8"))
            if not isinstance(expected, dict):
                raise ValueError("expected a JSON object")
            expected_codes = expected.get("diagnostics")
            if (not isinstance(expected_codes, list) or not expected_codes
                    or any(not isinstance(code, str) or not code for code in expected_codes)):
                raise ValueError("expected a non-empty 'diagnostics' list of codes")
        except (OSError, ValueError, json.JSONDecodeError) as error:
            failures.append(f"invalid diagnostic oracle: {display(expected_path)} ({error})")
            continue

        proc = run_sprig("check", "--json", str(snippet))
        if proc.returncode == 0:
            failures.append(f"expected compilation failure: {relative}")
            continue
        try:
            report = json.loads(proc.stdout)
            diagnostics = report.get("diagnostics")
            actual_codes = [item.get("code") for item in diagnostics]
            if not isinstance(diagnostics, list) or actual_codes != expected_codes:
                failures.append(
                    f"diagnostic codes for {relative}: expected {expected_codes}, "
                    f"got {actual_codes}"
                )
                continue
        except (ValueError, json.JSONDecodeError, AttributeError, TypeError) as error:
            failures.append(f"invalid JSON diagnostics for {relative}: {error}")
            continue
        passed += 1
        print(f"pass check {relative} (expected failure: {', '.join(expected_codes)})")

    classified = set(executable) | set(import_only) | set(compile_fail)
    for entry in sorted(set(actual) - classified):
        failures.append(f"unclassified snippet: {entry}")
    executable_set = set(executable)
    for oracle in sorted(snippets_root.rglob("*.out")):
        sibling = oracle.with_suffix(".spr").relative_to(snippets_root).as_posix()
        if sibling not in executable_set:
            failures.append(f"oracle without an executable snippet: {display(oracle)}")

    compile_fail_set = set(compile_fail)
    for oracle in sorted(snippets_root.rglob("*.expect.json")):
        relative = oracle.relative_to(snippets_root).as_posix()
        sibling = relative.removesuffix(".expect.json") + ".spr"
        if sibling not in compile_fail_set:
            failures.append(f"diagnostic oracle without a compile-fail snippet: {relative}")

    for failure in failures:
        print(f"FAIL {failure}")
    print(f"documentation snippets: {passed} passed, {len(failures)} failed")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
