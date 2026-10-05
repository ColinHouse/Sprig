#!/usr/bin/env python3
"""Build and check the desktop editor adapter without launching a GUI."""
from pathlib import Path
import json
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
EXTENSION = ROOT / "editors/vscode"
NOTICES = EXTENSION / "ThirdPartyNotices.txt"


def notices():
    """License texts of the production dependencies that esbuild bundles into out/main.js."""
    lock = json.loads((EXTENSION / "package-lock.json").read_text(encoding="utf-8"))
    parts = ["Sprig for VS Code bundles these third-party packages into out/main.js.\n"]
    for key, entry in sorted(lock["packages"].items()):
        if not key or entry.get("dev") or entry.get("devOptional") or entry.get("optional"):
            continue
        folder = EXTENSION / key
        license_file = next(path for path in sorted(folder.iterdir())
                            if path.name.lower().startswith(("license", "licence")))
        text = license_file.read_text(encoding="utf-8").replace("\r\n", "\n").strip()
        rule = "=" * 78
        parts.append(f"\n{rule}\n{key.split('node_modules/')[-1]} {entry['version']} ({entry['license']})\n{rule}\n\n{text}\n")
    return "".join(parts)


def check_package():
    """The VSIX holds the bundled extension and its assets, not sources or node_modules."""
    manifest = json.loads((EXTENSION / "package.json").read_text(encoding="utf-8"))
    vsix = EXTENSION / "dist" / f"sprig-language-{manifest['version']}.vsix"
    with zipfile.ZipFile(vsix) as archive:
        names = set(archive.namelist())
        packaged = json.loads(archive.read("extension/package.json"))
        bundle = archive.read("extension/out/main.js").decode("utf-8")
    problems = []
    main = "extension/" + packaged["main"].removeprefix("./")
    for required in (main, "extension/" + packaged["icon"], "extension/syntaxes/sprig.tmLanguage.json",
                     "extension/snippets/sprig.json", "extension/language-configuration.json",
                     "extension/readme.md", "extension/changelog.md", "extension/LICENSE.txt",
                     "extension/ThirdPartyNotices.txt"):
        if required not in names:
            problems.append(f"missing {required}")
    if not NOTICES.is_file() or NOTICES.read_text(encoding="utf-8") != notices():
        problems.append("ThirdPartyNotices.txt does not match the production dependencies: "
                        "run python3 scripts/check-editor.py --write-notices")
    for name in names:
        if name.startswith(("extension/node_modules/", "extension/src/", "extension/test/")) or name.endswith(".map"):
            problems.append(f"unexpected {name}")
    if 'require("vscode-languageclient' in bundle:
        problems.append("the language client is not bundled into out/main.js")
    if problems:
        print("VSIX check failed:\n  " + "\n  ".join(sorted(problems)), file=sys.stderr)
        return 1
    print(f"VSIX {vsix.name}: {len(names)} entries, bundled entry point {packaged['main']}.")
    return 0


def main():
    npm = shutil.which("npm")
    if not npm:
        print("Node.js 20+ and npm are required for editor checks.", file=sys.stderr)
        return 2
    if not (EXTENSION / "node_modules/typescript/bin/tsc").is_file():
        code = subprocess.call([npm, "ci"], cwd=EXTENSION)
        if code:
            return code
    if "--write-notices" in sys.argv[1:]:
        NOTICES.write_text(notices(), encoding="utf-8", newline="\n")
        print(f"Wrote {NOTICES.relative_to(ROOT)}.")
        return 0
    for args in (["run", "test"], ["run", "package"]):
        code = subprocess.call([npm, *args], cwd=EXTENSION)
        if code:
            return code
    code = check_package()
    if code:
        return code
    print("Editor verification passed (TextMate, real CLI/JVM, language server and VSIX package).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
