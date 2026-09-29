#!/usr/bin/env python3
"""sprig-json-codec: formatted pure-Sprig library, its own tests, consumer usage."""
from pathlib import Path
import json
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
LIB = ROOT / "libraries" / "sprig-json-codec"
passed = 0
failed = []


def call(*args, cwd=None):
    return subprocess.run([str(SPRIG), *map(str, args)], cwd=cwd or ROOT, text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=180)


def verify(name, ok, detail=""):
    global passed
    if ok:
        passed += 1
    else:
        failed.append(name)
        print(f"FAIL {name}: {detail[:900]}")


EXPECTED_TESTS = {
    "build_board_config.spr", "encoding.spr", "primitives.spr", "structure.spr", "support.spr",
}


def main():
    verify("library-layout", (LIB / "sprig.toml").is_file() and (LIB / "src/codec.spr").is_file()
           and (LIB / "README.md").is_file())
    verify("pure-sprig-library", not list(LIB.rglob("*.java")),
           str([p.name for p in LIB.rglob("*.java")]))

    for target in ("src", "tests"):
        formatted = call("fmt", "--check", target, cwd=LIB)
        verify(f"fmt-check-{target}", formatted.returncode == 0,
               f"exit={formatted.returncode} {formatted.stdout}{formatted.stderr}")

    resolved = call("resolve", "--offline", cwd=LIB)
    verify("library-resolve", resolved.returncode == 0, resolved.stdout + resolved.stderr)

    tested = call("test", "--json", cwd=LIB)
    report = None
    try:
        report = json.loads(tested.stdout)
    except ValueError:
        pass
    verify("library-tests", tested.returncode == 0 and report is not None
           and report["summary"] == {"total": len(EXPECTED_TESTS), "passed": len(EXPECTED_TESTS), "failed": 0}
           and {row["name"] for row in report["tests"]} == EXPECTED_TESTS,
           f"exit={tested.returncode} {tested.stdout[:400]}{tested.stderr[:200]}")

    # A consumer project uses the library through normal dependency/import rules.
    with tempfile.TemporaryDirectory(prefix="sprig-json-codec-consumer-") as temp:
        project = Path(temp)
        (project / "src").mkdir()
        (project / "sprig.toml").write_text(
            "[project]\n"
            'name = "codec-consumer"\n'
            'version = "0.1.0"\n'
            'language = "0.8"\n'
            'source = "src"\n'
            'entry = "src/main.spr"\n'
            "\n"
            "[[dependency]]\n"
            'name = "json-codec"\n'
            f'path = "{LIB}"\n',
            encoding="utf-8")
        (project / "src/main.spr").write_text('''import "@std/json.spr" as json
import "@json-codec/codec.spr" as codec

func decode_value(text: String) -> String throws Error:
    let root = codec.root(json.parse(text))
    codec.reject_unknown_fields(root, ["schema", "hud_enabled"])
    let schema = codec.required_int(root, "schema")
    let hud = codec.required_bool(root, "hud_enabled")
    return schema.toString() + ":" + hud.toString()

print(decode_value("{\\"schema\\": 1, \\"hud_enabled\\": true}"))
try:
    print(decode_value("{\\"schema\\": 1, \\"hud_enabled\\": true, \\"extra\\": 1}"))
catch problem: Error:
    print(problem.message)
try:
    print(decode_value("{\\"schema\\": 1}"))
catch problem: Error:
    print(problem.message)
''', encoding="utf-8")

        consumer_resolve = call("resolve", "--offline", cwd=project)
        verify("consumer-resolve", consumer_resolve.returncode == 0,
               consumer_resolve.stdout + consumer_resolve.stderr)
        consumer_check = call("check", cwd=project)
        verify("consumer-check", consumer_check.returncode == 0,
               consumer_check.stdout + consumer_check.stderr)
        consumer_run = call("run", cwd=project)
        expected = ("1:true\n"
                    "$: unknown field 'extra'\n"
                    "$.hud_enabled: required field is missing\n")
        verify("consumer-run", consumer_run.returncode == 0 and consumer_run.stdout == expected,
               f"exit={consumer_run.returncode} stdout={consumer_run.stdout!r} stderr={consumer_run.stderr!r}")

    print(f"json codec library: {passed} checks passed, {len(failed)} failed")
    for name in failed:
        print(f"failed: {name}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
