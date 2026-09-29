#!/usr/bin/env python3
"""Product-facing `sprig test` integration contracts using real projects and JVMs."""
from contextlib import contextmanager
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")


def invoke(*args, cwd):
    return subprocess.run([str(SPRIG), *map(str, args)], cwd=cwd,
                          text=True, encoding="utf-8", capture_output=True, timeout=120)


def write(root, relative, content):
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    return path


@contextmanager
def project(files, dependency=False):
    with tempfile.TemporaryDirectory(prefix="sprig-test-product-") as directory:
        root = Path(directory)
        manifest = '[project]\nname = "test-product"\nsource = "src"\nentry = "src/main.spr"\n'
        if dependency:
            manifest += '\n[[dependency]]\nname = "dep"\npath = "dep"\n'
            write(root, "dep/sprig.toml", '[project]\nname = "local-dep"\nsource = "src"\nexports = ["tool.spr"]\n')
            write(root, "dep/src/tool.spr", 'func value() -> String:\n    return "dependency works"\n')
        write(root, "sprig.toml", manifest)
        write(root, "src/main.spr", 'print("project entry")\n')
        for relative, content in files.items():
            write(root, relative, content)
        resolved = invoke("resolve", cwd=root)
        if resolved.returncode:
            raise AssertionError(f"resolve failed: {resolved.stdout} {resolved.stderr}")
        yield root


def payload(result):
    try:
        return json.loads(result.stdout)
    except json.JSONDecodeError as error:
        raise AssertionError(f"expected JSON: {result.stdout!r} {result.stderr!r}") from error


class TestRunnerContract(unittest.TestCase):
    def test_discoverable_tool_contract(self):
        capabilities = payload(invoke("capabilities", "--json", cwd=ROOT))
        self.assertIn("test", capabilities["commands"])
        self.assertTrue(capabilities["features"]["testRunner"])
        self.assertEqual(capabilities["testRunner"]["discovery"], "tests/**/*.spr")
        self.assertEqual(capabilities["testRunner"]["compileFailExpectations"],
                         "tests/compile_fail/**/*.expect.toml")
        self.assertTrue(capabilities["testRunner"]["separateJvmPerRuntimeTest"])
        topic = payload(invoke("help", "testing", "--json", cwd=ROOT))
        self.assertEqual(topic["topic"], "testing")
        self.assertTrue(any("sprig test" in item for item in topic["syntax"]))

    def test_default_discovery_order_filter_and_json(self):
        files = {
            "tests/zeta.spr": 'print("z")\n',
            "tests/a.spr": 'print("a")\n',
            "tests/nested/b.spr": 'print("b")\n',
            "tests/compile_fail/nullable.spr": 'let value: String? = null\nprint(value.length())\n',
            "tests/compile_fail/nullable.expect.toml": 'codes = ["SPR-TYPE-NULLABLE"]\nexact = true\n',
        }
        with project(files) as root:
            result = invoke("test", "--json", cwd=root)
            data = payload(result)
            self.assertEqual(result.returncode, 0, (data, result.stderr))
            self.assertEqual(data["summary"], {"total": 4, "passed": 4, "failed": 0})
            self.assertEqual([row["name"] for row in data["tests"]],
                             ["a.spr", "compile_fail/nullable.spr", "nested/b.spr", "zeta.spr"])
            self.assertEqual([row["mode"] for row in data["tests"]],
                             ["run", "compile_fail", "run", "run"])
            self.assertEqual(data["tests"][1]["diagnostics"][0]["code"], "SPR-TYPE-NULLABLE")
            self.assertEqual(data["tests"][0]["programOutput"], "a\n")
            self.assertEqual(payload(invoke("test", "--json", cwd=root)), data)
            filtered = payload(invoke("test", "--filter", "nested/", "--json", cwd=root))
            self.assertEqual(filtered["summary"], {"total": 1, "passed": 1, "failed": 0})
            self.assertEqual(filtered["tests"][0]["name"], "nested/b.spr")
            explicit = payload(invoke("test", root / "tests" / "zeta.spr", "--json", cwd=root))
            self.assertEqual(explicit["summary"], {"total": 1, "passed": 1, "failed": 0})
            self.assertEqual(explicit["tests"][0]["name"], "zeta.spr")
            wrong_file = payload(invoke("test", root / "sprig.toml", "--json", cwd=root))
            self.assertEqual(wrong_file["exitCode"], 2)
            human = invoke("test", "--filter", "zeta", cwd=root)
            self.assertEqual(human.returncode, 0, human.stderr)
            self.assertIn("PASS zeta.spr", human.stdout)
            self.assertIn("1 passed, 0 failed", human.stdout)

    def test_project_required_and_fixture_configuration_errors(self):
        with tempfile.TemporaryDirectory(prefix="sprig-test-no-project-") as directory:
            result = invoke("test", "--json", cwd=Path(directory))
            data = payload(result)
            self.assertEqual(result.returncode, 2)
            self.assertEqual(data["diagnostics"][0]["code"], "SPR-PROJECT-MANIFEST")
        source = 'let value: Int = "wrong"\n'
        with project({"tests/compile_fail/bad.spr": source}) as root:
            missing = payload(invoke("test", "--json", cwd=root))
            self.assertEqual(missing["exitCode"], 2)
            self.assertEqual(missing["tests"][0]["status"], "error")
            write(root, "tests/compile_fail/bad.expect.toml", 'codes = "SPR-TYPE-ASSIGN"\n')
            malformed = payload(invoke("test", "--json", cwd=root))
            self.assertEqual(malformed["exitCode"], 2)
            self.assertEqual(malformed["tests"][0]["status"], "error")
            write(root, "tests/compile_fail/bad.expect.toml", 'codes = [\n')
            broken_toml = payload(invoke("test", "--json", cwd=root))
            self.assertEqual(broken_toml["exitCode"], 2)
            broken_range = broken_toml["tests"][0]["diagnostics"][0]["range"]
            self.assertIsNotNone(broken_range)
            self.assertEqual(broken_range["start"]["line"], 0)
            write(root, "tests/compile_fail/bad.expect.toml", 'codes = ["SPR-TYPE-ASSIGN"]\nexact = "true"\n')
            quoted_boolean = payload(invoke("test", "--json", cwd=root))
            self.assertEqual(quoted_boolean["exitCode"], 2)
            self.assertEqual(quoted_boolean["tests"][0]["status"], "error")
            write(root, "tests/compile_fail/bad.expect.toml", 'codes = ["SPR-TYPE-ASSIGN"]\nexact = true\n')
            valid = payload(invoke("test", "--json", cwd=root))
            self.assertEqual(valid["exitCode"], 0, valid)
            self.assertEqual(valid["tests"][0]["diagnostics"][0]["code"], "SPR-TYPE-ASSIGN")

    def test_expected_code_subset_and_exact_mode(self):
        files = {
            "tests/compile_fail/two.spr": 'let value: Int = "wrong"\nlet second: String? = null\nprint(second.length())\n',
            "tests/compile_fail/two.expect.toml": 'codes = ["SPR-TYPE-ASSIGN"]\n',
        }
        with project(files) as root:
            subset = payload(invoke("test", "--json", cwd=root))
            self.assertEqual(subset["exitCode"], 0, subset)
            actual = {entry["code"] for entry in subset["tests"][0]["diagnostics"]}
            self.assertIn("SPR-TYPE-ASSIGN", actual)
            self.assertIn("SPR-TYPE-NULLABLE", actual)
            write(root, "tests/compile_fail/two.expect.toml", 'codes = ["SPR-TYPE-ASSIGN"]\nexact = true\n')
            exact = payload(invoke("test", "--json", cwd=root))
            self.assertEqual(exact["exitCode"], 1)
            self.assertEqual(exact["tests"][0]["status"], "failed")
            self.assertEqual({entry["code"] for entry in exact["tests"][0]["diagnostics"]}, actual)

    def test_runtime_exit_error_compile_failure_and_fresh_jvms(self):
        files = {
            "tests/00_exit.spr": 'import java.lang.System as System\nprint("before exit")\nSystem.exit(7)\n',
            "tests/01_after.spr": 'print("after exit")\n',
            "tests/02_error.spr": 'throw Error("expected boom")\n',
            "tests/03_bad.spr": 'let value: Int = "wrong"\n',
            "tests/04_static_set.spr": 'import java.lang.System as System\nSystem.setProperty("sprig.test.leak", "set")\n',
            "tests/05_static_probe.spr": 'import java.lang.System as System\nassert(System.getProperty("sprig.test.leak") == null)\n',
        }
        with project(files) as root:
            result = invoke("test", "--json", cwd=root)
            data = payload(result)
            self.assertEqual(result.returncode, 1, (data, result.stderr))
            self.assertEqual(data["summary"], {"total": 6, "passed": 3, "failed": 3})
            self.assertEqual([row["status"] for row in data["tests"]],
                             ["failed", "passed", "failed", "failed", "passed", "passed"])
            self.assertEqual(data["tests"][1]["programOutput"], "after exit\n")
            self.assertIn("SPR-PROGRAM-EXIT", [d["code"] for d in data["tests"][0]["diagnostics"]])
            self.assertIn("SPR-RUNTIME-ERROR", [d["code"] for d in data["tests"][2]["diagnostics"]])
            self.assertIn("SPR-TYPE-ASSIGN", [d["code"] for d in data["tests"][3]["diagnostics"]])
            human = invoke("test", "--filter", "00_exit", cwd=root)
            self.assertIn("before exit", human.stdout)

    def test_temp_files_and_argv_process_result(self):
        source = '''import "@std/test.spr" as testing
import "@std/files.spr" as files
let dir = testing.temp_dir()
let path = files.join(dir, "note.txt")
files.write_utf8(path, "data")
assert(files.read_utf8(path) == "data")
let out = testing.run_process(["java", "--version"])
assert(out.exit_code == 0)
assert(out.stdout.length() > 0)
assert(out.stderr == "")
let err = testing.run_process(["java", "-version"])
assert(err.exit_code == 0)
assert(err.stderr.length() > 0)
let bad = testing.run_process(["java", "no.such.SprigTestClass"])
assert(bad.exit_code != 0)
assert(bad.stderr.length() > 0)
let literal = testing.run_process(["java", "--version", ";", "echo", "SHOULD_NOT_EXECUTE"])
assert(literal.stdout.indexOf("SHOULD_NOT_EXECUTE") < 0)
var start_failed = false
try:
    testing.run_process(["sprig-no-such-executable-7282026"])
catch problem: Error:
    start_failed = true
assert(start_failed)
print(dir)
'''
        with project({"tests/process.spr": source,
                      "tests/second.spr": 'import "@std/test.spr" as testing\nprint(testing.temp_dir())\n'}) as root:
            result = invoke("test", "--json", cwd=root)
            data = payload(result)
            self.assertEqual(result.returncode, 0, (data, result.stderr))
            dirs = [Path(row["programOutput"].strip()) for row in data["tests"]]
            self.assertEqual(len(set(dirs)), 2)
            self.assertTrue(all(not path.exists() for path in dirs))
            outside = invoke("run", root / "tests/process.spr", "--json", cwd=root)
            self.assertNotEqual(outside.returncode, 0)
            outside_data = payload(outside)
            self.assertEqual(outside_data["diagnostics"][0]["code"], "SPR-RUNTIME-ERROR")
            self.assertIn("only available inside sprig test", outside_data["diagnostics"][0]["message"])

    def test_locked_dependency_graph_applies_to_tests(self):
        files = {
            "tests/dependency.spr": 'import "@dep/tool.spr" as tool\nprint(tool.value())\n',
            "tests/compile_fail/dependency.spr": 'import "@dep/tool.spr" as tool\nlet x: Int = tool.value()\n',
            "tests/compile_fail/dependency.expect.toml": 'codes = ["SPR-TYPE-ASSIGN"]\nexact = true\n',
        }
        with project(files, dependency=True) as root:
            result = invoke("test", "--offline", "--json", cwd=root)
            data = payload(result)
            self.assertEqual(result.returncode, 0, (data, result.stderr))
            self.assertEqual(data["summary"], {"total": 2, "passed": 2, "failed": 0})
            self.assertEqual(data["tests"][1]["programOutput"], "dependency works\n")
            manifest = (root / "sprig.toml")
            manifest.write_text(manifest.read_text(encoding="utf-8").replace(
                'name = "test-product"\n', 'name = "test-product"\nversion = "0.2.0"\n'), encoding="utf-8")
            stale = payload(invoke("test", "--json", cwd=root))
            self.assertEqual(stale["exitCode"], 2)
            self.assertEqual(stale["diagnostics"][0]["code"], "SPR-PROJECT-LOCK-STALE")

    def test_locked_maven_classpath_applies_to_tests(self):
        files = {
            "tests/maven.spr": 'import org.apache.commons.text.StringEscapeUtils as Escape\nlet text = Escape.escapeHtml4("<")\nassert(text == "&lt;")\n',
        }
        with project(files) as root:
            manifest = (root / "sprig.toml").read_text(encoding="utf-8")
            (root / "sprig.toml").write_text(manifest + '\n[[jvm]]\ngroup = "org.apache.commons"\nartifact = "commons-text"\nversion = "1.12.0"\n', encoding="utf-8")
            resolved = invoke("resolve", cwd=root)
            self.assertEqual(resolved.returncode, 0, resolved.stdout + resolved.stderr)
            result = invoke("test", "--offline", "--json", cwd=root)
            data = payload(result)
            self.assertEqual(result.returncode, 0, (data, result.stderr))
            self.assertEqual(data["summary"], {"total": 1, "passed": 1, "failed": 0})

    def test_process_invalid_utf8_is_reported_as_error(self):
        source = '''import "@std/test.spr" as testing
var rejected = false
try:
    testing.run_process(["java", "-cp", "classes", "BadBytes"])
catch problem: Error:
    rejected = true
assert(rejected)
'''
        with project({"tests/utf8.spr": source}) as root:
            java = write(root, "BadBytes.java", '''public class BadBytes {
    public static void main(String[] args) throws Exception {
        System.out.write(255);
        System.out.flush();
    }
}
''')
            classes = root / "classes"
            classes.mkdir()
            subprocess.run(["javac", "-d", str(classes), str(java)], check=True,
                           capture_output=True, text=True)
            result = invoke("test", "--json", cwd=root)
            data = payload(result)
            self.assertEqual(result.returncode, 0, (data, result.stderr))
            self.assertEqual(data["summary"], {"total": 1, "passed": 1, "failed": 0})

    def test_runtime_timeout_is_a_failing_test(self):
        with project({"tests/hangs.spr": 'while true:\n    pass\n'}) as root:
            result = invoke("test", "--json", cwd=root)
            data = payload(result)
            self.assertEqual(result.returncode, 1, (data, result.stderr))
            self.assertEqual(data["summary"], {"total": 1, "passed": 0, "failed": 1})
            self.assertIn("timed out", data["tests"][0]["diagnostics"][0]["message"])

    def test_repository_dogfood_project(self):
        example = ROOT / "examples" / "test_runner"
        result = invoke("test", example, "--json", cwd=ROOT)
        data = payload(result)
        self.assertEqual(result.returncode, 0, (data, result.stderr))
        self.assertEqual(data["summary"], {"total": 5, "passed": 5, "failed": 0})
        self.assertEqual([row["name"] for row in data["tests"]],
                         ["compile_fail/nullable.spr", "process.spr", "string_positions.spr",
                          "table_squares.spr", "temp_file.spr"])


if __name__ == "__main__":
    unittest.main()
