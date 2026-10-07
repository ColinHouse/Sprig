#!/usr/bin/env python3
"""Regression checks for correctness defects found in the independent audit."""
import os
import json
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
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
                                       capture_output=True, text=True, errors="replace")
        if compile_probe.returncode != 0:
            check("json-javac-diagnostic", False, compile_probe.stderr)
            return
        # A localized javac message must arrive as UTF-8 (JDK 19+ otherwise uses the ANSI code page).
        proc = subprocess.run(["java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                               "-cp", os.pathsep.join([temp, str(jar)]), "JavacJsonProbe"],
                              capture_output=True, text=True, encoding="utf-8")
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


def sealed_variant_lowering():
    """feature-status documents variants as a sealed interface with final cases."""
    with tempfile.TemporaryDirectory(prefix="sprig-sealed-") as work:
        source = Path(work) / "shape.spr"
        source.write_text("variant Shape:\n    Circle(radius: Float)\n    Point\n\nprint(Shape.Point)\n",
                          encoding="utf-8")
        out = Path(work) / "out"
        result = run("build", source, "--emit-java-only", "-d", out)
        generated = list(out.rglob("$Shape.java"))
        java = generated[0].read_text(encoding="utf-8") if generated else ""
        check("variant-lowers-to-sealed-interface", result.returncode == 0
              and "public sealed interface $Shape" in java
              and "final class Circle implements $Shape" in java
              and "final class Point implements $Shape" in java,
              f"exit={result.returncode} {result.stdout}{result.stderr}{java[:400]}")


def string_join_lowering():
    """String, Int, Int32, Float, Float32 and Bool join with Java's own concatenation.

    Each SprigRuntime.str call inlines the general formatter; in one long
    top-level block those copies exhausted HotSpot's inlining budget and left a
    later hot loop's checked arithmetic as real calls (about 5x slower). Other
    values still go through str, which renders lists, maps and errors.
    """
    with tempfile.TemporaryDirectory(prefix="sprig-join-") as work:
        source = Path(work) / "join.spr"
        source.write_text("func describe(count: Int, small: Int32, ratio: Float, narrow: Float32, ok: Bool,\n"
                          "             name: String, items: List[Int]) -> String:\n"
                          '    var text = "n=" + count + small + ratio + narrow + ok + name + " items=" + items\n'
                          "    text += count\n"
                          "    return text\n\n"
                          'print(describe(3, 4, 0.5, 1.5, true, "pen", [1, 2]))\n', encoding="utf-8")
        out = Path(work) / "out"
        result = run("build", source, "--emit-java-only", "-d", out)
        generated = list(out.rglob("$M_join.java"))
        java = generated[0].read_text(encoding="utf-8") if generated else ""
        joins = [line.strip() for line in java.splitlines() if "text = " in line]
        scalar_str = [name for name in ("count", "small", "ratio", "narrow", "ok", "name")
                      if f"SprigRuntime.str({name})" in java]
        check("string-join-scalars-use-java-concatenation", result.returncode == 0 and len(joins) == 2
              and not scalar_str and '"n=" + count' in java and "SprigRuntime.str(items)" in java
              and "(text + count)" in java,
              f"exit={result.returncode} str={scalar_str} {result.stdout}{result.stderr}{joins}")
        ran = run("run", source)
        check("string-join-scalars-render-as-print", ran.returncode == 0
              and ran.stdout.strip() == "n=340.51.5truepen items=[1, 2]3", f"{ran.stdout!r} {ran.stderr!r}")


def run_program(name, source, expected):
    """Check, compile with javac and run one program; the output must match."""
    with tempfile.TemporaryDirectory(prefix="sprig-run-") as work:
        path = Path(work) / (name + ".spr")
        path.write_text(source, encoding="utf-8")
        ran = run("run", path)
        check(name, ran.returncode == 0 and ran.stdout == expected, f"{ran.stdout!r} {ran.stderr!r}")


def generic_compound_assignment():
    """A collection typed with a type parameter is raw in Java, so get() is an Object.

    `m[key] += 1` on a MutableMap[K, String] or MutableMap[K, Int] passed check and
    then failed in javac; the old value is now read as the element's concrete type.
    """
    run_program("generic-compound-assignment",
                "generic K:\n    func bump(m: MutableMap[K, String], key: K) -> Unit:\n        m[key] += 1\n\n"
                "generic K:\n    func count(m: MutableMap[K, Int], key: K) -> Unit:\n        m[key] += 1\n\n"
                "generic T:\n    class Names:\n        let names: MutableMap[T, String]\n\n"
                "        func tag(key: T) -> Unit:\n            names[key] += true\n\n"
                'let m: MutableMap[Int, String] = {1: "a"}\nbump[Int](m, 1)\nprint(m)\n'
                'let c: MutableMap[String, Int] = {"x": 1}\ncount[String](c, "x")\nprint(c)\n'
                'let n = Names[Int](names={2: "b"})\nn.tag(2)\nprint(n.names)\n',
                "{1: a1}\n{x: 2}\n{2: btrue}\n")


def java_to_string():
    """toString() works on an interface-typed Java value, and an overload with arguments stays Java's."""
    run_program("java-to-string",
                "import java.util.ArrayList as ArrayList\nimport java.math.BigInteger as BigInteger\n\n"
                'let xs = ArrayList[String]()\nxs.add("a")\nxs.add("b")\nlet head = xs.subList(0, 1)\n'
                'if head != null:\n    print("head " + head.toString())\n'
                "let big = BigInteger.valueOf(255)\nif big != null:\n    print(big.toString(16))\n    print(big.toString())\n",
                "head [a]\nff\n255\n")


def main():
    sealed_variant_lowering()
    string_join_lowering()
    generic_compound_assignment()
    java_to_string()
    null_assignment = run("check", "--json", CASES / "java_nonnull_null.spr")
    check("java-null-rejected", null_assignment.returncode != 0 and
          diagnostic_codes(null_assignment) == ["SPR-TYPE-NULL"] and
          "NullPointerException" not in null_assignment.stdout + null_assignment.stderr,
          f"exit={null_assignment.returncode}, {null_assignment.stdout}{null_assignment.stderr}")
    nullable_call = run("check", "--json", CASES / "jvm_null.spr")
    check("java-platform-nullable-checks", nullable_call.returncode != 0 and
          diagnostic_codes(nullable_call) == ["SPR-TYPE-NULLABLE"])
    narrowed = run("run", CASES / "java_nullable_narrowing.spr")
    # The fixture prints the length of the JVM line separator: 2 (CRLF) on Windows.
    check("java-null-check-narrows-result", narrowed.returncode == 0
          and narrowed.stdout.strip() == str(len(os.linesep)),
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
    # v0.8: non-generic types with type arguments report the dedicated
    # generic-arity code instead of the old placeholder TYPE-MISMATCH.
    check("non-generic-type-arguments-rejected", fake.returncode != 0 and
          diagnostic_codes(run("check", "--json", CASES / "fake_generic.spr")) ==
          ["SPR-TYPE-GENERIC-ARITY"] * 3)
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
