#!/usr/bin/env python3
"""JSON codec: the bundled std module, the package that reexports it, its tests and consumers."""
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
    "build_board_config.spr", "collections.spr", "encoding.spr", "primitives.spr",
    "structure.spr", "support.spr",
}


def run_checks():
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

    # The implementation is the bundled std module; the library only reexports it.
    std_module = ROOT / "std" / "json_codec.spr"
    formatted = call("fmt", "--check", std_module)
    verify("fmt-check-std-module", formatted.returncode == 0,
           f"exit={formatted.returncode} {formatted.stdout}{formatted.stderr}")
    std_api = call("api", "@std/json_codec.spr", "--json")
    std_names = []
    try:
        std_names = [row["name"] for row in json.loads(std_api.stdout)["declarations"]
                     if not row.get("reexported")]
    except (ValueError, KeyError):
        pass
    verify("std-module-api", std_api.returncode == 0 and "required_int" in std_names
           and "root_array" in std_names and "Reader" in std_names,
           f"exit={std_api.returncode} {std_api.stdout[:300]}{std_api.stderr[:200]}")

    # A single file needs no manifest and no dependency to use the std module.
    with tempfile.TemporaryDirectory(prefix="sprig-json-codec-standalone-") as temp:
        source = Path(temp) / "standalone.spr"
        source.write_text('''import "@std/json.spr" as json
import "@std/json_codec.spr" as codec

for row in codec.root_array(json.parse("[{\\"id\\": 1}, {\\"id\\": \\"two\\"}]")):
    try:
        print(codec.required_int(row, "id"))
    catch problem: Error:
        print(problem.message)
''', encoding="utf-8")
        standalone = call("run", source, cwd=temp)
        verify("std-module-standalone", standalone.returncode == 0
               and standalone.stdout == "1\n$[1].id: expected integer, found string\n",
               f"exit={standalone.returncode} stdout={standalone.stdout!r} stderr={standalone.stderr!r}")

    # Every reader failure is an Error whose message starts with the path (#206),
    # also for a duplicate key in a hand-built object, a valid number whose
    # exponent no Decimal can hold and a hand-built number with invalid text.
    # A failure that is not an Error would end the program before its last line.
    with tempfile.TemporaryDirectory(prefix="sprig-json-codec-errors-") as temp:
        source = Path(temp) / "reader_errors.spr"
        source.write_text('''import "@std/json.spr" as json
import "@std/json_codec.spr" as codec

func int_error(reader: codec.Reader, name: String) -> String:
    try:
        return "decoded: " + codec.required_int(reader, name).toString()
    catch problem: Error:
        return problem.message

func optional_string_error(reader: codec.Reader, name: String) -> String:
    try:
        let value = codec.optional_string(reader, name)
        if value == null:
            return "null"
        return "decoded: " + value
    catch problem: Error:
        return problem.message

func field_error(reader: codec.Reader, name: String) -> String:
    try:
        match codec.field(reader, name):
            case codec.Field.Missing:
                return "missing"
            case codec.Field.Null:
                return "null"
            case codec.Field.Value as found:
                return "value"
    catch problem: Error:
        return problem.message

func decimal_error(reader: codec.Reader, name: String) -> String:
    try:
        return "decoded: " + codec.required_decimal(reader, name).toString()
    catch problem: Error:
        return problem.message

func optional_decimal_error(reader: codec.Reader, name: String) -> String:
    try:
        let value = codec.optional_decimal(reader, name)
        if value == null:
            return "null"
        return "decoded: " + value.toString()
    catch problem: Error:
        return problem.message

func as_decimal_error(reader: codec.Reader) -> String:
    try:
        return "decoded: " + codec.as_decimal(reader).toString()
    catch problem: Error:
        return problem.message

func number_text_error(reader: codec.Reader, name: String) -> String:
    try:
        return "decoded: " + codec.required_number_text(reader, name)
    catch problem: Error:
        return problem.message

func optional_number_text_error(reader: codec.Reader, name: String) -> String:
    try:
        let value = codec.optional_number_text(reader, name)
        if value == null:
            return "null"
        return "decoded: " + value
    catch problem: Error:
        return problem.message

# json.parse rejects a duplicate key, so only a hand-built object has one. The
# lookup checks the whole object, so the path is the object's, whatever the key.
let duplicate = codec.object([codec.member("a", codec.int(1)), codec.member("a", codec.int(2))])
let flat = codec.Reader(path="$", value=duplicate)
print(int_error(flat, "a"))
print(int_error(flat, "b"))
print(optional_string_error(flat, "a"))
print(field_error(flat, "a"))
let nested = codec.root(codec.object([codec.member("meta", duplicate)]))
print(int_error(codec.required_object(nested, "meta"), "a"))

# Valid JSON numbers whose exponent no Decimal can hold.
let big = codec.root(json.parse("{\\"n\\": 1e9999999999, \\"m\\": -2.5E-9999999999, \\"ok\\": 1e3}"))
print(decimal_error(big, "n"))
print(optional_decimal_error(big, "m"))
print(as_decimal_error(codec.root_array(json.parse("[1e9999999999]"))[0]))
print(decimal_error(big, "ok"))
print(int_error(big, "n"))
print(number_text_error(big, "n"))

# A hand-built number whose text is not a JSON number fails every number reader.
let manual = codec.root(codec.object([codec.member("n", codec.number("1e")), codec.member("m", codec.number("+12"))]))
print(number_text_error(manual, "n"))
print(optional_number_text_error(manual, "m"))
print(decimal_error(manual, "n"))
print(int_error(manual, "m"))
print("done")
''', encoding="utf-8")
        errors = call("run", source, cwd=temp)
        expected = ("$: duplicate object key: a\n"
                    "$: duplicate object key: a\n"
                    "$: duplicate object key: a\n"
                    "$: duplicate object key: a\n"
                    "$.meta: duplicate object key: a\n"
                    "$.n: expected decimal in range, found number 1e9999999999\n"
                    "$.m: expected decimal in range, found number -2.5E-9999999999\n"
                    "$[0]: expected decimal in range, found number 1e9999999999\n"
                    "decoded: 1000\n"
                    "$.n: expected integer, found number 1e9999999999\n"
                    "decoded: 1e9999999999\n"
                    "$.n: expected number, found invalid number lexeme 1e\n"
                    "$.m: expected number, found invalid number lexeme +12\n"
                    "$.n: expected decimal, found number 1e\n"
                    "$.m: expected integer, found number +12\n"
                    "done\n")
        verify("std-module-reader-errors-name-the-path", errors.returncode == 0 and errors.stdout == expected,
               f"exit={errors.returncode} stdout={errors.stdout!r} stderr={errors.stderr!r}")

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

        # The package's names are the std module's own declarations, so values
        # from either import are the same types.
        (project / "src/mixed.spr").write_text('''import "@std/json.spr" as json
import "@std/json_codec.spr" as std_codec
import "@json-codec/codec.spr" as codec

let from_package: std_codec.Reader = codec.root(json.parse("{\\"n\\": 3}"))
let from_std: codec.Reader = std_codec.root(json.parse("{\\"n\\": 4}"))
print(std_codec.required_int(from_package, "n") + codec.required_int(from_std, "n"))
''', encoding="utf-8")
        mixed = call("run", "src/mixed.spr", cwd=project)
        verify("package-and-std-share-types", mixed.returncode == 0 and mixed.stdout == "7\n",
               f"exit={mixed.returncode} stdout={mixed.stdout!r} stderr={mixed.stderr!r}")
        facade_api = call("api", "@json-codec/codec.spr", "--json", cwd=project)
        facade_rows = []
        try:
            facade_rows = json.loads(facade_api.stdout)["declarations"]
        except (ValueError, KeyError):
            pass
        verify("package-api-names-std-origin", facade_api.returncode == 0
               and sorted(row["name"] for row in facade_rows) == sorted(std_names)
               and all(row.get("reexported") is True and row.get("originModule") == "@std/json_codec.spr"
                       for row in facade_rows),
               f"exit={facade_api.returncode} {facade_api.stdout[:400]}{facade_api.stderr[:200]}")

    print(f"json codec library: {passed} checks passed, {len(failed)} failed")
    for name in failed:
        print(f"failed: {name}")
    return 1 if failed else 0


def main():
    # Resolving the library is part of this test, but the generated lock is not
    # checked in. Keep the checkout unchanged so exact-tag release packaging
    # can still verify that it came from a clean source tree.
    lock = LIB / "sprig.lock"
    original_lock = lock.read_bytes() if lock.exists() else None
    try:
        return run_checks()
    finally:
        if original_lock is None:
            lock.unlink(missing_ok=True)
        else:
            lock.write_bytes(original_lock)


if __name__ == "__main__":
    raise SystemExit(main())
