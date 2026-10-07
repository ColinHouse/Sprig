#!/usr/bin/env python3
"""Every declaration that @std and the bundled packages show through `sprig api`
has a doc: the comment written directly above it, which is all an agent sees
when it queries one symbol. A helper that callers should not use says so with a
doc that starts with "Internal:", since Sprig has no private declarations."""
import json
import os
import shutil
import subprocess
import tempfile
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
COUNT = 0


def api(module, cwd):
    result = subprocess.run([str(SPRIG), "api", module, "--json"], cwd=cwd, capture_output=True,
                            text=True, encoding="utf-8", timeout=120)
    assert result.returncode == 0, (module, result.stdout, result.stderr)
    return json.loads(result.stdout)


def undocumented(module, data):
    global COUNT
    missing = []
    for variable in data.get("variables") or []:
        COUNT += 1
        if not (variable.get("doc") or "").strip():
            missing.append(f"{module}: {variable['name']}")
    for declaration in data["declarations"]:
        named = declaration["name"]
        COUNT += 1
        if not (declaration.get("doc") or "").strip():
            missing.append(f"{module}: {named}")
        for method in declaration.get("methods") or []:
            COUNT += 1
            if not (method.get("doc") or "").strip():
                missing.append(f"{module}: {named}.{method['name']}")
    return missing


def main():
    if not SPRIG.exists():
        print("Build first: python3 scripts/build.py")
        return 2
    missing = []
    for source in sorted((ROOT / "std").glob("*.spr")):
        module = "@std/" + source.name
        missing += undocumented(module, api(module, ROOT))
    with tempfile.TemporaryDirectory(prefix="sprig-api-docs-") as temporary:
        for manifest in sorted((ROOT / "libraries").glob("*/sprig.toml")):
            project = tomllib.loads(manifest.read_text(encoding="utf-8")).get("project", {})
            if not project.get("exports"):
                continue
            # A copy, so resolving writes no lock into the checkout.
            copy = Path(temporary) / manifest.parent.name
            shutil.copytree(manifest.parent, copy, ignore=shutil.ignore_patterns("sprig.lock", "sprig-build"))
            resolved = subprocess.run([str(SPRIG), "resolve"], cwd=copy, capture_output=True, text=True,
                                      encoding="utf-8", timeout=120)
            assert resolved.returncode == 0, (manifest, resolved.stdout, resolved.stderr)
            for exported in project["exports"]:
                module = project.get("source", "src") + "/" + exported
                missing += undocumented(manifest.parent.name + "/" + module, api(module, copy))
    assert not missing, "declarations without a doc comment:\n  " + "\n  ".join(missing)
    print(f"api docs: {COUNT} variables, declarations and methods of @std and the bundled packages documented")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
