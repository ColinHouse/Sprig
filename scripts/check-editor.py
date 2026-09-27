#!/usr/bin/env python3
"""Build and check the desktop editor adapter without launching a GUI."""
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
EXTENSION = ROOT / "editors/vscode"


def main():
    npm = shutil.which("npm")
    if not npm:
        print("Node.js 20+ and npm are required for editor checks.", file=sys.stderr)
        return 2
    if not (EXTENSION / "node_modules/typescript/bin/tsc").is_file():
        code = subprocess.call([npm, "ci"], cwd=EXTENSION)
        if code:
            return code
    for args in (["run", "test"], ["run", "package"]):
        code = subprocess.call([npm, *args], cwd=EXTENSION)
        if code:
            return code
    print("Editor verification passed (TextMate, real CLI/JVM and VSIX package).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
