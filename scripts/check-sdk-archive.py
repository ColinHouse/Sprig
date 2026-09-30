#!/usr/bin/env python3
"""Verify the actual ZIP SDK outside the checkout, including network/offline showcases."""
import argparse
import hashlib
import json
import re
import os
import sys
import zipfile
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--archive", type=Path, help="verify a downloaded release ZIP instead of the local build")
args = parser.parse_args()
if args.archive:
    archive = args.archive.resolve()
    version = archive.name.removeprefix("sprig-v").removesuffix("-jdk.zip")
else:
    launcher = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
    version = subprocess.check_output([str(launcher), "version"], text=True).split()[1]
    archive = ROOT / "dist" / f"sprig-v{version}-jdk.zip"
assert archive.is_file(), archive
checksum = Path(str(archive) + ".sha256")
assert checksum.is_file(), checksum
assert hashlib.sha256(archive.read_bytes()).hexdigest() == checksum.read_text(encoding="utf-8").split()[0]

with tempfile.TemporaryDirectory(prefix="sprig SDK smoke with spaces ") as temp:
    with zipfile.ZipFile(archive) as packaged:
        packaged.extractall(temp)
    sdk = Path(temp) / f"sprig-v{version}-jdk"
    cli = sdk / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
    (sdk / "bin" / "sprig").chmod(0o755)
    assert (sdk / "bin" / "sprig.cmd").is_file()
    resolver = json.loads((sdk / "legal/resolver-libraries.json").read_text(encoding="utf-8"))
    for name, digest in resolver["jars"].items():
        assert hashlib.sha256((sdk / "lib" / name).read_bytes()).hexdigest() == digest, name
    assert (sdk / "runtime/src/main/java/sprig/runtime").is_dir()
    assert (sdk / "legal/resolver/LICENSE").is_file()
    assert (sdk / "legal/resolver/NOTICE").is_file()
    for name in ("README.md", "INSTALL.md", "AGENT_GUIDE.md", f"RELEASE_NOTES-v{version}.md",
                 "docs/QUICK_REFERENCE.md", "docs/GENERICS.md",
                 "docs/PROJECTS.md", "docs/DEPENDENCIES.md",
                 "docs/FEATURE_STATUS_IMPLEMENTED.md", "docs/JVM_INTEROP.md",
                 "docs/NUMERIC_SEMANTICS.md", "docs/DIAGNOSTIC_CODES.md",
                 "docs/KNOWN_LIMITATIONS.md", "LICENSE", "NOTICE"):
        assert (sdk / name).is_file(), name
    for file in sdk.rglob("*.md"):
        assert not any(x in file.name for x in ("VALIDATION", "REVIEW_REPORT", "REPORT")), file
    for name in ("README.md", f"RELEASE_NOTES-v{version}.md", "docs/KNOWN_LIMITATIONS.md"):
        text = (sdk / name).read_text()
        assert version in text
        assert not any(marker in text for marker in ("NOT RELEASED", "development draft", "development Agent SDK")), name
    examples_index = (sdk / "examples/README.md").read_text(encoding="utf-8")
    assert "Begin with the [Sprig Agent Guide](../AGENT_GUIDE.md)." in examples_index
    assert "[Fabric/Loom integration](../docs/SHOWCASES.md)" in examples_index
    subprocess.run([sys.executable, str(ROOT / "scripts/internal/check-doc-links.py"), "--root", str(sdk)], check=True)
    assert not (sdk / "acceptance").exists(), "maintainer blind-test evidence must stay in the repository"

    def command(*args, cwd=sdk):
        result = subprocess.run([str(cli), *args], cwd=cwd,
                                capture_output=True, text=True)
        assert result.returncode == 0, (args, result.stdout, result.stderr)
        return result.stdout

    assert command("version").strip().endswith(version)
    capabilities = json.loads(command("capabilities", "--json"))
    assert capabilities["compilerVersion"] == version
    assert capabilities["releaseStatus"] == "prerelease; v" + version or re.fullmatch(r"development; (?:target|based on) v" + re.escape(version) + r"; latest published v[0-9A-Za-z.+-]+", capabilities["releaseStatus"])
    if args.archive:
        assert capabilities["releaseStatus"] == "prerelease; v" + version, "release ZIP must originate from a clean exact-tag build"
    assert capabilities["features"]["mavenDependencies"]
    assert json.loads(command("doctor", "--json"))["antlrAvailable"]
    assert json.loads(command("api", "java.time.LocalDate", "--json"))["className"] == "java.time.LocalDate"
    for topic in json.loads(command("help", "--json"))["topics"]:
        for example in json.loads(command("help", topic, "--json"))["examples"]:
            if example.startswith(("examples/", "website/", "tests/")):
                assert (sdk / example).is_file(), (topic, example)
    hello = "website/snippets/tutorial/hello.spr"
    assert json.loads(command("check", hello, "--json"))["diagnostics"] == []
    assert json.loads(command("run", hello, "--json"))["programOutput"] == "Hello, Ada!\n"
    independent = Path(temp) / "standalone std user"
    independent.mkdir()
    source = independent / "main.spr"
    source.write_text('import "@std/text.spr" as text\nprint(text.trim("  installed std  "))\n', encoding="utf-8")
    assert json.loads(command("run", str(source), "--json", cwd=independent))["programOutput"] == "installed std\n"
    generated = json.loads(command("build", str(source), "--emit-java-only", "-d", str(independent / "output"), "--json", cwd=independent))
    assert generated["javacInvoked"] is False and generated["javaSources"]
    assert not list((independent / "output").rglob("*.class"))
    probe = json.loads(command("run", "examples/stage1_frontend_probe/frontend.spr", "--json"))
    assert "PROBE-LEX 3:1 [22,24)" in probe["programOutput"]
    project = Path(temp) / "fresh project with spaces"
    initialized = json.loads(command("init", str(project), "--json"))
    assert initialized["exitCode"] == 0
    resolved = json.loads(command("resolve", "--offline", "--json", cwd=project))
    assert resolved["exitCode"] == 0, resolved
    assert (project / "sprig.lock").is_file()
    assert json.loads(command("check", "--offline", "--json", cwd=project))["diagnostics"] == []
    assert json.loads(command("run", "--offline", "--json", cwd=project))["programOutput"] == "Hello, Sprig!\n"
    showcase_test = ROOT / "scripts/test-showcases.py"
    if not showcase_test.is_file():
        raise AssertionError("release requires all three showcases and their portable verification")
    subprocess.run([sys.executable, str(showcase_test), "--sdk", str(sdk)], check=True)
    print(f"archive SDK: {archive.name}, offline help/api/doctor/check/run/probe/init/resolve/project + showcases passed")
