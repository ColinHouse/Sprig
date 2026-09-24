#!/usr/bin/env python3
"""Regression checks for correctness defects found in the independent audit."""
import json
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / "sprig"
CASES = ROOT / "tests" / "review_cases"
passed = failed = 0


def check(name, ok, detail=""):
    global passed, failed
    if ok:
        passed += 1
        print(f"pass {name}")
    else:
        failed += 1
        print(f"FAIL {name}: {detail}")


def run(*args):
    return subprocess.run([str(SPRIG), *map(str, args)], capture_output=True, text=True)


def diagnostic_codes(proc):
    try:
        return [d.get("code") for d in json.loads(proc.stdout).get("diagnostics", [])]
    except (json.JSONDecodeError, AttributeError):
        return []


def json_result(name, *args, expect_success=False, expected_code=None):
    proc = run(*args, "--json")
    try:
        data = json.loads(proc.stdout)
        valid = data.get("schemaVersion") == 1 and isinstance(data.get("diagnostics"), list)
        codes = {d.get("code") for d in data["diagnostics"]}
        if expected_code:
            valid = valid and expected_code in codes
        if expect_success:
            valid = valid and not data["diagnostics"]
        check(name, valid, f"exit={proc.returncode}, stdout={proc.stdout!r}, stderr={proc.stderr!r}")
        return proc, data
    except (json.JSONDecodeError, KeyError, TypeError) as exc:
        check(name, False, f"invalid JSON ({exc}), stdout={proc.stdout!r}, stderr={proc.stderr!r}")
        return proc, {}


def javac_json_probe():
    with tempfile.TemporaryDirectory(prefix="sprig-json-probe-") as temp:
        jar = ROOT / "build" / "sprig-compiler.jar"
        probe = ROOT / "tests" / "correctness" / "JavacJsonProbe.java"
        compile_probe = subprocess.run(["javac", "-cp", str(jar), "-d", temp, str(probe)],
                                       capture_output=True, text=True)
        if compile_probe.returncode != 0:
            check("json-javac-diagnostic", False, compile_probe.stderr)
            return
        proc = subprocess.run(["java", "-cp", f"{temp}:{jar}", "JavacJsonProbe"],
                              capture_output=True, text=True)
        try:
            data = json.loads(proc.stdout)
            diagnostics = data.get("diagnostics", [])
            valid = (proc.returncode == 0 and data.get("command") == "build"
                     and data.get("exitCode") == 1 and len(diagnostics) == 1
                     and diagnostics[0].get("code") == "SPR-JVM-COMPILE"
                     and diagnostics[0].get("uri") is None
                     and diagnostics[0].get("range") is None)
            check("json-javac-diagnostic", valid,
                  f"exit={proc.returncode}, stdout={proc.stdout!r}, stderr={proc.stderr!r}")
        except (json.JSONDecodeError, AttributeError) as exc:
            check("json-javac-diagnostic", False,
                  f"invalid JSON ({exc}), stdout={proc.stdout!r}, stderr={proc.stderr!r}")


def main():
    null_assignment = run("check", "--json", CASES / "java_nonnull_null.spr")
    check("java-null-rejected", null_assignment.returncode != 0 and
          diagnostic_codes(null_assignment) == ["SPR-TYPE-NULL"] and
          "NullPointerException" not in null_assignment.stdout + null_assignment.stderr,
          f"exit={null_assignment.returncode}, {null_assignment.stdout}{null_assignment.stderr}")
    nullable_call = run("check", "--json", CASES / "jvm_null.spr")
    check("java-platform-nullable-checks", nullable_call.returncode != 0 and
          diagnostic_codes(nullable_call) == ["SPR-TYPE-NULLABLE"])
    narrowed = run("run", CASES / "java_nullable_narrowing.spr")
    check("java-null-check-narrows-result", narrowed.returncode == 0 and narrowed.stdout.strip() == "1",
          f"exit={narrowed.returncode}, {narrowed.stdout}{narrowed.stderr}")
    java_nullable = run("run", CASES / "java_reference_nullable_assignment.spr")
    check("java-reference-result-assigns-to-explicit-nullable", java_nullable.returncode == 0 and
          len(java_nullable.stdout.strip()) == 10,
          f"exit={java_nullable.returncode}, {java_nullable.stdout}{java_nullable.stderr}")
    default_effect = run("check", "--json", CASES / "default_checked_exception.spr")
    check("field-default-effect-propagates", default_effect.returncode != 0 and
          diagnostic_codes(default_effect) == ["SPR-FLOW-THROWS"])
    multiple_effects = run("check", "--json", CASES / "default_multiple_effects.spr")
    check("same-default-effect-reported-once-per-constructor",
          diagnostic_codes(multiple_effects) == ["SPR-FLOW-THROWS"], multiple_effects.stdout)
    override = run("run", CASES / "default_explicit_override.spr")
    check("explicit-constructor-field-skips-default-effect", override.returncode == 0 and
          override.stdout.strip() == "1", f"exit={override.returncode}, {override.stdout}{override.stderr}")
    for fixture in ("unit_field.spr", "unit_param.spr"):
        output = run("check", "--json", CASES / fixture)
        check(f"{fixture}-front-end", output.returncode != 0 and
              diagnostic_codes(output) == ["SPR-TYPE-UNIT"],
              f"exit={output.returncode}, {output.stdout}{output.stderr}")
    unit_collection = run("check", "--json", CASES / "unit_collection.spr")
    check("unit-collection-type-rejected-without-cascade",
          unit_collection.returncode != 0 and diagnostic_codes(unit_collection) == ["SPR-TYPE-UNIT"],
          unit_collection.stdout + unit_collection.stderr)
    fake = run("check", CASES / "fake_generic.spr")
    check("non-generic-type-arguments-rejected", fake.returncode != 0 and
          diagnostic_codes(run("check", "--json", CASES / "fake_generic.spr")) ==
          ["SPR-TYPE-MISMATCH"] * 3)
    lambda_effect = run("check", "--json", CASES / "jvm_lambda_checked.spr")
    check("checked-exception-in-lambda-rejected-before-javac", lambda_effect.returncode != 0 and
          diagnostic_codes(lambda_effect) == ["SPR-FLOW-THROWS"],
          f"exit={lambda_effect.returncode}, {lambda_effect.stdout}{lambda_effect.stderr}")
    inferred = run("run", CASES / "match_inferred_case.spr")
    check("match-inferred-case-runs", inferred.returncode == 0 and inferred.stdout.strip() == "7",
          f"exit={inferred.returncode}, {inferred.stdout}{inferred.stderr}")

    for command, fixture, name in (
        ("check", CASES / "java_nonnull_null.spr", "json-static-error"),
        ("build", CASES / "jvm_lambda_checked.spr", "json-checked-lambda-error"),
        ("run", CASES / "default_runtime.spr", "json-runtime-error"),
        ("check", CASES / "recursive_visitor.spr", "json-check-success"),
        ("build", CASES / "recursive_visitor.spr", "json-build-success"),
        ("run", CASES / "recursive_visitor.spr", "json-run-success"),
    ):
        _, data = json_result(name, command, fixture,
                              expect_success=name.endswith("success"),
                              expected_code=("SPR-RUNTIME-ERROR" if name == "json-runtime-error"
                                             else "SPR-FLOW-THROWS" if name == "json-checked-lambda-error"
                                             else None))
        if name == "json-runtime-error":
            check("json-runtime-failure-exit", data.get("exitCode") == 1)
        if name == "json-run-success":
            check("json-run-carries-program-output", data.get("programOutput", "").strip() == "14")

    javac_json_probe()

    print(f"correctness regressions: {passed} passed, {failed} failed")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
