#!/usr/bin/env python3
"""Run the packaged SDK outside the source checkout, with no network."""
import json
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
version = subprocess.check_output([str(ROOT / "bin" / "sprig"), "version"], text=True).split()[1]
archive = ROOT / "dist" / f"sprig-v{version}-jdk.zip"
assert archive.is_file(), archive

with tempfile.TemporaryDirectory(prefix="sprig-sdk-smoke-") as temp:
    subprocess.run(["unzip", "-q", str(archive), "-d", temp], check=True)
    sdk = Path(temp) / f"sprig-v{version}-jdk"
    cli = sdk / "bin" / "sprig"
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
    subprocess.run(["python3", str(ROOT / "tools/check-doc-links.py"), "--root", str(sdk)], check=True)
    assert not (sdk / "acceptance").exists(), "maintainer blind-test evidence must stay in the repository"

    def command(*args):
        result = subprocess.run([str(cli), *args], cwd=sdk,
                                capture_output=True, text=True)
        assert result.returncode == 0, (args, result.stdout, result.stderr)
        return result.stdout

    assert command("version").strip().endswith(version)
    capabilities = json.loads(command("capabilities", "--json"))
    assert capabilities["compilerVersion"] == version
    assert capabilities["releaseStatus"] == "prerelease; v" + version
    assert not capabilities["features"]["mavenDependencies"]
    assert json.loads(command("doctor", "--json"))["antlrAvailable"]
    assert json.loads(command("api", "java.time.LocalDate", "--json"))["className"] == "java.time.LocalDate"
    for topic in json.loads(command("help", "--json"))["topics"]:
        for example in json.loads(command("help", topic, "--json"))["examples"]:
            if example.startswith(("examples/", "website/", "tests/")):
                assert (sdk / example).is_file(), (topic, example)
    assert json.loads(command("check", "examples/hello.spr", "--json"))["diagnostics"] == []
    assert json.loads(command("run", "examples/hello.spr", "--json"))["programOutput"] == "Hello, Ada!\n"
    probe = json.loads(command("run", "examples/stage1_frontend_probe/frontend.spr", "--json"))
    assert "PROBE-LEX 3:1 [22,24)" in probe["programOutput"]
    print(f"archive SDK: {archive.name}, offline help/api/doctor/check/run/probe passed")
