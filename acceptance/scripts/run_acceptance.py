#!/usr/bin/env python3
"""Independent acceptance checks for the Sprig stage-0 correctness repair.

MUST-PASS: assertions that encode the repair's claimed behavior. A failure here
means the repair does not hold.
FINDINGS: probes that record current behavior for issues the reviewer found;
they are reported, not asserted to be correct.

Written by the acceptance reviewer; does not modify compiler sources.
"""
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / "sprig"
CASES = ROOT / "acceptance" / "cases"
RESULTS = ROOT / "acceptance" / "results"

CHECKS = []          # (name, file, expected_exit, expected_codes or None)
RUNS = []            # (name, file, expected_exit, expected_stdout or None)
FINDINGS = []        # (name, args, note)


def check(name, source, exit_code, codes=None):
    CHECKS.append((name, source, exit_code, codes))


def run(name, source, exit_code, stdout=None):
    RUNS.append((name, source, exit_code, stdout))


# ---------------------------------------------------------------- P1 nullability
check("p1-null-stringbuilder", "p1_java_null_builder.spr", 1, ["SPR-TYPE-NULL"])
check("p1-nullable-to-nonnull-param", "p1_java_nullable_to_param.spr", 1, ["SPR-TYPE-NULLABLE"])
check("p1-null-ctor-field", "p1_java_null_field.spr", 1, ["SPR-TYPE-NULL"])
check("p1-nullable-into-nonnull-field", "p1_java_nullable_into_field.spr", 1, ["SPR-TYPE-NULLABLE"])
check("p1-java-return-deref", "p1_java_api_null_deref.spr", 1, ["SPR-TYPE-NULLABLE"])
check("p1-java-boxed-deref", "p1_java_boxed_nullable.spr", 1, ["SPR-TYPE-NULLABLE"])
run("p1-java-return-narrowing", "p1_java_api_null_narrow.spr", 0, "absent\nseparator-ok\n")
run("p1-explicit-nullable-date", "p1_java_explicit_nullable.spr", 0, "true\nexplicit-absent-ok\n")
run("p1-java-primitives-nonnull", "p1_java_primitive_nonnull.spr", 0, "true\ntrue\n42\n")
run("p1-java-boxed-narrowing", "p1_java_boxed_narrow.spr", 0, "42\n")

# --------------------------------------------------------- P1 default effects
check("p1-default-two-effects", "p1_default_two_effects.spr", 1,
      ["SPR-FLOW-THROWS", "SPR-FLOW-THROWS"])
check("p1-default-indirect", "p1_default_indirect.spr", 1, ["SPR-FLOW-THROWS"])
check("p1-default-java-checked", "p1_default_java_checked.spr", 1, ["SPR-FLOW-THROWS"])
check("p1-default-mixed-override", "p1_default_mixed_override_bad.spr", 1, ["SPR-FLOW-THROWS"])
run("p1-default-try-catch-runs", "p1_default_try_catch.spr", 0, "-1\n")
run("p1-default-propagation-runs", "p1_default_propagation.spr", 0, "caught: propagated\n")
run("p1-default-all-explicit-runs", "p1_default_mixed_override_ok.spr", 0, "3\n")
FINDINGS.append(("p1-default-runtime-top",
                 ["run", "--json", str(CASES / "p1_default_runtime_top.spr")],
                 "top-level omitted default escapes as SPR-RUNTIME-ERROR (documented)"))

# ------------------------------------------------------------------ P1 Unit
for name in ["p1_unit_field", "p1_unit_param", "p1_unit_list", "p1_unit_nullable",
             "p1_unit_bind", "p1_unit_lambda_param", "p1_unit_nested_print",
             "p1_unit_java_void_bind"]:
    check(f"unit-{name}", f"{name}.spr", 1, ["SPR-TYPE-UNIT"])
check("unit-return-nullable", "p1_unit_return_nullable.spr", 1, ["SPR-TYPE-MISMATCH"])
check("unit-in-int-return", "p1_unit_in_int_return.spr", 1, ["SPR-TYPE-RETURN"])
run("unit-legal-usage", "p1_unit_legal.spr", 0,
    "[log] hello\n[log] again\n[log] again\nfrom method\n1\n")

# ------------------------------------------------------------- P2 generics
# v0.8 uses the dedicated generic arity code for user type arguments.
check("generic-user-class", "p2_generic_user_class.spr", 1, ["SPR-TYPE-GENERIC-ARITY"])
check("generic-native", "p2_generic_native.spr", 1, ["SPR-TYPE-GENERIC-ARITY"])
check("generic-java-import", "p2_generic_java.spr", 1, ["SPR-TYPE-GENERIC-ARITY"])
check("generic-nested-arg", "p2_generic_nested_bad.spr", 1, ["SPR-TYPE-GENERIC-ARITY"])
check("generic-arity", "p2_generic_arity.spr", 1, ["SPR-TYPE-MISMATCH", "SPR-TYPE-MISMATCH"])
check("generic-unknown-arg", "p2_generic_unknown_arg.spr", 1, ["SPR-NAME-UNRESOLVED"])
run("generic-valid-collections", "p2_generic_valid.spr", 0, "6\n[1, 3]\n[evens, odds]\n")

# --------------------------------------------------------- P2 case matching
run("case-singleton-match", "p2_case_match_singleton.spr", 0, "7\n")
run("case-to-parent-match", "p2_case_to_parent.spr", 0, "7\n")
check("case-sibling-branch-unreachable", "p2_case_sibling_branch.spr", 1, ["SPR-FLOW-UNREACHABLE"])
check("case-duplicate", "p2_case_duplicate.spr", 1, ["SPR-MATCH-DUPLICATE"])
check("case-binder-escape", "p2_case_binder_escape.spr", 1, ["SPR-NAME-UNRESOLVED"])
check("case-wildcard", "p2_case_wildcard.spr", 1, ["SPR-MATCH-UNKNOWN-CASE"])

# ------------------------------------------------------------------ numeric
check("num-range-64", "num_range_64.spr", 1, ["SPR-NUM-RANGE"])
check("num-range-32", "num_range_32.spr", 1, ["SPR-NUM-RANGE"])
check("num-static-division", "num_static_int_division.spr", 1, ["SPR-NUM-DIVISION"])
run("num-div-trunc", "num_div_trunc.spr", 0, "2\n-2\n-2\n2\n")
run("num-mix-int32-int", "num_int32_int_mix.spr", 0, "12\n5\n")
run("num-decimal-bigint", "num_decimal_bigint.spr", 0,
    "0.3\ntrue\n0.333333\n15\n42\n"
    "15241578753238836750495351562536198787501905199875019052100\n52\n7\ntrue\n")
for name in ["num_overflow_runtime", "num_min_div", "num_min_negate",
             "num_int32_overflow", "num_div_zero"]:
    FINDINGS.append((name, ["run", "--json", str(CASES / f"{name}.spr")],
                     "checked arithmetic raises SprigNumericError at runtime"))

# --------------------------------------------------------- repaired F1-F4 regressions
CHECKS += [
    ("incomplete-expression-keeps-syntax-diagnostic", "json_syntax_error.spr", 1,
     ["SPR-SYNTAX-ERROR"]),
    ("nullable-java-argument-rejected", "p1_nullable_into_java_formal.spr", 1,
     ["SPR-TYPE-NULLABLE"]),
]
RUNS += [
    ("zero-argument-lambda-runs", "p2_lambda_zero_arg.spr", 0, "42\n"),
    ("int32-compound-literal-context", "p2_compound_literal.spr", 0, "6\n"),
    ("int32-compound-explicit-control", "p2_compound_literal_control.spr", 0, "6\n"),
]


def codes_of(stdout):
    if not stdout.lstrip().startswith("{"):
        return None
    try:
        data = json.loads(stdout)
    except json.JSONDecodeError:
        return None
    if not isinstance(data, dict):
        return None
    return [d.get("code") for d in data.get("diagnostics", [])]


def main():
    RESULTS.mkdir(parents=True, exist_ok=True)
    (CASES / "json_syntax_error.spr").write_text("let x = \n", encoding="utf-8")
    failures = []
    records = []

    def record(kind, name, proc, expected_exit, expected_codes=None, expected_stdout=None):
        actual_codes = codes_of(proc.stdout)
        problems = []
        if expected_exit is not None and proc.returncode != expected_exit:
            problems.append(f"exit {proc.returncode} != {expected_exit}")
        if expected_codes is not None and actual_codes != expected_codes:
            problems.append(f"codes {actual_codes} != {expected_codes}")
        if expected_stdout is not None and proc.stdout != expected_stdout:
            problems.append(f"stdout {proc.stdout!r} != {expected_stdout!r}")
        ok = not problems
        if not ok:
            failures.append(name)
        records.append({"kind": kind, "name": name, "args": None, "exit": proc.returncode,
                        "codes": actual_codes, "stdout": proc.stdout[:400],
                        "problems": problems, "ok": ok})
        print(f"{'pass' if ok else 'FAIL'} {kind} {name}"
              + ("" if ok else f"  ({'; '.join(problems)})"))

    for name, source, exit_code, expected_codes in CHECKS:
        proc = subprocess.run([SPRIG, "check", "--json", CASES / source],
                              capture_output=True, text=True)
        record("check", name, proc, exit_code, expected_codes=expected_codes)
    for name, source, exit_code, expected_stdout in RUNS:
        proc = subprocess.run([SPRIG, "run", CASES / source], capture_output=True, text=True)
        record("run", name, proc, exit_code, expected_stdout=expected_stdout)

    finding_records = []
    for name, args, note in FINDINGS:
        if name == "front-end-crash-incomplete-expression":
            args = ["check", "--json", "/tmp/sprig_acceptance_incomplete.spr"]
        elif name == "compound-literal":
            continue
        proc = subprocess.run([SPRIG, *args], capture_output=True, text=True)
        finding_records.append({
            "name": name, "note": note, "exit": proc.returncode,
            "codes": codes_of(proc.stdout),
            "stdout_head": proc.stdout[:400],
            "stderr_head": proc.stderr[:300],
        })
        print(f"finding {name}: exit={proc.returncode} codes={codes_of(proc.stdout)}")
    (RESULTS / "acceptance_checks.json").write_text(json.dumps(records, indent=2), encoding="utf-8")
    (RESULTS / "acceptance_findings.json").write_text(
        json.dumps(finding_records, indent=2), encoding="utf-8")
    print(f"must-pass checks: {len(records) - len(failures)}/{len(records)} passed")
    if failures:
        print("failures: " + ", ".join(failures))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
