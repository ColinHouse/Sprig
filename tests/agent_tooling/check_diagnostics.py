#!/usr/bin/env python3
"""Mechanical consistency for stable codes, CodeDocs, explain and docs."""
from pathlib import Path
import json
import re
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / ("sprig.cmd" if sys.platform == "win32" else "sprig")
CODES_JAVA = ROOT / "compiler/src/main/java/sprig/compiler/diag/Codes.java"
CODE_DOCS = ROOT / "compiler/src/main/java/sprig/compiler/diag/CodeDocs.java"
EXPLANATIONS = ROOT / "compiler/src/main/java/sprig/compiler/diag/Explanations.java"
DOC_TABLE = ROOT / "docs/tooling/diagnostic-codes.md"

HIGH_VALUE = [
    "SPR-MATCH-RESULT", "SPR-MATCH-INFERENCE", "SPR-MODULE-EXPORT", "SPR-MODULE-EXPORT-ORDER",
    "SPR-TYPE-INFER", "SPR-TYPE-RETURN", "SPR-TYPE-ASSIGN", "SPR-TYPE-OPERAND",
    "SPR-TYPE-NOT-CALLABLE", "SPR-TYPE-UNIT", "SPR-CALL-ARITY", "SPR-CALL-MISSING-FIELD",
    "SPR-CALL-UNKNOWN-FIELD", "SPR-CALL-DUPLICATE-FIELD", "SPR-PROJECT-MANIFEST",
    "SPR-PROJECT-ENTRY", "SPR-PROJECT-LOCK-MISSING", "SPR-PROJECT-LOCK-STALE",
    "SPR-DEP-OFFLINE", "SPR-DEP-CHECKSUM", "SPR-DEP-MAVEN", "SPR-DEP-NOT-FOUND",
    "SPR-JVM-MEMBER", "SPR-JVM-AMBIGUOUS", "SPR-JVM-CLASS", "SPR-SYNTAX-ERROR",
    "SPR-LEX-INDENT-INCONSISTENT",
]

GENERIC_MARKERS = (
    "The rule keeps Sprig programs explicit and statically predictable.",
    "Inspect the source range and expected/actual types, then change the program explicitly.",
)

FAILURES = []


def check(name, ok, detail=""):
    print(("PASS " if ok else "FAIL ") + name)
    if not ok:
        FAILURES.append(f"{name}: {detail}")


def sprig(*args):
    return subprocess.run([str(SPRIG), *map(str, args)], cwd=ROOT,
                          capture_output=True, text=True, encoding="utf-8")


def explain(code):
    result = sprig("explain", code, "--json")
    assert result.returncode == 0, (code, result.stdout, result.stderr)
    return json.loads(result.stdout)


def main():
    codes_text = CODES_JAVA.read_text(encoding="utf-8")
    constants = dict(re.findall(r'public static final String (\w+) = "([^"]+)";', codes_text))
    codes = sorted(constants.values())
    code_docs_text = CODE_DOCS.read_text(encoding="utf-8")
    documented = {constants[c] for c in re.findall(r"Map\.entry\(Codes\.(\w+),", code_docs_text)}
    check("codedocs-covers-every-stable-code", documented == set(codes),
          f"missing={sorted(set(codes) - documented)} extra={sorted(documented - set(codes))}")

    codes_json = json.loads(sprig("codes", "--json").stdout)["codes"]
    api_codes = sorted(item["code"] for item in codes_json)
    check("sprig-codes-matches-codes-java", api_codes == codes,
          f"missing={sorted(set(codes) - set(api_codes))} extra={sorted(set(api_codes) - set(codes))}")

    table = DOC_TABLE.read_text(encoding="utf-8")
    table_codes = set(re.findall(r"\| (SPR-[A-Z0-9-]+) \|", table))
    check("diagnostic-codes-doc-covers-sprig-codes", set(api_codes) <= table_codes,
          f"missing={sorted(set(api_codes) - table_codes)}")
    check("diagnostic-codes-doc-has-no-unknown-codes", table_codes <= set(codes),
          f"unknown={sorted(table_codes - set(codes))}")

    # Every explain payload must be schema-valid, actionable and deterministic.
    help_topics = json.loads(sprig("help", "--json").stdout)["topics"]
    topic_ok = {}
    for topic in help_topics:
        topic_ok[topic] = sprig("help", topic, "--json").returncode == 0
    catalog_version = json.loads(sprig("capabilities", "--json").stdout)["compilerVersion"]
    bad_fields = []
    bad_topics = []
    bad_related = []
    non_deterministic = []
    machine_applicable = []
    known_count = 0
    for code in codes:
        first = explain(code)
        second = explain(code)
        if first != second:
            non_deterministic.append(code)
        if not (first.get("schemaVersion") == 1 and first.get("code") == code
                and first.get("compilerVersion") == catalog_version and first.get("known") is True):
            bad_fields.append(code)
            continue
        known_count += 1
        if not first.get("meaning") or first["meaning"].startswith("Unknown"):
            bad_fields.append(code)
        if first.get("documentationTopic") not in topic_ok:
            bad_topics.append(code)
        elif not topic_ok[first["documentationTopic"]]:
            bad_topics.append(code)
        if not first.get("whyMatters") or not first.get("commonCauses") or not first.get("safeFixes"):
            bad_fields.append(code)
        for related in first.get("relatedCodes") or []:
            if related not in codes:
                bad_related.append((code, related))
        repair = first.get("repair")
        if repair is not None:
            if set(repair) != {"kind", "machineApplicable"} or not isinstance(repair["machineApplicable"], bool):
                bad_fields.append(code)
            if repair["machineApplicable"]:
                machine_applicable.append(code)
    check("explain-every-known-code", known_count == len(codes), f"{known_count}/{len(codes)}")
    check("explain-schema-valid", not bad_fields, bad_fields[:5])
    check("explain-topics-exist", not bad_topics, bad_topics[:5])
    check("explain-related-codes-exist", not bad_related, bad_related[:5])
    check("explain-deterministic", not non_deterministic, non_deterministic[:5])
    check("explain-never-machine-applicable", not machine_applicable, machine_applicable)

    # Agent-facing steering must stay concrete for common learning errors.
    placeholder = []
    thin = []
    for code in HIGH_VALUE:
        payload = explain(code)
        text = " ".join([payload["whyMatters"], *payload["commonCauses"], *payload["safeFixes"]])
        if any(marker in text for marker in GENERIC_MARKERS):
            placeholder.append(code)
        if len(payload["commonCauses"]) < 2 or len(payload["safeFixes"]) < 2:
            thin.append(code)
    check("high-value-explanations-not-placeholder", not placeholder, placeholder)
    check("high-value-explanations-actionable", not thin, thin)

    steering = {
        "SPR-MODULE-EXPORT": "sprig api",
        "SPR-JVM-MEMBER": "sprig api",
        "SPR-PROJECT-LOCK-STALE": "sprig resolve",
        "SPR-PROJECT-LOCK-MISSING": "sprig resolve",
        "SPR-DEP-OFFLINE": "sprig resolve",
        "SPR-LEX-INDENT-INCONSISTENT": "space",
    }
    missing_steering = []
    for code, marker in steering.items():
        fixes = " ".join(explain(code)["safeFixes"]).lower()
        if marker not in fixes:
            missing_steering.append((code, marker))
    check("high-value-explanations-steer-to-tools", not missing_steering, missing_steering)

    unknown = explain("SPR-NOT-A-REAL-CODE")
    check("unknown-code-honest", unknown["known"] is False
          and "Unknown" in unknown["meaning"] and unknown["documentationTopic"] in topic_ok,
          unknown)

    # An unclosed grouping delimiter is reported at its opener, with EOF coordinates
    # kept inside the source and parser recovery bounded to one follow-on diagnostic.
    unclosed_sources = (
        ("paren", "let a = (1 + 2\nprint(a)\n", 0, 8),
        ("brace", "let a = 1 {\nlet b = 2\nlet c = 3\nlet d = 4\nlet e = 5\nlet f = 6\nprint(a)\n", 0, 10),
    )
    with tempfile.TemporaryDirectory() as tmp:
        for name, source, line, column in unclosed_sources:
            path = Path(tmp) / (name + ".spr")
            path.write_text(source, encoding="utf-8")
            result = sprig("check", path, "--json")
            diagnostics = json.loads(result.stdout)["diagnostics"]
            first = diagnostics[0] if diagnostics else {}
            start = first.get("range", {}).get("start", {})
            end = first.get("range", {}).get("end", {})
            ok = (result.returncode == 1 and first.get("code") == "SPR-LEX-UNCLOSED"
                  and start == {"line": line, "character": column}
                  and end == {"line": line, "character": column + 1}
                  and len(diagnostics) <= 2)
            check("unclosed-delimiter-at-opener-" + name, ok, json.dumps(diagnostics))

    print(f"diagnostics: {len(codes)} stable codes, {known_count} explain payloads, "
          f"{len(HIGH_VALUE)} high-value checks")
    if FAILURES:
        for failure in FAILURES:
            print("FAIL", failure)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
