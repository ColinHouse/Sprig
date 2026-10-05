#!/usr/bin/env python3
"""Java-to-Sprig wrapper generator: mapping, report, determinism and editability."""
import json
import os
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
COMPILER_JAR = ROOT / "build" / "sprig-compiler.jar"
passed = 0
failed = []

GENERATOR_REASONS = {
    "value-adapter-unsupported", "nested-collection-unsupported",
    "overload-collision", "member-name-collision", "object-method-unsupported",
    # the generator is intentionally stricter than the current shared Support
    # for recursive bounds until the follow-up safety classification lands.
    "generic-bound-unsupported",
}


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


def body(process):
    try:
        return json.loads(process.stdout)
    except ValueError:
        return None


JAVA = {
    "Direct.java": '''package audit;
import java.io.IOException;
public class Direct {
    public String name;
    public static int count;
    public Direct(String name) throws IOException {
        if (name.isEmpty()) throw new IOException("empty name");
        this.name = name;
    }
    public String label() { return "direct:" + name; }
    public void bump(int amount) { }
    public static String version() { return "1.0"; }
    public byte[] bytes() { return new byte[] {1, 2}; }
}
''',
    "Shapes.java": '''package audit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
public class Shapes {
    public Optional<String> maybe(String value) { return Optional.ofNullable(value); }
    public Optional<String> empty() { return Optional.empty(); }
    public List<String> names() { return List.of("ada", "grace"); }
    public Map<String, Integer> counts() { return Map.of("a", 1, "b", 2); }
    public String join(List<String> values, String separator) { return String.join(separator, values); }
    public int sum(List<Integer> values) { int total = 0; for (int v : values) total += v; return total; }
    public String describe(Optional<String> value) { return value.orElse("none"); }
    public Map<String, Integer> merge(Map<String, Integer> left, Map<String, Integer> right) {
        java.util.Map<String, Integer> out = new java.util.LinkedHashMap<>(left);
        out.putAll(right);
        return out;
    }
    public List<Map<String, Integer>> nested() { return List.of(Map.of("k", 1)); }
}
''',
    "Overloaded.java": '''package audit;
public class Overloaded {
    public String parse(String value) { return "s:" + value; }
    public String parse(String value, String mode) { return "s2:" + value + ":" + mode; }
    public String parse(int value) { return "i:" + value; }
    public String hit(int value) { return "hit:" + value; }
    public String hit(Integer value) { return "box:" + value; }
    public String echoChar(char value) { return String.valueOf(value); }
    public char initial() { return 'x'; }
    public String varargs(String format, Object... args) { return String.format(format, args); }
    public java.util.List<? extends Number> bounded() { return java.util.List.of(1); }
    public <T> T[] genericArray(T value) { throw new UnsupportedOperationException(); }
    public static <T> T identity(T value) { return value; }
    public static <T extends Number> T boundedPick(T a, T b) { return a; }
    public String apply(sprig.runtime.Fn1<Long, String> fn) { return fn.apply(1L); }
}
''',
}

DIRECT_USE = '''import "./direct.spr" as direct

let client = direct.direct_new("a")
print(client.label())
print(direct.version())
print(direct.count())
client.bump(2)
'''

SHAPES_USE = '''import "./shapes.spr" as shapes

let helper = shapes.shapes_new()
let name = helper.maybe("ada")
if name != null:
    print(name)
print(helper.empty() == null)
let names = helper.names()
if names != null:
    print(names.size())
    print(names[0])
let counts = helper.counts()
if counts != null:
    print(counts.size())
print(helper.join(["a", "b"], "-"))
print(helper.sum([1, 2, 3]))
print(helper.describe("value"))
print(helper.describe(null))
let merged = helper.merge({"a": 1}, {"b": 2})
if merged != null:
    print(merged.size())
'''

OVERLOAD_USE = '''import "./overloaded.spr" as overloaded

let helper = overloaded.overloaded_new()
print(helper.parse_string("x"))
print(helper.parse_string_string("x", "y"))
print(helper.parse_int32(3))
print(overloaded.identity[String]("id"))
print(helper.initial())
'''


def main():
    with tempfile.TemporaryDirectory(prefix="sprig-wrap-") as temp:
        base = Path(temp)
        source_dir = base / "src" / "audit"
        source_dir.mkdir(parents=True)
        for name, text in JAVA.items():
            (source_dir / name).write_text(text, encoding="utf-8")
        classes = base / "classes"
        classes.mkdir()
        compiled = subprocess.run(
            ["javac", "--release", "17", "-cp", str(COMPILER_JAR), "-d", str(classes),
             *map(str, sorted(source_dir.glob("*.java")))],
            text=True, errors="replace", stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        verify("javac-fixture", compiled.returncode == 0, compiled.stdout + compiled.stderr)
        cp = str(classes)

        def wrap(class_name, out, *extra):
            return call("wrap", class_name, "--classpath", cp, "--out", out, *extra)

        # ------------------------------------------------- direct class
        direct_out = base / "direct.spr"
        result = wrap("audit.Direct", direct_out, "--json")
        report = body(result)
        verify("wrap-direct-json", result.returncode == 0 and report is not None
               and report["inputClass"] == "audit.Direct"
               and report["outputPath"] == str(direct_out)
               and any(m["name"] == "direct_new" for m in report["generatedMembers"])
               and any(m["javaSignature"].endswith("bytes()") for m in report["skippedMembers"]),
               f"exit={result.returncode} {result.stdout}{result.stderr}")
        if report:
            array_skip = next((m for m in report["skippedMembers"]
                               if m["javaSignature"].endswith("bytes()")), {})
            verify("wrap-array-skipped-reason",
                   "array-source-syntax-unavailable" in array_skip.get("reasonCodes", []),
                   json.dumps(array_skip))
        text = direct_out.read_text(encoding="utf-8")
        verify("wrap-generated-header",
               text.startswith("# Generated by sprig wrap from audit.Direct.")
               and "Ordinary Sprig source; safe to edit." in text
               and "20" not in text.split("\n")[0], text[:120])
        verify("wrap-constructor-throws", "throws java.io.IOException, Error:" in text
               or "throws IOException, Error:" in text, text[:2000])

        use = base / "use_direct.spr"
        use.write_text(DIRECT_USE, encoding="utf-8")
        executed = call("run", use, "--classpath", cp)
        verify("wrap-direct-runs", executed.returncode == 0 and executed.stdout == "direct:a\n1.0\n0\n",
               f"exit={executed.returncode} {executed.stdout}{executed.stderr}")

        # ------------------------------------------------- overwrite policy
        blocked = wrap("audit.Direct", direct_out, "--json")
        blocked_json = body(blocked)
        verify("wrap-refuses-overwrite", blocked.returncode == 2 and blocked_json is not None
               and blocked_json["diagnostics"][0]["code"] == "SPR-CLI-OPTION"
               and "use --force" in blocked_json["diagnostics"][0]["message"],
               f"exit={blocked.returncode} {blocked.stdout}{blocked.stderr}")
        forced = wrap("audit.Direct", direct_out, "--force")
        verify("wrap-force-overwrites", forced.returncode == 0,
               f"exit={forced.returncode} {forced.stdout}{forced.stderr}")

        # ------------------------------------------------- determinism
        first_copy = base / "deterministic_first.spr"
        second_copy = base / "deterministic_second.spr"
        verify("wrap-first-copy", wrap("audit.Direct", first_copy).returncode == 0)
        verify("wrap-second-copy", wrap("audit.Direct", second_copy).returncode == 0)
        verify("wrap-deterministic", first_copy.read_bytes() == second_copy.read_bytes())
        import re
        verify("wrap-no-timestamp", re.search(r"\d{4}-\d{2}-\d{2}T\d{2}", first_copy.read_text(encoding="utf-8")) is None)

        # ------------------------------------------------- member filter
        member_out = base / "direct_label.spr"
        member = wrap("audit.Direct", member_out, "--member", "label", "--json")
        member_report = body(member)
        verify("wrap-member", member.returncode == 0 and member_report is not None
               and [m["name"] for m in member_report["generatedMembers"]] == ["label"],
               f"exit={member.returncode} {member.stdout}{member.stderr}")
        unknown = wrap("audit.Direct", base / "direct_missing.spr", "--member", "nope", "--json")
        verify("wrap-member-missing", unknown.returncode == 1
               and (body(unknown) or {}).get("diagnostics", [{}])[0].get("code") == "SPR-JVM-MEMBER",
               f"exit={unknown.returncode} {unknown.stdout}{unknown.stderr}")

        # ------------------------------------------------- optional and collections
        shapes_out = base / "shapes.spr"
        shapes_result = wrap("audit.Shapes", shapes_out, "--json")
        shapes_report = body(shapes_result)
        verify("wrap-shapes-json", shapes_result.returncode == 0 and shapes_report is not None,
               f"exit={shapes_result.returncode} {shapes_result.stdout}{shapes_result.stderr}")
        shapes_text = shapes_out.read_text(encoding="utf-8")
        verify("wrap-optional-mapping", "func maybe(value1: String) -> String?:" in shapes_text
               and "Optional" in shapes_text, shapes_text[:1200])
        verify("wrap-adapter-mapping", 'import "@std/jvm.spr" as jvm' in shapes_text
               and "jvm.list_snapshot[String]" in shapes_text
               and "jvm.map_snapshot[String, Int32]" in shapes_text
               and "jvm.list_copy[String]" in shapes_text
               and "jvm.map_copy[String, Int32]" in shapes_text, shapes_text)
        if shapes_report:
            nested = [m for m in shapes_report["skippedMembers"] if "nested" in m["javaSignature"]]
            verify("wrap-nested-skipped", nested and
                   "nested-collection-unsupported" in nested[0]["reasonCodes"], json.dumps(nested))
        shapes_use = base / "use_shapes.spr"
        shapes_use.write_text(SHAPES_USE, encoding="utf-8")
        shapes_run = call("run", shapes_use, "--classpath", cp)
        verify("wrap-shapes-runs", shapes_run.returncode == 0
               and shapes_run.stdout == "ada\ntrue\n2\nada\n2\na-b\n6\nvalue\nnone\n2\n",
               f"exit={shapes_run.returncode} {shapes_run.stdout}{shapes_run.stderr}")

        # ------------------------------------------------- overloads and unsupported
        overload_out = base / "overloaded.spr"
        overload_result = wrap("audit.Overloaded", overload_out, "--json")
        overload_report = body(overload_result)
        verify("wrap-overloaded-json", overload_result.returncode == 0 and overload_report is not None,
               f"exit={overload_result.returncode} {overload_result.stdout}{overload_result.stderr}")
        overload_text = overload_out.read_text(encoding="utf-8")
        verify("wrap-overload-names",
               "func parse_string(" in overload_text
               and "func parse_string_string(" in overload_text
               and "func parse_int32(" in overload_text
               and "generic T:" in overload_text and "func identity(" in overload_text,
               overload_text)
        if overload_report:
            skips = {m["javaSignature"]: m["reasonCodes"] for m in overload_report["skippedMembers"]}
            codes = {code for reasons in skips.values() for code in reasons}
            verify("wrap-unsupported-reasons",
                   "varargs-unsupported" in codes
                   and "wildcard-unsupported" in codes
                   and "generic-array-unsupported" in codes
                   and "generic-bound-unsupported" in codes
                   and "value-adapter-unsupported" in codes
                   and "sprig-callable-boundary" in codes
                   and "overload-collision" in codes,
                   json.dumps(skips, indent=1)[:900])
        overload_use = base / "use_overloaded.spr"
        overload_use.write_text(OVERLOAD_USE, encoding="utf-8")
        overload_run = call("run", overload_use, "--classpath", cp)
        verify("wrap-overloaded-runs", overload_run.returncode == 0
               and overload_run.stdout == "s:x\ns2:x:y\ni:3\nid\nx\n",
               f"exit={overload_run.returncode} {overload_run.stdout}{overload_run.stderr}")

        # ------------------------------------------------- editability
        edited = base / "direct_edited.spr"
        verify("wrap-edit-copy", wrap("audit.Direct", edited).returncode == 0)
        edited_text = edited.read_text(encoding="utf-8")
        assert "return host.label()" in edited_text, edited_text[:500]
        edited.write_text(edited_text.replace("return host.label()", 'return host.label() + "!"'),
                          encoding="utf-8")
        edited_use = base / "use_edited.spr"
        edited_use.write_text(DIRECT_USE.replace('"./direct.spr"', '"./direct_edited.spr"'),
                              encoding="utf-8")
        edited_run = call("run", edited_use, "--classpath", cp)
        verify("wrap-edited-runs-without-generator",
               edited_run.returncode == 0 and edited_run.stdout.startswith("direct:a!\n"),
               f"exit={edited_run.returncode} {edited_run.stdout}{edited_run.stderr}")

        # ------------------------------------------------- api agreement
        api = body(call("api", "audit.Overloaded", "--classpath", cp, "--json")) or {}
        api_members = {m["javaSignature"]: m for group in ("staticMethods", "instanceMethods")
                       for m in api.get(group, [])}
        disagreements = []
        for skipped in (overload_report or {}).get("skippedMembers", []):
            shared = [code for code in skipped["reasonCodes"] if code not in GENERATOR_REASONS]
            if not shared:
                continue
            member = api_members.get(skipped["javaSignature"])
            callable = member is not None and member.get("interopLevel") == "sprig-callable" \
                and "sprig-callable-boundary" in shared
            if not callable and (member is None
                                 or not set(shared) & set(member.get("interopReasonCodes", []))):
                disagreements.append((skipped["javaSignature"], shared,
                                      None if member is None else member.get("interopReasonCodes")))
        verify("wrap-api-agreement", not disagreements, json.dumps(disagreements)[:900])

    print(f"wrap generator: {passed} checks passed, {len(failed)} failed")
    for name in failed:
        print(f"failed: {name}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
