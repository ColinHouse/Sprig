#!/usr/bin/env python3
"""Offline, real-process checks for the alpha.2 agent-facing CLI."""

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / "sprig"
COUNT = 0


def run(*args, env=None):
    return subprocess.run([str(SPRIG), *map(str, args)], cwd=ROOT, env=env,
                          text=True, capture_output=True)


def check(name, ok):
    global COUNT
    if not ok:
        raise AssertionError(name)
    COUNT += 1
    print("pass", name)


def obj(proc):
    return json.loads(proc.stdout)


def main():
    catalog = obj(run("capabilities", "--json"))
    check("catalog-version", catalog["compilerVersion"] == "0.1.0-alpha.2"
          and catalog["languageVersion"] == "0.7" and catalog["jdk"]["minimum"] == 17)
    check("catalog-types", catalog["collectionTypes"] ==
          ["List[T]", "MutableList[T]", "Map[K,V]", "MutableMap[K,V]"])
    check("catalog-commands", all(command in catalog["commands"] for command in
          ("help", "capabilities", "api", "doctor", "check", "build", "run")))
    capability_tests = json.loads((ROOT / "tests/agent_tooling/capability-test-map.json").read_text())
    for capability, fixtures in capability_tests.items():
        value = catalog["features"].get(capability.removeprefix("feature.")) \
            if capability.startswith("feature.") else catalog.get(capability)
        check("capability-test-map-" + capability,
              value is not None and all((ROOT / item.split("#", 1)[0]).is_file() for item in fixtures))
    check("capabilities-text", "Implemented:" in run("capabilities").stdout)
    topics = obj(run("help", "--json"))["topics"]
    for topic in topics:
        result = run("help", topic, "--json")
        data = obj(result)
        check("help-" + topic, result.returncode == 0 and data["topic"] == topic
              and data["compilerVersion"] == catalog["compilerVersion"]
              and data["syntax"] and data["rules"])
        for example in data["examples"]:
            example_path = ROOT / example
            if example_path.suffix == ".spr":
                parsed = run("check", example_path, "--json")
                check("help-example-check-" + topic,
                      parsed.returncode == 0 and not obj(parsed)["diagnostics"])
                executed = run("run", example_path, "--json")
                check("help-example-run-" + topic,
                      executed.returncode == 0 and not obj(executed)["diagnostics"])
    check("help-text", "Syntax:" in run("help", "match").stdout)
    check("help-unknown-json", obj(run("help", "invalid", "--json"))["exitCode"] == 2)
    doctor = obj(run("doctor", "--json"))
    check("doctor", doctor["javacAvailable"] and doctor["antlrAvailable"]
          and doctor["compilerVersion"] == catalog["compilerVersion"])
    check("doctor-text", "javaVersion:" in run("doctor").stdout)
    explanation = obj(run("explain", "SPR-TYPE-NULLABLE", "--json"))
    check("explain", explanation["known"] and explanation["goodExample"]
          and explanation["documentationTopic"] == "nullability")
    local_date = obj(run("api", "java.time.LocalDate", "--json"))
    check("api-jdk", local_date["className"] == "java.time.LocalDate"
          and any(m["name"] == "of" for m in local_date["staticMethods"]))
    check("api-text", "Java API: java.time.LocalDate" in run("api", "java.time.LocalDate").stdout)
    files = obj(run("api", "java.nio.file.Files", "--json"))
    arrays = [m for m in files["staticMethods"] if m["name"] == "readAllBytes"]
    check("api-array-boundary", arrays and not arrays[0]["usableFromSprig"])

    with tempfile.TemporaryDirectory(prefix="sprig-agent-tooling-") as tmp:
        directory = Path(tmp)
        source = directory / "src" / "probe" / "Widget.java"
        source.parent.mkdir(parents=True)
        source.write_text('''package probe;
public final class Widget {
    static { try { java.nio.file.Files.writeString(
        java.nio.file.Path.of(System.getenv("SPRIG_INIT_MARKER")), "initialized");
    } catch (Exception e) { throw new RuntimeException(e); } }
    public static String echo(String value) { return value; }
    public static int plus(int a, int b) { return a + b; }
    public static String[] array() { return new String[]{"a"}; }
    public static String join(String... values) { return String.join(",", values); }
}''', encoding="utf-8")
        classes = directory / "classes"
        classes.mkdir()
        subprocess.run(["javac", "-d", str(classes), str(source)], check=True)
        jar = directory / "probe.jar"
        subprocess.run(["jar", "--create", "--file", str(jar), "-C", str(classes), "."], check=True)
        marker = directory / "initialized"
        env = dict(os.environ, SPRIG_INIT_MARKER=str(marker))
        api = obj(run("api", "probe.Widget", "--classpath", jar, "--json", env=env))
        check("api-jar-no-init", api["className"] == "probe.Widget" and not marker.exists())
        methods = {m["name"]: m for m in api["staticMethods"]}
        check("api-varargs-boundary", methods["join"]["unusableReason"] is not None)
        check("api-primitive-map", methods["plus"]["sprigReturnType"] == "Int32")
        program = directory / "valid.spr"
        program.write_text('''import probe.Widget as Widget
let value = Widget.echo("ok")
if value != null:
    print(value)
''', encoding="utf-8")
        checked = run("check", program, "--classpath", jar, "--json", env=env)
        check("classpath-check", checked.returncode == 0
              and obj(checked)["environment"]["classpath"] == [str(jar)] and not marker.exists())
        built = run("build", program, "--classpath", jar, "-d", directory / "build", "--json", env=env)
        check("classpath-build", built.returncode == 0 and not marker.exists())
        executed = run("run", program, "--classpath", jar, "--json", env=env)
        check("classpath-run", executed.returncode == 0
              and obj(executed)["programOutput"] == "ok\n" and marker.exists())
        alt_source = directory / "alt" / "probe" / "Widget.java"
        alt_source.parent.mkdir(parents=True)
        alt_source.write_text('''package probe;
public final class Widget {
    public static String echo(String value) { return "second"; }
}''', encoding="utf-8")
        alt_classes = directory / "altclasses"
        alt_classes.mkdir()
        subprocess.run(["javac", "-d", str(alt_classes), str(alt_source)], check=True)
        alt_jar = directory / "alt.jar"
        subprocess.run(["jar", "--create", "--file", str(alt_jar),
                        "-C", str(alt_classes), "."], check=True)
        first = run("run", program, "--classpath", jar, "--classpath", alt_jar,
                    "--json", env=env)
        second = run("run", program, "--classpath", alt_jar, "--classpath", jar,
                     "--json", env=env)
        check("classpath-duplicate-order", first.returncode == 0 and second.returncode == 0
              and obj(first)["programOutput"] == "ok\n"
              and obj(second)["programOutput"] == "second\n")
        joined = run("check", program, "--classpath", os.pathsep.join([str(jar), str(alt_jar)]),
                     "--json", env=env)
        check("classpath-path-separator", joined.returncode == 0 and
              obj(joined)["environment"]["classpath"] == [str(jar), str(alt_jar)])
        bad = directory / "bad.spr"
        bad.write_text('''import probe.Widget as Widget
Widget.plus("bad")
''', encoding="utf-8")
        failure = run("check", bad, "--classpath", jar, "--json", env=env)
        errors = obj(failure)["diagnostics"]
        check("overload-candidates", failure.returncode == 1 and any(
            d["code"] == "SPR-JVM-MEMBER" and d["data"]["candidates"]
            and d["data"]["candidates"][0]["rejectedBecause"] == "wrong arity"
            for d in errors))
        absent = run("check", program, "--classpath", directory / "missing.jar", "--json")
        check("missing-classpath", absent.returncode == 2 and obj(absent)["exitCode"] == 2
              and obj(absent)["diagnostics"][0]["code"] == "SPR-JVM-CLASSPATH")
        nullable = directory / "nullable.spr"
        nullable.write_text('''import java.util.Objects as Objects
let value: String? = null
Objects.requireNonNull(value)
''', encoding="utf-8")
        rejected = run("check", nullable, "--json")
        check("nullable-object-rejected", rejected.returncode == 1
              and any(d["code"] == "SPR-TYPE-NULLABLE" for d in obj(rejected)["diagnostics"]))
        cli = run("check", "--json")
        check("missing-file-json", cli.returncode == 2 and
              obj(cli)["diagnostics"][0]["code"] == "SPR-CLI-OPTION" and not cli.stderr)
        edge_source = directory / "src" / "probe" / "NumericEdges.java"
        edge_source.write_text('''package probe;
public final class NumericEdges {
    public static char raw = 'R';
    public static Character boxed = Character.valueOf('B');
    public static Short shortValue = Short.valueOf((short)7);
    public static Byte byteValue = Byte.valueOf((byte)8);
    public static char echo(char value) { return value; }
    public static Character echoBoxed(Character value) { return value; }
    public static Character absent() { return null; }
    public static Short shortBoxed() { return Short.valueOf((short)9); }
    public static Byte byteBoxed() { return Byte.valueOf((byte)10); }
    public static long longRaw() { return 11L; }
    public static Long longBoxed() { return Long.valueOf(12L); }
    public static int intRaw() { return 13; }
    public static Integer intBoxed() { return Integer.valueOf(14); }
    public static short shortRaw() { return (short)15; }
    public static byte byteRaw() { return (byte)16; }
    public static double doubleRaw() { return 1.25; }
    public static Double doubleBoxed() { return Double.valueOf(2.5); }
    public static float floatRaw() { return 3.5f; }
    public static Float floatBoxed() { return Float.valueOf(4.5f); }
    public static boolean booleanRaw() { return true; }
    public static Boolean booleanBoxed() { return Boolean.FALSE; }
}''', encoding="utf-8")
        subprocess.run(["javac", "-d", str(classes), str(edge_source)], check=True)
        edge_jar = directory / "edges.jar"
        subprocess.run(["jar", "--create", "--file", str(edge_jar),
                        "-C", str(classes), "probe/NumericEdges.class"], check=True)
        edges_api = obj(run("api", "probe.NumericEdges", "--classpath", edge_jar, "--json"))
        echo_api = next(m for m in edges_api["staticMethods"] if m["name"] == "echo")
        check("api-char-policy", any("one-UTF-16-unit" in n for n in echo_api["interopNotes"]))
        edges = directory / "edges.spr"
        edges.write_text('''import probe.NumericEdges as NumericEdges
print(NumericEdges.echo("A"))
let boxed = NumericEdges.echoBoxed("Z")
if boxed != null:
    print(boxed)
let absent = NumericEdges.absent()
print(absent)
let small = NumericEdges.shortBoxed()
if small != null:
    print(small)
let tiny = NumericEdges.byteBoxed()
if tiny != null:
    print(tiny)
print(NumericEdges.raw)
let fieldBoxed = NumericEdges.boxed
if fieldBoxed != null:
    print(fieldBoxed)
let fieldShort = NumericEdges.shortValue
if fieldShort != null:
    print(fieldShort)
let fieldByte = NumericEdges.byteValue
if fieldByte != null:
    print(fieldByte)
print(NumericEdges.longRaw())
let longBoxed = NumericEdges.longBoxed()
if longBoxed != null:
    print(longBoxed)
print(NumericEdges.intRaw())
let intBoxed = NumericEdges.intBoxed()
if intBoxed != null:
    print(intBoxed)
print(NumericEdges.shortRaw())
print(NumericEdges.byteRaw())
print(NumericEdges.doubleRaw())
let doubleBoxed = NumericEdges.doubleBoxed()
if doubleBoxed != null:
    print(doubleBoxed)
print(NumericEdges.floatRaw())
let floatBoxed = NumericEdges.floatBoxed()
if floatBoxed != null:
    print(floatBoxed)
print(NumericEdges.booleanRaw())
let booleanBoxed = NumericEdges.booleanBoxed()
if booleanBoxed != null:
    print(booleanBoxed)
''', encoding="utf-8")
        checked_edges = run("check", edges, "--classpath", edge_jar, "--json")
        check("jvm-scalars-check", checked_edges.returncode == 0)
        run_edges = run("run", edges, "--classpath", edge_jar, "--json")
        check("jvm-scalars-run", run_edges.returncode == 0 and
              obj(run_edges)["programOutput"] ==
              "A\nZ\nnull\n9\n10\nR\nB\n7\n8\n11\n12\n13\n14\n15\n16\n1.25\n2.5\n3.5\n4.5\ntrue\nfalse\n")
        dynamic_char = directory / "dynamic_char.spr"
        dynamic_char.write_text('''import probe.NumericEdges as NumericEdges
let value = "AB"
print(NumericEdges.echo(value))
''', encoding="utf-8")
        rejected_char = run("check", dynamic_char, "--classpath", edge_jar, "--json")
        check("jvm-char-variable-rejected", rejected_char.returncode == 1 and any(
            d["code"] == "SPR-JVM-MEMBER" for d in obj(rejected_char)["diagnostics"]))
        dynamic_boxed = directory / "dynamic_boxed.spr"
        dynamic_boxed.write_text('''import probe.NumericEdges as NumericEdges
let value = "AB"
print(NumericEdges.echoBoxed(value))
''', encoding="utf-8")
        rejected_boxed = run("check", dynamic_boxed, "--classpath", edge_jar, "--json")
        check("jvm-character-variable-rejected", rejected_boxed.returncode == 1 and any(
            d["code"] == "SPR-JVM-MEMBER" for d in obj(rejected_boxed)["diagnostics"]))
        writable = directory / "write_mapped_field.spr"
        writable.write_text('''import probe.NumericEdges as NumericEdges
NumericEdges.shortValue = 2
''', encoding="utf-8")
        rejected_write = run("check", writable, "--classpath", edge_jar, "--json")
        check("jvm-adapted-field-write-rejected", rejected_write.returncode == 1 and any(
            d["code"] == "SPR-JVM-MEMBER" for d in obj(rejected_write)["diagnostics"]))

    top_level_checked = ROOT / "tests/agent_tooling/fixtures/top_level_checked_exception.spr"
    checked = run("check", top_level_checked, "--json")
    executed = run("run", top_level_checked, "--json")
    check("top-level-checked-java-is-runtime-propagation", checked.returncode == 0
          and obj(checked)["diagnostics"] == [] and executed.returncode == 0
          and obj(executed)["programOutput"] == "true\n")
    print(f"agent tooling: {COUNT} passed, 0 failed")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print("FAIL agent tooling:", exc, file=sys.stderr)
        raise
