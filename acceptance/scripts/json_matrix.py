#!/usr/bin/env python3
"""Independent JSON/CLI failure-path matrix for the Sprig stage-0 compiler.

Runs each case, requires stdout to be a single parseable JSON document in JSON
mode, records exit codes, code sets, whether program output leaked to stdout,
and stderr content. Written by the acceptance reviewer (not the implementer).
"""
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / "sprig"
OUT = ROOT / "acceptance" / "results"
CASES = ROOT / "acceptance" / "cases"

SYNTAX_BAD = CASES / "json_syntax_error.spr"
MISSING = CASES / "does_not_exist.spr"


def write_syntax_bad():
    SYNTAX_BAD.write_text("let x = \n", encoding="utf-8")


def run_case(name, args, expect_json=True, expect_exit=None, expected_codes=None,
             expect_program_output=None):
    proc = subprocess.run([str(SPRIG), *map(str, args)], capture_output=True, text=True)
    record = {
        "name": name,
        "args": [str(a) for a in args],
        "exit": proc.returncode,
        "stdout_is_single_json": False,
        "stdout_parse_error": None,
        "codes": None,
        "uri_null_count": None,
        "range_null_count": None,
        "program_output": None,
        "stderr_nonempty": bool(proc.stderr.strip()),
        "stderr_head": proc.stderr[:300],
        "ok": True,
        "problems": [],
    }
    data = None
    if expect_json:
        try:
            data = json.loads(proc.stdout)
            record["stdout_is_single_json"] = True
        except json.JSONDecodeError as exc:
            record["stdout_parse_error"] = str(exc)
            record["problems"].append("stdout is not parseable JSON")
    else:
        data = None
    if data is not None:
        diagnostics = data.get("diagnostics", [])
        record["codes"] = [d.get("code") for d in diagnostics]
        record["uri_null_count"] = sum(1 for d in diagnostics if d.get("uri") is None)
        record["range_null_count"] = sum(1 for d in diagnostics if d.get("range") is None)
        record["program_output"] = data.get("programOutput")
    if expect_exit is not None and proc.returncode != expect_exit:
        record["problems"].append(f"exit {proc.returncode} != expected {expect_exit}")
    if expected_codes is not None and record["codes"] != expected_codes:
        record["problems"].append(f"codes {record['codes']} != expected {expected_codes}")
    if expect_program_output is not None and record["program_output"] != expect_program_output:
        record["problems"].append(
            f"programOutput {record['program_output']!r} != {expect_program_output!r}")
    record["ok"] = not record["problems"]
    return record


def main():
    write_syntax_bad()
    results = [
        run_case("check-json-success", ["check", "--json", ROOT / "tests/runtime/01_arithmetic.spr"],
                 expect_exit=0, expected_codes=[]),
        run_case("check-json-type-error", ["check", "--json", CASES / "p1_java_null_builder.spr"],
                 expect_exit=1, expected_codes=["SPR-TYPE-NULL"]),
        run_case("check-json-syntax-error", ["check", "--json", SYNTAX_BAD],
                 expect_exit=1, expected_codes=["SPR-SYNTAX-ERROR"]),
        run_case("check-json-syntax-error-syntax-only",
                 ["check", "--syntax-only", "--json", SYNTAX_BAD],
                 expect_exit=1, expected_codes=["SPR-SYNTAX-ERROR"]),
        run_case("check-json-nonstatic-program",
                 ["check", "--json", CASES / "p1_default_try_catch.spr"],
                 expect_exit=0, expected_codes=[]),
        run_case("build-json-success",
                 ["build", "--json", ROOT / "tests/review_cases/recursive_visitor.spr", "-d",
                  ROOT / "build/acceptance_build_ok"],
                 expect_exit=0, expected_codes=[]),
        run_case("run-json-success-with-output",
                 ["run", "--json", CASES / "p1_unit_legal.spr"],
                 expect_exit=0, expected_codes=[],
                 expect_program_output="[log] hello\n[log] again\n[log] again\nfrom method\n1\n"),
        run_case("run-json-runtime-error",
                 ["run", "--json", CASES / "p1_default_runtime_top.spr"],
                 expect_exit=1, expected_codes=["SPR-RUNTIME-ERROR"]),
    run_case("run-json-zero-arg-lambda",
                 ["run", "--json", CASES / "p2_lambda_zero_arg.spr"],
                 expect_exit=0, expected_codes=[], expect_program_output="42\n"),
        run_case("check-json-missing-file", ["check", "--json", MISSING], expect_exit=1),
        run_case("run-json-missing-file", ["run", "--json", MISSING], expect_exit=1),
    ]
    # Internal-error path: point -d at an existing regular file so directory
    # creation fails inside the build command.
    blocker = ROOT / "build" / "acceptance_blocker"
    blocker.parent.mkdir(parents=True, exist_ok=True)
    blocker.write_text("not a directory", encoding="utf-8")
    results.append(run_case(
        "build-json-io-failure",
        ["build", "--json", ROOT / "examples/hello.spr", "-d", blocker],
        expect_exit=2, expected_codes=["SPR-JVM-INTERNAL"]))
    # Non-JSON run of a failing program must keep human-readable output.
    results.append(run_case("run-text-runtime-error",
                            ["run", CASES / "p1_default_runtime_top.spr"],
                            expect_json=False, expect_exit=1))
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "json_matrix.json").write_text(json.dumps(results, indent=2), encoding="utf-8")
    ok = all(r["ok"] for r in results)
    for r in results:
        marker = "pass" if r["ok"] else "FAIL"
        print(f"{marker} {r['name']} exit={r['exit']} codes={r['codes']} "
              f"json={r['stdout_is_single_json']} stderr={'yes' if r['stderr_nonempty'] else 'no'}")
        for problem in r["problems"]:
            print(f"      {problem}")
    print(f"json matrix: {sum(1 for r in results if r['ok'])}/{len(results)} passed")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
