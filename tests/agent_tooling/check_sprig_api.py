#!/usr/bin/env python3
"""Sprig module/project API introspection: resolved metadata and negative cases."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
passed = 0
failed = []

MANIFEST = """[project]
name = "api-fixture"
version = "0.1.0"
language = "0.8"
source = "src"
entry = "src/main.spr"

[[dependency]]
name = "web"
path = "{web}"

[[dependency]]
name = "cli"
path = "{cli}"
"""

MAIN = '''import "@web/app.spr" as web
import "@cli/cli.spr" as cli

# A value with a label.
generic T:
    class Box:
        # What the box holds.
        let value: T
        let label: String = "box"

generic T:
    variant Maybe:
        # There is a value.
        Present:
            value: T
        Absent:

enum Mode:
    FAST
    SLOW

class Service:
    let name: String
    let endpoint: String? = null
    var calls: Int = 0

    # Echoes the input.
    # Empty input is an Error.
    func call(input: String) -> String throws Error:
        if input == "":
            throw Error("empty")
        return input

    func describe(handler: fn(String) -> String) -> String:
        return handler(name)

func identity(value: String) -> String:
    return value

generic K:
    func same_key(left: K, right: K) -> Bool:
        requires K: Equatable
        return left == right

let default_box = Box[Int](value=1)
'''


def run(*args, cwd):
    return subprocess.run([str(SPRIG), *map(str, args)], cwd=cwd, text=True,
                          encoding="utf-8", capture_output=True, timeout=120)


def result(cwd, *args):
    proc = run(*args, cwd=cwd)
    try:
        return proc, json.loads(proc.stdout)
    except json.JSONDecodeError:
        return proc, {}


def verify(name, ok, detail=""):
    global passed
    if ok:
        passed += 1
    else:
        failed.append(name)
        print(f"FAIL {name}: {detail[:900]}")


def diagnostic_codes(data):
    return [item.get("code") for item in data.get("diagnostics", [])]


def declaration(module, name):
    for item in module.get("declarations", []):
        if item.get("name") == name:
            return item
    return None


def make_fixture(parent, name="fixture"):
    project = parent / name
    (project / "src").mkdir(parents=True)
    (project / "sprig.toml").write_text(
        MANIFEST.format(web=ROOT / "libraries/sprig-web", cli=ROOT / "libraries/sprig-cli"),
        encoding="utf-8")
    (project / "src/main.spr").write_text(MAIN, encoding="utf-8")
    resolved = run("resolve", "--offline", cwd=project)
    assert resolved.returncode == 0, resolved.stdout + resolved.stderr
    return project


with tempfile.TemporaryDirectory(prefix="sprig-api-") as temp:
    work = Path(temp)
    project = make_fixture(work)

    proc, module = result(project, "api", "src/main.spr", "--json")
    verify("module file target", proc.returncode == 0 and module.get("kind") == "sprig-module",
           f"{proc.stdout}{proc.stderr}")
    verify("module label is argument", module.get("module") == "src/main.spr", module.get("module"))

    box = declaration(module, "Box")
    verify("generic class parameters", box is not None and box.get("genericParameters") == ["T"], box)
    label = None if box is None else [f for f in box.get("fields", []) if f["name"] == "label"]
    value = None if box is None else [f for f in box.get("fields", []) if f["name"] == "value"]
    verify("field required/defaulted",
           value and value[0]["required"] is True and value[0]["type"] == "T"
           and label and label[0]["required"] is False and label[0]["type"] == "String", box)

    service = declaration(module, "Service")
    call = None if service is None else [m for m in service.get("methods", []) if m["name"] == "call"]
    describe = None if service is None else [m for m in service.get("methods", []) if m["name"] == "describe"]
    verify("method throws and fn parameter",
           call and call[0]["throws"] == ["Error"]
           and describe and describe[0]["parameters"][0]["type"] == "fn(String) -> String", service)

    maybe = declaration(module, "Maybe")
    verify("variant cases and payloads",
           maybe is not None and [c["name"] for c in maybe["cases"]] == ["Present", "Absent"]
           and maybe["cases"][0]["fields"][0]["type"] == "T", maybe)
    mode = declaration(module, "Mode")
    verify("enum cases", mode is not None and mode["cases"] == ["FAST", "SLOW"], mode)
    same_key = declaration(module, "same_key")
    verify("generic function requires clause",
           same_key is not None and same_key.get("requires") == ["K: Equatable"], same_key)

    # The comment written directly above a declaration, as editor hover shows it.
    absent = None if maybe is None else maybe["cases"][1]
    verify("doc comments",
           box is not None and box.get("doc") == "A value with a label."
           and value and value[0].get("doc") == "What the box holds." and "doc" not in label[0]
           and call and call[0].get("doc") == "Echoes the input.\nEmpty input is an Error."
           and maybe is not None and maybe["cases"][0].get("doc") == "There is a value."
           and absent is not None and "doc" not in absent and "doc" not in (mode or {})
           and "doc" not in (declaration(module, "identity") or {"doc": None}), module)
    text = run("api", "src/main.spr", cwd=project).stdout
    verify("doc comments in text output",
           "  class Box\n    # A value with a label.\n" in text
           and "    method call(input: String): String throws Error\n      # Echoes the input.\n"
               "      # Empty input is an Error.\n" in text, text)

    # A reexported declaration keeps the comment written where it is declared.
    (project / "src/inner.spr").write_text("# Twice the input.\nfunc twice(x: Int) -> Int:\n    return x * 2\n",
                                           encoding="utf-8")
    (project / "src/facade.spr").write_text('import "./inner.spr" as inner\n\nexport inner.twice\n',
                                            encoding="utf-8")
    proc, facade = result(project, "api", "src/facade.spr", "--json")
    twice = declaration(facade, "twice")
    verify("reexported doc comment", proc.returncode == 0 and twice is not None
           and twice.get("reexported") is True and twice.get("doc") == "Twice the input.", facade)

    variable = [v for v in module.get("variables", []) if v["name"] == "default_box"]
    verify("top-level variable type", variable and variable[0]["type"] == "Box[Int]", module.get("variables"))

    proc, web = result(project, "api", "@web/app.spr", "--json")
    app = declaration(web, "App")
    get = None if app is None else [m for m in app.get("methods", []) if m["name"] == "get"]
    verify("package module target",
           proc.returncode == 0 and web.get("module") == "@web/app.spr"
           and get and get[0]["parameters"][1]["type"] == "fn(Request) -> Response", f"{proc.stdout}{proc.stderr}")

    proc, cli = result(project, "api", "@cli/cli.spr", "--json")
    verify("second package module", proc.returncode == 0 and declaration(cli, "parse") is not None,
           f"{proc.stdout}{proc.stderr}")

    proc, filtered = result(project, "api", "@web/app.spr", "--member", "App.get", "--json")
    verify("member filter", proc.returncode == 0 and filtered.get("memberCount") == 1
           and filtered["declarations"][0]["name"] == "App", f"{proc.stdout}{proc.stderr}")

    proc, enum_case = result(project, "api", "src/main.spr", "--member", "Mode.FAST", "--json")
    verify("enum case member filter",
           proc.returncode == 0 and enum_case.get("memberCount") == 1
           and enum_case["declarations"][0]["cases"] == ["FAST"], f"{proc.stdout}{proc.stderr}")

    proc, variant_case = result(project, "api", "src/main.spr", "--member", "Maybe.Absent", "--json")
    verify("variant case member filter",
           proc.returncode == 0 and variant_case.get("memberCount") == 1
           and [c["name"] for c in variant_case["declarations"][0]["cases"]] == ["Absent"],
           f"{proc.stdout}{proc.stderr}")

    proc, data = result(project, "api", "@web/app.spr", "--member", "App.nope", "--json")
    verify("member not found", proc.returncode == 1 and "SPR-API-MEMBER" in diagnostic_codes(data), proc.stdout)

    proc, data = result(project, "api", "@web/openapi_helpers.spr", "--json")
    verify("unexported module refused", proc.returncode == 1 and "SPR-PROJECT-NOT-EXPORTED" in diagnostic_codes(data),
           f"{proc.stdout}{proc.stderr}")

    proc, data = result(project, "api", "@web/nope.spr", "--json")
    verify("missing module refused", proc.returncode == 1 and "SPR-DEP-NOT-FOUND" in diagnostic_codes(data),
           f"{proc.stdout}{proc.stderr}")

    proc, data = result(project, "api", "@nope/app.spr", "--json")
    verify("missing alias refused", proc.returncode == 1 and "SPR-DEP-NOT-FOUND" in diagnostic_codes(data),
           f"{proc.stdout}{proc.stderr}")

    proc, data = result(project, "api", "missing.spr", "--json")
    verify("missing file target", proc.returncode == 1 and "SPR-API-TARGET" in diagnostic_codes(data),
           f"{proc.stdout}{proc.stderr}")

    proc, inventory = result(project, "api", ".", "--json")
    labels = {m["module"]: m for m in inventory.get("modules", [])}
    verify("project inventory",
           proc.returncode == 0 and inventory.get("kind") == "sprig-project"
           and labels.get("main.spr", {}).get("origin") == "source"
           and labels.get("@web/app.spr", {}).get("origin") == "dependency"
           and labels.get("@web/app.spr", {}).get("exported") is True
           and labels.get("@cli/cli.spr", {}).get("dependency") == "cli", f"{proc.stdout}{proc.stderr}")
    verify("project inventory hides unexported",
           "@web/openapi_helpers.spr" not in labels and "@web/request_helpers.spr" not in labels, labels.keys())

    proc, _ = result(project, "api", ".", "--member", "App.get", "--json")
    verify("member filter rejected for project", proc.returncode == 2 and "SPR-CLI-OPTION" in proc.stdout, proc.stdout)

    proc, data = result(project, "api", "java.time.LocalDate", "--json")
    verify("java api unchanged", proc.returncode == 0 and data.get("className") == "java.time.LocalDate",
           f"{proc.stdout}{proc.stderr}")

    malformed = work / "malformed"
    (malformed / "src").mkdir(parents=True)
    (malformed / "sprig.toml").write_text("[project]\nname = \n", encoding="utf-8")
    (malformed / "src/main.spr").write_text('print("hi")\n', encoding="utf-8")
    proc, data = result(malformed, "api", ".", "--json")
    verify("malformed project refused",
           proc.returncode == 1 and "SPR-PROJECT-MANIFEST" in diagnostic_codes(data), proc.stdout)

    plain = work / "plain"
    plain.mkdir()
    (plain / "one.spr").write_text('print("hi")\n', encoding="utf-8")
    proc, data = result(plain, "api", ".", "--json")
    verify("directory without project refused",
           proc.returncode == 1 and "SPR-API-TARGET" in diagnostic_codes(data), proc.stdout)

    # Bundled modules belong to the SDK: they are queryable by import with or without a project.
    for where, directory in (("without a project", plain), ("inside a project", project)):
        proc, data = result(directory, "api", "@std/text.spr", "--json")
        verify(f"bundled std module {where}",
               proc.returncode == 0 and data.get("module") == "@std/text.spr"
               and "trim" in {d["name"] for d in data.get("declarations", [])}, f"{proc.stdout}{proc.stderr}")
    proc, data = result(plain, "api", "@std/no_such_module.spr", "--json")
    verify("missing std module refused without a project",
           proc.returncode == 1 and "SPR-DEP-NOT-FOUND" in diagnostic_codes(data), f"{proc.stdout}{proc.stderr}")
    proc, data = result(plain, "api", "@web/app.spr", "--json")
    verify("package module still requires a project",
           proc.returncode == 1 and "SPR-API-TARGET" in diagnostic_codes(data), f"{proc.stdout}{proc.stderr}")

    stale = make_fixture(work, "stale")
    manifest = (stale / "sprig.toml").read_text(encoding="utf-8")
    (stale / "sprig.toml").write_text(manifest.replace('version = "0.1.0"', 'version = "0.2.0"'),
                                      encoding="utf-8")
    proc, data = result(stale, "api", ".", "--json")
    verify("stale lock refused", proc.returncode == 1 and "SPR-PROJECT-LOCK-STALE" in diagnostic_codes(data),
           proc.stdout)

    cyclic = work / "cyclic"
    (cyclic / "src").mkdir(parents=True)
    (cyclic / "sprig.toml").write_text(
        '[project]\nname = "cyclic"\nversion = "0.1.0"\nlanguage = "0.8"\nsource = "src"\n'
        'entry = "src/a.spr"\n', encoding="utf-8")
    (cyclic / "src/a.spr").write_text('import "./b.spr" as b\nlet a = b.b\n', encoding="utf-8")
    (cyclic / "src/b.spr").write_text('import "./a.spr" as a\nlet b = a.a\n', encoding="utf-8")
    resolved = run("resolve", "--offline", cwd=cyclic)
    proc, data = result(cyclic, "api", ".", "--json")
    verify("import cycle refused",
           resolved.returncode == 0 and proc.returncode == 1
           and "SPR-NAME-IMPORT-CYCLE" in diagnostic_codes(data), f"{resolved.stdout}{proc.stdout}")

    policy = json.loads((ROOT / "libraries/sprig-web/policy.json").read_text(encoding="utf-8"))
    verify("web policy index",
           policy.get("resolvedSignatures") == "sprig api @web/app.spr --json"
           and "cors" in policy.get("behavior", {})
           and "openapi" in policy.get("behavior", {}), policy)

    proc, capabilities = result(project, "capabilities", "--json")
    strings = capabilities.get("stringSemantics", {})
    guidance = capabilities.get("featureGuidance", {})
    verify("string semantics capability",
           strings.get("hasCharType") is False and strings.get("elementType") == "String"
           and strings.get("positionUnit") == "unicode-code-point"
           and strings.get("graphemeClusters") is False, strings)
    verify("feature guidance",
           guidance.get("inheritance", {}).get("supported") is False
           and "composition" in guidance.get("inheritance", {}).get("alternatives", [])
           and guidance.get("matchExpression", {}).get("helpTopic") == "match", guidance)

print(f"Sprig API introspection: {passed} checks passed, {len(failed)} failed")
for name in failed:
    print(f"failed: {name}")
raise SystemExit(1 if failed else 0)
