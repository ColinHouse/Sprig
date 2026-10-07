#!/usr/bin/env python3
"""Offline cross-check for compiler catalog, documentation and release metadata."""
import os
import json
from pathlib import Path
import subprocess
import tempfile
import re

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")


def invoke(*args):
    result = subprocess.run([str(SPRIG), *args], cwd=ROOT,
                            capture_output=True, text=True, check=True)
    return result.stdout


catalog = json.loads(invoke("capabilities", "--json"))
version = catalog["compilerVersion"]
assert invoke("version").strip() == "sprig-compiler " + version
assert json.loads((ROOT / "website" / "package.json").read_text())["version"] == version
assert catalog["languageVersion"] == "0.8-dev"
assert catalog["jdk"]["minimum"] == 21
assert catalog["license"] == "Apache-2.0"
assert "Apache License" in (ROOT / "LICENSE").read_text()
source_status = next(line.split("=", 1)[1] for line in (ROOT / "compiler/src/main/resources/sprig/compiler/tooling/catalog.properties").read_text().splitlines() if line.startswith("releaseStatus="))
assert catalog["releaseStatus"] in (source_status, "prerelease; v" + version)
validation_text = (ROOT / "docs/releases/validation.md").read_text(encoding="utf-8")
published_match = re.search(r"validation authority for the published `([^`]+)`", validation_text)
assert published_match, "release validation must identify the latest published tag"
published_tag = published_match.group(1)
published_version = published_tag.removeprefix("v")
assert f"latest published {published_tag}" in source_status or version == published_version
assert catalog["features"]["mavenDependencies"]
assert catalog["features"]["projectAwareClasspath"]
assert catalog["supportedPlatforms"] == ["Linux", "macOS"]
assert catalog["experimentalPlatforms"] == ["Windows"]
assert catalog["features"]["bundledStd"] and catalog["features"]["emitJavaOnly"]
assert version in (ROOT / "docs/language/quick-reference.md").read_text().splitlines()[0]
assert version in (ROOT / "docs/jvm/interop.md").read_text().splitlines()[0]
assert (ROOT / "docs/history/milestones/DESIGN_PRESSURE.md").read_text().startswith("# Design pressure")

help_index = json.loads(invoke("help", "--json"))
assert set(help_index["commands"]) == set(catalog["commands"])
for topic in help_index["topics"]:
    detail = json.loads(invoke("help", topic, "--json"))
    assert detail["compilerVersion"] == version and detail["languageVersion"] == "0.8-dev"
    for example in detail["examples"]:
        if example.startswith(("examples/", "tests/", "website/")):
            assert (ROOT / example).is_file(), (topic, example)

dependency_help = " ".join(json.loads(invoke("help", "dependencies", "--json"))["rules"])
assert "lock schema 5 records compiler identity" in dependency_help
assert "@std package comes from the installed SDK" in dependency_help
assert "stdlib-sha256" not in dependency_help

code_rows = (ROOT / "docs/tooling/diagnostic-codes.md").read_text()
for item in json.loads(invoke("codes", "--json"))["codes"]:
    assert "| " + item["code"] + " |" in code_rows, item["code"]

required = {
    "README.md": [published_version, published_tag, "Apache"],
    "AGENTS.md": ["sprig api", "Apache-2.0"],
    "docs/language/feature-status.md": ["capabilities", "--classpath"],
    "docs/language/known-limitations.md": [version, "v" + version, "experimental Beta", "Apache-2.0"],
    "website/en/guide/tooling.md": [published_version, "sprig api"],
    "website/guide/tooling.md": [published_version, "sprig api"],
    "website/en/project/release-status.md": [published_version, published_tag],
    "website/project/release-status.md": [published_version, published_tag],
}
for name, markers in required.items():
    text = (ROOT / name).read_text()
    for marker in markers:
        assert marker in text, (name, marker)
assert "no selected license" not in (ROOT / "docs/language/known-limitations.md").read_text()
assert not list(ROOT.glob("docs/**/REVIEW_REPORT.md"))
# Explicit current-document inventory: historical evidence is excluded.
current = list(required) + ["docs/tooling/agent-guide.md", "docs/projects/dependencies.md", "docs/projects/projects.md",
    "docs/language/generics.md", "docs/releases/validation.md",
 "website/en/guide/generics.md",
    "website/guide/generics.md", "website/en/guide/projects.md", "website/guide/projects.md",
    "website/en/guide/language-tour.md", "website/guide/language-tour.md",
    "docs/language/feature-status.md", "docs/language/known-limitations.md"]
rules = []
features = catalog["features"]
if features.get("userGenerics"):
    rules += [r"User-defined generics[^.]*not implemented",
              r"用户自定义泛型.{0,150}尚未实现"]
if features.get("sourceFunctionTypes"):
    rules += [r"function types in source[^.]*not implemented",
              r"源码中的函数类型.{0,100}尚未实现"]
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
assert not (ROOT / "docs/history/design-kit/GENERICS.md").exists()
assert not (ROOT / "REVIEW_REPORT.md").exists()
for name in current:
    text = (ROOT / name).read_text()
    for marker in ("no package manager", "没有包管理器"):
        assert marker.lower() not in text.lower(), (name, "stale release claim", marker)
quick = (ROOT / "docs/language/quick-reference.md").read_text().split("```sprig\n", 1)[1].split("\n```", 1)[0]
with tempfile.TemporaryDirectory(prefix="sprig-doc-reference-") as temp:
    source = Path(temp) / "quick.spr"
    source.write_text(quick + "\n")
    result = subprocess.run([str(SPRIG), "run", str(source), "--json"],
                            cwd=ROOT, capture_output=True, text=True)
    assert result.returncode == 0 and json.loads(result.stdout)["programOutput"].replace("\r\n", "\n") == "3\n"
print("tooling/release consistency: version, language, JDK, license, commands, topics, codes, status passed")
