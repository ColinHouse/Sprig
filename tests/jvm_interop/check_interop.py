#!/usr/bin/env python3
"""Bounded JVM interop: opaque arrays, concrete generics, adapters and bytes.

Independent Java fixtures are compiled locally; api metadata, check/build and
JVM behavior are compared for the same shapes. Wildcards, generic inference,
source arrays and varargs must stay honest negatives with structured reasons.
"""
import base64
import hashlib
import json
import os
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
passed = 0
failed = []


def call(*args, env=None):
    return subprocess.run([str(SPRIG), *map(str, args)], text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                          timeout=120, env=env)


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


def diagnostic(process):
    envelope = body(process)
    if not envelope or not envelope.get("diagnostics"):
        return None
    return envelope["diagnostics"][0]


JAVA = {
    "Interop.java": '''package audit;
import java.util.List;
import java.util.Map;

public final class Interop {
    private Interop() {}

    public static byte[] payload() { return new byte[] {1, 2, 3}; }
    public static byte[] echo(byte[] value) { return value; }
    public static int[] ints() { return new int[] {1, 2, 3}; }
    public static int sum(int[] values) { int total = 0; for (int v : values) total += v; return total; }
    public static String kind(byte[] value) { return "bytes"; }
    public static String kind(int[] value) { return "ints"; }
    public static String kind(String[] value) { return "strings"; }
    public static String[] words() { return new String[] {"a", "b"}; }
    public static String[] echoStrings(String[] values) { return values; }
    public static Object[] objects() { return new Object[] {"x"}; }
    public static int fingerprint(Object[] values) { return values.length; }
    public static byte[] invalidUtf8() { return new byte[] {(byte) 0xC3, (byte) 0x28}; }

    public static List<String> names() { return List.of("ada", "grace"); }
    public static Map<String, Integer> counts() { return Map.of("a", 1, "b", 2); }
    public static List<Map<String, Integer>> nested() { return List.of(Map.of("k", 1)); }
    public static List<Integer> integerList() { return List.of(1, 2); }
    public static java.util.ArrayList<Integer> integerArrayList() { return new java.util.ArrayList<>(List.of(1)); }
    public static List<Short> shorts() { return List.of((short) 1); }
    public static List<Character> characters() { return List.of('a'); }
    public static void acceptStrings(List<String> values) { }
    public static java.util.ArrayList<String> mutableNames() {
        return new java.util.ArrayList<>(List.of("ada", "grace"));
    }
    public static void mutate(List<String> values) { values.add("mutated"); }
    public static int countEntries(Map<String, Integer> values) { return values.size(); }
    public static List<String> withNullElement() { return java.util.Arrays.asList("a", null); }

    public static <T> T identity(T value) { return value; }
    public static <T> List<T> repeat(T value, int times) { return java.util.Collections.nCopies(times, value); }
    public static <T extends Comparable<T>> T pick(T a, T b) { return a.compareTo(b) <= 0 ? a : b; }
    public static <T> T[] genericArray(T value) { throw new UnsupportedOperationException(); }
    public static List<? extends Number> wildcardResult() { return List.of(1, 2); }
    public static String join(String format, Object... args) { return String.format(format, args); }

    public static byte[] sha256(byte[] value) throws java.security.NoSuchAlgorithmException {
        return java.security.MessageDigest.getInstance("SHA-256").digest(value);
    }
    public static String base64(byte[] value) { return java.util.Base64.getEncoder().encodeToString(value); }
}
''',
    "Bounded.java": '''package audit;
public class Bounded<T extends Number> {
    public Bounded() {}
    public T get() { return null; }
}
''',
    "Box.java": '''package audit;
public class Box<T> {
    private T value;
    public Box() {}
    public Box(T value) { this.value = value; }
    public T get() { return value; }
    public void set(T value) { this.value = value; }
}
''',
    "Source.java": '''package audit;
public interface Source<T> { T get(); }
''',
    "StringSource.java": '''package audit;
public final class StringSource implements Source<String> {
    @Override public String get() { return "hello"; }
}
''',
    "Base.java": '''package audit;
class Base<T> {
    public T value() { return null; }
    public String tag() { return "base-tag"; }
}
''',
    "Bridge.java": '''package audit;
public class Bridge extends Base<String> {
    static {
        String marker = System.getenv("SPRIG_BRIDGE_MARKER");
        if (marker != null) {
            try { java.nio.file.Files.writeString(java.nio.file.Path.of(marker), "initialized"); }
            catch (Exception ignored) { }
        }
    }
    @Override public String value() { return "bridge"; }
}
''',
    "Widget.java": '''package audit;
public final class Widget {
    static {
        String marker = System.getenv("SPRIG_INIT_MARKER");
        if (marker != null) {
            try { java.nio.file.Files.writeString(java.nio.file.Path.of(marker), "initialized"); }
            catch (Exception ignored) { }
        }
    }
    public static String ping() { return "pong"; }
}
''',
}

ARRAYS = '''import audit.Interop as Interop

let bytes = Interop.payload()
if bytes != null:
    let again = Interop.echo(bytes)
    print(again != null)
    print(Interop.kind(bytes))
let ints = Interop.ints()
if ints != null:
    print(Interop.sum(ints))
    print(Interop.kind(ints))
let words = Interop.words()
if words != null:
    print(Interop.kind(words))
    print(Interop.echoStrings(words) != null)
let objects = Interop.objects()
if objects != null:
    print(Interop.fingerprint(objects))
'''

ADAPTERS = '''import "@std/jvm.spr" as jvm
import audit.Interop as Interop

func main() -> Unit throws Error:
    let foreign = Interop.mutableNames()
    if foreign != null:
        let snapshot = jvm.list_snapshot[String](foreign)
        Interop.mutate(foreign)
        print(snapshot.size())
        print(foreign.size())

        let exported = jvm.list_copy[String](snapshot)
        Interop.mutate(exported)
        print(snapshot.size())
        let array = exported.toArray()
        if array != null:
            print(Interop.fingerprint(array))

    let counts = Interop.counts()
    if counts != null:
        let table = jvm.map_snapshot[String, Int32](counts)
        print(table.size())
        let copy = jvm.map_copy[String, Int32](table)
        print(Interop.countEntries(copy))

try:
    main()
catch problem: Error:
    print("error: " + problem.message)
'''

BYTES = '''import audit.Interop as Interop
import java.io.File as File
import java.io.FileInputStream as FileInputStream
import java.io.FileOutputStream as FileOutputStream
import java.io.IOException as IOException
import java.security.NoSuchAlgorithmException as NoSuchAlgorithmException
import sprig.runtime.jvm.HostBytes as HostBytes

func main() -> Unit throws IOException, NoSuchAlgorithmException, Error:
    let payload = HostBytes.utf8("sprig bytes")
    if payload != null:
        print(HostBytes.length(payload))
        let target = File("interop-bytes.bin")
        let stream = FileOutputStream(target)
        stream.write(payload)
        stream.close()

        let source = FileInputStream(target)
        let read = source.readAllBytes()
        source.close()
        if read != null:
            print(HostBytes.utf8String(read))
            let hash = Interop.sha256(read)
            if hash != null:
                print(HostBytes.hex(hash))
            print(Interop.base64(read))
        if not target.delete():
            throw Error("cannot delete temporary file")

try:
    main()
catch problem: IOException:
    print("io error")
catch problem: NoSuchAlgorithmException:
    print("missing algorithm")
catch problem: Error:
    print("error: " + problem.message)
'''


def main():
    with tempfile.TemporaryDirectory(prefix="sprig-interop-") as tmp:
        directory = Path(tmp)
        source_dir = directory / "src" / "audit"
        source_dir.mkdir(parents=True)
        for name, text in JAVA.items():
            (source_dir / name).write_text(text, encoding="utf-8")
        classes = directory / "classes"
        classes.mkdir()
        compiled = subprocess.run(
            ["javac", "--release", "17", "-d", str(classes), *map(str, sorted(source_dir.glob("*.java")))],
            text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        verify("javac-fixture", compiled.returncode == 0, compiled.stdout + compiled.stderr)
        cp = str(classes)

        def run_file(name, source, *extra, env=None):
            path = directory / name
            path.write_text(source, encoding="utf-8")
            return path, call("run", path, "--classpath", cp, *extra, env=env)

        def check_file(name, source, *extra):
            path = directory / name
            path.write_text(source, encoding="utf-8")
            return path, call("check", path, "--classpath", cp, *extra)

        # ---------------------------------------------------------- capabilities
        catalog = body(call("capabilities", "--json"))
        expected = {"sourceArrays": False, "jvmArrayPassThrough": True,
                    "jvmByteArrayHelpers": True, "jvmConcreteGenerics": True,
                    "jvmCollectionAdapters": True, "jvmGenericInference": False,
                    "jvmWildcards": False, "jvmVarargs": False}
        verify("capabilities-interop-fields",
               catalog and all(catalog.get(key) == value for key, value in expected.items()),
               str({key: catalog.get(key) if catalog else None for key in expected}))

        # ---------------------------------------------------------------- api
        interop = body(call("api", "audit.Interop", "--classpath", cp, "--json")) or {}
        members = {m["name"]: m for m in interop.get("staticMethods", [])}

        def levels(names):
            return {name: (members.get(name, {}).get("interopLevel"),
                           members.get(name, {}).get("interopReasonCodes", []),
                           members.get(name, {}).get("adaptation", {}).get("kind"))
                    for name in names}

        payload_api = members.get("payload", {})
        verify("api-opaque-array",
               payload_api.get("interopLevel") == "opaque-array"
               and payload_api.get("usableFromSprig") and payload_api.get("signatureSupported")
               and payload_api.get("adaptation", {}).get("kind") == "byte-array"
               and "array-source-syntax-unavailable" in payload_api.get("interopReasonCodes", []),
               str(payload_api))
        verify("api-array-kinds",
               members.get("kind", {}).get("interopLevel") == "opaque-array"
               and members.get("sum", {}).get("interopLevel") == "opaque-array"
               and members.get("sum", {}).get("adaptation", {}).get("available") is False,
               str(levels(["kind", "sum"])))

        names_api = members.get("names", {})
        counts_api = members.get("counts", {})
        verify("api-concrete-generics",
               names_api.get("interopLevel") == "adaptable"
               and "java.util.List[String]" in names_api.get("sprigBoundaryType", "")
               and counts_api.get("returnTypeShape", {}).get("kind") == "parameterized"
               and counts_api.get("returnTypeShape", {}).get("arguments", [{}])[0].get("kind") == "class"
               and names_api.get("adaptation", {}).get("kind") == "collection-adapter",
               f"{names_api.get('sprigBoundaryType')} {counts_api.get('returnTypeShape')}")
        nested_shape = members.get("nested", {}).get("returnTypeShape", {})
        verify("api-nested-shape",
               nested_shape.get("kind") == "parameterized"
               and nested_shape.get("arguments", [{}])[0].get("kind") == "parameterized"
               and "java.util.Map" in str(nested_shape),
               str(nested_shape))

        identity_api = members.get("identity", {})
        verify("api-generic-method",
               identity_api.get("usableFromSprig")
               and identity_api.get("interopLevel") == "erased-generic"
               and "explicit-type-arguments-required" in identity_api.get("interopReasonCodes", []),
               str(identity_api))

        unsupported = {
            "pick": "generic-bound-unsupported",
            "genericArray": "generic-array-unsupported",
            "join": "varargs-unsupported",
            "wildcardResult": "wildcard-unsupported",
        }
        for name, code in unsupported.items():
            member = members.get(name, {})
            verify(f"api-unsupported-{name}",
                   code in member.get("interopReasonCodes", []),
                   f"{name}: {member.get('interopLevel')} {member.get('interopReasonCodes')}")

        optional = body(call("api", "java.util.Optional", "--classpath", cp, "--json")) or {}
        optional_of = [m for m in optional.get("staticMethods", []) if m["name"] == "of"]
        java_list = body(call("api", "java.util.List", "--classpath", cp, "--json")) or {}
        list_get = [m for m in java_list.get("instanceMethods", []) if m["name"] == "get"]
        verify("api-jdk-generic-methods",
               optional_of and all(m["usableFromSprig"]
                                   and "explicit-type-arguments-required" in m["interopReasonCodes"]
                                   for m in optional_of)
               and list_get and all(m["usableFromSprig"]
                                    and "raw-generic-boundary" in m["interopReasonCodes"]
                                    for m in list_get),
               f"optional={len(optional_of)} list-get={len(list_get)}")

        # Issue #15: inherited and bridge-method query fixtures. Querying the
        # hierarchy must not initialize any class.
        bridge_marker = directory / "bridge.marker"
        bridge_env = dict(os.environ, SPRIG_BRIDGE_MARKER=str(bridge_marker))
        bridge = body(call("api", "audit.Bridge", "--classpath", cp, "--json",
                           env=bridge_env)) or {}
        bridge_methods = bridge.get("instanceMethods", [])
        source_value = [m for m in bridge_methods
                        if m["name"] == "value" and m["javaSignature"].startswith("public java.lang.String")]
        java_bridge = [m for m in bridge_methods
                       if m["name"] == "value" and m["javaSignature"].startswith("public java.lang.Object")]
        inherited_tag = [m for m in bridge_methods if m["name"] == "tag"]
        verify("api-bridge-inherited",
               source_value and source_value[0]["usableFromSprig"]
               and java_bridge and not java_bridge[0]["signatureSupported"]
               and "bridge-superseded" in java_bridge[0]["interopReasonCodes"]
               and inherited_tag and inherited_tag[0]["usableFromSprig"]
               and not bridge_marker.exists(),
               str([(m["javaSignature"], m["interopLevel"], m["interopReasonCodes"])
                    for m in bridge_methods if m["name"] in ("value", "tag")]))

        # Querying must not initialize classes; running may.
        marker = directory / "widget.marker"
        env = dict(os.environ, SPRIG_INIT_MARKER=str(marker))
        widget = directory / "widget.spr"
        widget.write_text('import audit.Widget as Widget\nprint(Widget.ping())\n', encoding="utf-8")
        api_query = call("api", "audit.Widget", "--classpath", cp, "--json", env=env)
        check_query = call("check", widget, "--classpath", cp, env=env)
        verify("query-no-init", api_query.returncode == 0 and check_query.returncode == 0
               and not marker.exists(), f"marker={marker.exists()}")
        ran = call("run", widget, "--classpath", cp, env=env)
        verify("run-initializes", ran.returncode == 0 and ran.stdout == "pong\n"
               and marker.exists(), f"exit={ran.returncode} {ran.stdout!r} marker={marker.exists()}")

        # ------------------------------------------------- array behavior
        _, arrays = run_file("arrays.spr", ARRAYS)
        verify("run-arrays", arrays.returncode == 0 and arrays.stdout ==
               "true\nbytes\n6\nints\nstrings\ntrue\n1\n",
               f"exit={arrays.returncode} stdout={arrays.stdout!r} stderr={arrays.stderr!r}")

        _, indexed = check_file("array-index.spr", '''import audit.Interop as Interop
let bytes = Interop.payload()
if bytes != null:
    print(bytes[0])
''', "--json")
        indexed_code = diagnostic(indexed)
        verify("check-array-index-rejected",
               indexed.returncode == 1 and indexed_code is not None
               and indexed_code["code"] == "SPR-TYPE-OPERAND"
               and "SPR-JVM-COMPILE" not in indexed.stdout + indexed.stderr,
               f"exit={indexed.returncode} {indexed.stdout}{indexed.stderr}")

        _, literal = check_file("array-literal.spr", "let values: byte[] = null\n")
        verify("check-array-literal-rejected",
               literal.returncode != 0 and "SPR-SYNTAX-ERROR" in literal.stdout + literal.stderr
               and "SPR-JVM-COMPILE" not in literal.stdout + literal.stderr,
               f"exit={literal.returncode} {literal.stdout}{literal.stderr}")

        _, varargs = check_file("varargs.spr", '''import audit.Interop as Interop
print(Interop.join("%s", "x"))
''', "--json")
        varargs_json = diagnostic(varargs)
        varargs_reasons = [c.get("rejectedBecause") for c in
                           (varargs_json or {}).get("data", {}).get("candidates", [])]
        verify("check-varargs-rejected",
               varargs.returncode == 1 and varargs_json and varargs_json["code"] == "SPR-JVM-MEMBER"
               and "Java varargs are not supported" in varargs_reasons,
               f"exit={varargs.returncode} {varargs.stdout}{varargs.stderr}")

        # ------------------------------------------------- generic behavior
        _, box_run = run_file("box.spr", '''import audit.Box as Box
let box = Box[String]("hi")
let value = box.get()
if value != null:
    print(value)
box.set("changed")
let again = box.get()
if again != null:
    print(again)
''')
        verify("run-box", box_run.returncode == 0 and box_run.stdout == "hi\nchanged\n",
               f"exit={box_run.returncode} stdout={box_run.stdout!r} stderr={box_run.stderr!r}")

        _, invariant = check_file("box-invariant.spr", '''import audit.Box as Box
let box = Box[String]("hi")
box.set(1)
''', "--json")
        invariant_json = diagnostic(invariant)
        verify("check-box-invariant",
               invariant.returncode == 1 and invariant_json
               and invariant_json["code"] == "SPR-JVM-MEMBER"
               and "SPR-JVM-COMPILE" not in invariant.stdout + invariant.stderr,
               f"exit={invariant.returncode} {invariant.stdout}{invariant.stderr}")

        inherited_path, inherited = run_file("inherited.spr", '''import audit.Source as Source
import audit.StringSource as StringSource
import audit.Bridge as Bridge

let source: Source[String] = StringSource()
let text = source.get()
if text != null:
    print(text)
print(Bridge().value())
print(Bridge().tag())
''', env=bridge_env)
        verify("run-inherited", inherited.returncode == 0 and inherited.stdout == "hello\nbridge\nbase-tag\n"
               and bridge_marker.exists(),
               f"exit={inherited.returncode} stdout={inherited.stdout!r} stderr={inherited.stderr!r}")
        inherited_build = call("build", inherited_path, "--classpath", cp,
                               "-d", directory / "inherited-build", "--json")
        verify("build-inherited",
               inherited_build.returncode == 0 and body(inherited_build) is not None
               and not body(inherited_build)["diagnostics"]
               and list((directory / "inherited-build").rglob("*.class")),
               f"exit={inherited_build.returncode} {inherited_build.stdout}{inherited_build.stderr}")

        _, explicit = run_file("explicit.spr", '''import audit.Interop as Interop

let picked = Interop.identity[String]("exact")
if picked != null:
    print(picked)
let repeated = Interop.repeat[Int](7, 3)
if repeated != null:
    print(repeated.size())
''')
        verify("run-explicit-generic", explicit.returncode == 0 and explicit.stdout == "exact\n3\n",
               f"exit={explicit.returncode} stdout={explicit.stdout!r} stderr={explicit.stderr!r}")

        _, missing_args = check_file("missing-args.spr", '''import audit.Interop as Interop
print(Interop.identity("x"))
''', "--json")
        missing_json = diagnostic(missing_args)
        missing_reasons = [c.get("rejectedBecause") for c in
                           (missing_json or {}).get("data", {}).get("candidates", [])]
        verify("check-missing-type-args",
               missing_args.returncode == 1 and missing_json
               and missing_json["code"] == "SPR-JVM-MEMBER"
               and "explicit type arguments required" in missing_reasons,
               f"exit={missing_args.returncode} {missing_args.stdout}{missing_args.stderr}")

        _, bounded = check_file("bounded.spr", '''import audit.Interop as Interop
print(Interop.pick[String]("a", "b"))
''', "--json")
        bounded_json = diagnostic(bounded)
        bounded_reasons = [c.get("rejectedBecause") for c in
                           (bounded_json or {}).get("data", {}).get("candidates", [])]
        verify("check-bounded-rejected",
               bounded.returncode == 1 and bounded_json
               and any("recursive or intersection bound" in reason for reason in bounded_reasons),
               f"exit={bounded.returncode} reasons={bounded_reasons} json={bounded_json is not None}")

        _, generic_array = check_file("generic-array.spr", '''import audit.Interop as Interop
print(Interop.genericArray[String]("x"))
''', "--json")
        generic_array_json = diagnostic(generic_array)
        generic_array_reasons = [c.get("rejectedBecause") for c in
                                 (generic_array_json or {}).get("data", {}).get("candidates", [])]
        verify("check-generic-array-rejected",
               generic_array.returncode == 1 and generic_array_json
               and "Java generic array types (T[]) are not supported" in generic_array_reasons,
               f"exit={generic_array.returncode} {generic_array.stdout}{generic_array.stderr}")

        # ------------------------------------------- generic safety negatives
        _, raw_assign = check_file("raw-to-concrete.spr", '''import audit.Interop as Interop
import java.util.List as JavaList

let raw = Interop.integerList()
if raw != null:
    let typed: JavaList[String] = raw
''', "--json")
        raw_assign_diag = diagnostic(raw_assign)
        verify("check-raw-concrete-assignment",
               raw_assign.returncode == 1 and raw_assign_diag
               and raw_assign_diag["code"] == "SPR-TYPE-ASSIGN"
               and "SPR-JVM-COMPILE" not in raw_assign.stdout + raw_assign.stderr,
               f"exit={raw_assign.returncode} {raw_assign.stdout}{raw_assign.stderr}")

        _, raw_argument = check_file("raw-concrete-argument.spr", '''import audit.Interop as Interop

let raw = Interop.integerArrayList()
if raw != null:
    Interop.acceptStrings(raw)
''', "--json")
        raw_argument_diag = diagnostic(raw_argument)
        verify("check-raw-concrete-argument",
               raw_argument.returncode == 1 and raw_argument_diag
               and raw_argument_diag["code"] == "SPR-JVM-MEMBER"
               and "SPR-JVM-COMPILE" not in raw_argument.stdout + raw_argument.stderr,
               f"exit={raw_argument.returncode} {raw_argument.stdout}{raw_argument.stderr}")

        _, adapter_lie = check_file("adapter-lie.spr", '''import "@std/jvm.spr" as jvm
import audit.Interop as Interop

let raw = Interop.integerList()
if raw != null:
    let wrong = jvm.list_snapshot[String](raw)
''', "--json")
        adapter_lie_diag = diagnostic(adapter_lie)
        verify("check-adapter-element-mismatch",
               adapter_lie.returncode == 1 and adapter_lie_diag
               and adapter_lie_diag["code"] == "SPR-TYPE-MISMATCH"
               and "SPR-JVM-COMPILE" not in adapter_lie.stdout + adapter_lie.stderr,
               f"exit={adapter_lie.returncode} {adapter_lie.stdout}{adapter_lie.stderr}")

        _, genuine = run_file("adapter-genuine.spr", '''import "@std/jvm.spr" as jvm
import audit.Interop as Interop

let raw = Interop.integerList()
if raw != null:
    let right = jvm.list_snapshot[Int32](raw)
    print(right.size())
''')
        verify("run-adapter-element-match",
               genuine.returncode == 0 and genuine.stdout == "2\n",
               f"exit={genuine.returncode} stdout={genuine.stdout!r} stderr={genuine.stderr!r}")

        shorts_api = members.get("shorts", {})
        characters_api = members.get("characters", {})
        verify("api-wrapper-generics",
               not shorts_api.get("signatureSupported") and not characters_api.get("signatureSupported")
               and "generic-wrapper-unsupported" in shorts_api.get("interopReasonCodes", [])
               and "generic-wrapper-unsupported" in characters_api.get("interopReasonCodes", []),
               f"shorts={shorts_api.get('interopReasonCodes')} chars={characters_api.get('interopReasonCodes')}")

        _, wrapper_call = check_file("wrapper-generic.spr", '''import audit.Interop as Interop
let values = Interop.shorts()
''', "--json")
        wrapper_diag = diagnostic(wrapper_call)
        wrapper_reasons = [c.get("rejectedBecause") for c in
                           (wrapper_diag or {}).get("data", {}).get("candidates", [])]
        verify("check-wrapper-generic-rejected",
               wrapper_call.returncode == 1 and wrapper_diag
               and any(reason and "element adapter" in reason for reason in wrapper_reasons),
               f"exit={wrapper_call.returncode} reasons={wrapper_reasons}")

        _, bounded_class = check_file("bounded-class.spr", '''import audit.Bounded as Bounded
let box = Bounded[String]()
''', "--json")
        bounded_class_diag = diagnostic(bounded_class)
        verify("check-bounded-class-rejected",
               bounded_class.returncode == 1 and bounded_class_diag
               and bounded_class_diag["code"] == "SPR-TYPE-MISMATCH"
               and "SPR-JVM-COMPILE" not in bounded_class.stdout + bounded_class.stderr,
               f"exit={bounded_class.returncode} {bounded_class.stdout}{bounded_class.stderr}")

        _, bounded_ok = check_file("bounded-class-ok.spr", '''import audit.Bounded as Bounded
let box = Bounded[Int32]()
let value = box.get()
if value != null:
    print(value)
''')
        verify("check-bounded-class-accepted", bounded_ok.returncode == 0,
               f"exit={bounded_ok.returncode} {bounded_ok.stdout}{bounded_ok.stderr}")

        _, wildcard_call = check_file("wildcard-invocation.spr", '''import audit.Interop as Interop
let values = Interop.wildcardResult()
''', "--json")
        wildcard_diag = diagnostic(wildcard_call)
        wildcard_reasons = [c.get("rejectedBecause") for c in
                            (wildcard_diag or {}).get("data", {}).get("candidates", [])]
        verify("check-wildcard-invocation-rejected",
               wildcard_call.returncode == 1 and wildcard_diag
               and wildcard_diag["code"] == "SPR-JVM-MEMBER"
               and "Java wildcards are not supported" in wildcard_reasons,
               f"exit={wildcard_call.returncode} reasons={wildcard_reasons}")

        # ------------------------------------------------- collection adapters
        _, adapters = run_file("adapters.spr", ADAPTERS)
        verify("run-adapters", adapters.returncode == 0 and adapters.stdout == "2\n3\n2\n3\n2\n2\n",
               f"exit={adapters.returncode} stdout={adapters.stdout!r} stderr={adapters.stderr!r}")

        _, nulls = run_file("adapter-nulls.spr", '''import "@std/jvm.spr" as jvm
import audit.Interop as Interop

let values = Interop.withNullElement()
if values != null:
    let snapshot = jvm.list_snapshot[String](values)
    print(snapshot.size())
''', "--json")
        nulls_json = body(nulls)
        verify("run-adapter-null-rejected",
               nulls.returncode == 1 and nulls_json
               and nulls_json["diagnostics"][0]["code"] == "SPR-RUNTIME-ERROR"
               and "contains null" in nulls_json["diagnostics"][0]["message"],
               f"exit={nulls.returncode} stdout={nulls.stdout!r} stderr={nulls.stderr!r}")

        # ------------------------------------------------- bytes dogfood
        _, bytes_run = run_file("bytes.spr", BYTES)
        digest = hashlib.sha256(b"sprig bytes").hexdigest()
        encoded = base64.b64encode(b"sprig bytes").decode()
        verify("run-bytes-dogfood",
               bytes_run.returncode == 0
               and bytes_run.stdout == f"11\nsprig bytes\n{digest}\n{encoded}\n",
               f"exit={bytes_run.returncode} stdout={bytes_run.stdout!r} stderr={bytes_run.stderr!r}")

        _, invalid = run_file("invalid-utf8.spr", '''import audit.Interop as Interop
import sprig.runtime.jvm.HostBytes as HostBytes

let raw = Interop.invalidUtf8()
if raw != null:
    print(HostBytes.utf8String(raw))
''', "--json")
        invalid_json = body(invalid)
        verify("run-invalid-utf8",
               invalid.returncode == 1 and invalid_json
               and invalid_json["diagnostics"][0]["code"] == "SPR-RUNTIME-ERROR"
               and "not valid UTF-8" in invalid_json["diagnostics"][0]["message"],
               f"exit={invalid.returncode} stdout={invalid.stdout!r} stderr={invalid.stderr!r}")

        # ------------------------------------------------- build evidence
        arrays_path = directory / "arrays.spr"
        built = call("build", arrays_path, "--classpath", cp,
                     "-d", directory / "arrays-build", "--json")
        class_files = list((directory / "arrays-build").rglob("*.class"))
        built_json = body(built)
        verify("build-representative",
               built.returncode == 0 and built_json and not built_json["diagnostics"]
               and class_files,
               f"exit={built.returncode} classes={len(class_files)} {built.stdout}{built.stderr}")

    print(f"JVM interop: {passed} checks passed, {len(failed)} failed")
    for name in failed:
        print(f"failed: {name}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
