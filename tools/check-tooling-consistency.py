#!/usr/bin/env python3
"""Offline cross-check for compiler catalog, documentation and release metadata."""
import json
from pathlib import Path
import subprocess
import tempfile
import re

ROOT = Path(__file__).resolve().parents[1]
SPRIG = ROOT / "bin" / "sprig"


def invoke(*args):
    result = subprocess.run([str(SPRIG), *args], cwd=ROOT,
                            capture_output=True, text=True, check=True)
    return result.stdout


catalog = json.loads(invoke("capabilities", "--json"))
version = catalog["compilerVersion"]
assert invoke("version").strip() == "sprig-compiler " + version
assert json.loads((ROOT / "website" / "package.json").read_text())["version"] == version
assert catalog["languageVersion"] == "0.8-dev"
assert catalog["jdk"]["minimum"] == 17
assert catalog["license"] == "Apache-2.0"
assert "Apache License" in (ROOT / "LICENSE").read_text()
assert "v0.1.0-alpha.1" in catalog["releaseStatus"]
assert "not** evidence" in (ROOT / "docs" / "releases" /
                            "RELEASE_NOTES-v0.1.0-alpha.2.md").read_text()

help_index = json.loads(invoke("help", "--json"))
assert set(help_index["commands"]) == set(catalog["commands"])
for topic in help_index["topics"]:
    detail = json.loads(invoke("help", topic, "--json"))
    assert detail["compilerVersion"] == version and detail["languageVersion"] == "0.8-dev"
    for example in detail["examples"]:
        if example.startswith(("examples/", "tests/", "website/")):
            assert (ROOT / example).is_file(), (topic, example)

code_rows = (ROOT / "docs" / "DIAGNOSTIC_CODES.md").read_text()
for item in json.loads(invoke("codes", "--json"))["codes"]:
    assert "| " + item["code"] + " |" in code_rows, item["code"]

required = {
    "README.md": [version, "v0.1.0-alpha.1", "Apache"],
    "AGENTS.md": ["sprig api", "Apache-2.0"],
    "docs/FEATURE_STATUS_IMPLEMENTED.md": ["capabilities", "--classpath"],
    "docs/KNOWN_LIMITATIONS.md": ["Apache-2.0", "v0.1.0-alpha.1"],
    "website/en/guide/tooling.md": [version, "sprig api"],
    "website/guide/tooling.md": [version, "sprig api"],
    "website/en/project/release-status.md": [version, "v0.1.0-alpha.1"],
    "website/project/release-status.md": [version, "v0.1.0-alpha.1"],
}
for name, markers in required.items():
    text = (ROOT / name).read_text()
    for marker in markers:
        assert marker in text, (name, marker)
assert "no selected license" not in (ROOT / "docs" / "KNOWN_LIMITATIONS.md").read_text()
assert not list(ROOT.glob("docs/**/REVIEW_REPORT.md"))
# Explicit current-document inventory: historical evidence is excluded.
current = list(required) + ["AGENT_GUIDE.md", "docs/DEPENDENCIES.md", "docs/PROJECTS.md",
    "docs/GENERICS.md", "spec/docs/GENERICS.md", "docs/post-v0.7/V08_VALIDATION_REPORT.md",
    "docs/releases/RELEASE_NOTES-v0.2.0-alpha.1.md", "website/en/guide/generics.md",
    "website/guide/generics.md", "website/en/guide/projects.md", "website/guide/projects.md"]
rules = []
features = catalog["features"]
if features.get("multipleGenericParameters"):
    rules += [r"single-parameter (?:user )?generics", r"multiple (?:type )?parameters.{0,20}unsupported"]
    rules += [r"single.parameter only", r"multiple type parameters and inference are rejected",
              r"仅支持单(?:个)?(?:类型)?参数", r"多类型参数.{0,12}(?:未实现|不支持)"]
if all(features.get(x) for x in ("localDependencies", "gitDependencies", "lockfile")):
    rules += [r"there is no [`']?resolve", r"no (?:[`']?sprig.lock|lockfile)",
              r"dependency resolution/lockfiles.{0,60}incomplete",
              r"there is no dependency export boundary"]
    rules += [r"local/Git(?:/Maven)? dependency\s+resolution (?:is|are) not implemented",
              r"(?:lockfile|lockfiles) (?:is|are) not implemented",
              r"本地(?:/|和)Git.{0,12}(?:尚未实现|不支持)"]
if not features.get("mavenDependencies"):
    rules += [r"Maven (?:dependency )?resolution (?:is )?(?:implemented|complete)",
              r"Maven.{0,30}管理已完成"]
for name in current:
    text = (ROOT / name).read_text()
    for pattern in rules:
        assert not re.search(pattern, text, re.I | re.S), (name, "capability drift", pattern)
assert (ROOT / "docs/GENERICS.md").read_text() == (ROOT / "spec/docs/GENERICS.md").read_text()
assert "Historical audit" in (ROOT / "REVIEW_REPORT.md").read_text().splitlines()[0]
quick = (ROOT / "docs" / "QUICK_REFERENCE.md").read_text().split("```sprig\n", 1)[1].split("\n```", 1)[0]
with tempfile.TemporaryDirectory(prefix="sprig-doc-reference-") as temp:
    source = Path(temp) / "quick.spr"
    source.write_text(quick + "\n")
    result = subprocess.run([str(SPRIG), "run", str(source), "--json"],
                            cwd=ROOT, capture_output=True, text=True)
    assert result.returncode == 0 and json.loads(result.stdout)["programOutput"] == "3\n"
print("tooling/release consistency: version, language, JDK, license, commands, topics, codes, status passed")
