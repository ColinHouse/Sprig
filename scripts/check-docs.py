#!/usr/bin/env python3
"""Execute documented snippets, build VitePress, and check local links."""
from pathlib import Path
import os
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def main():
    npm = shutil.which("npm")
    if not npm:
        print("Node.js 20+ and npm are required for the documentation gate.", file=sys.stderr)
        return 2
    commands = [[sys.executable, str(ROOT / "tools" / name)] for name in
                ("verify-doc-snippets.py", "check-tooling-consistency.py")]
    vitepress = ROOT / "website/node_modules/vitepress/bin/vitepress.js"
    if not vitepress.is_file() or os.environ.get("DOCS_FORCE_INSTALL"):
        commands.append([npm, "--prefix", str(ROOT / "website"), "ci"])
    commands += [[npm, "--prefix", str(ROOT / "website"), "run", "docs:build"],
                 [sys.executable, str(ROOT / "tools/check-doc-links.py")]]
    for command in commands:
        result = subprocess.run(command, cwd=ROOT)
        if result.returncode:
            return result.returncode
    print("Documentation checks passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
