#!/usr/bin/env python3
"""The launchers start short commands with the C1 JIT; `sprig lsp` keeps tiered compilation.

A stand-in `java` first on PATH records the arguments the platform launcher
passes, so this checks the launcher itself without starting a JVM.
"""
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
LAUNCHER = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
C1 = "-XX:TieredStopAtLevel=1"


def java_arguments(fake_bin, *args):
    env = dict(os.environ, PATH=str(fake_bin) + os.pathsep + os.environ.get("PATH", ""))
    result = subprocess.run([str(LAUNCHER), *args], env=env, capture_output=True, text=True, timeout=60)
    assert result.returncode == 0 and not result.stderr, (args, result.stdout, result.stderr)
    return result.stdout.split()


def main():
    with tempfile.TemporaryDirectory(prefix="sprig-fake-java-") as temp:
        fake = Path(temp)
        if os.name == "nt":
            (fake / "java.cmd").write_bytes(b"@echo %*\r\n")
        else:
            script = fake / "java"
            script.write_text('#!/bin/sh\nprintf "%s\\n" "$@"\n', encoding="utf-8", newline="\n")
            script.chmod(0o755)
        for command in ("version", "check", "run", "test", "fmt"):
            arguments = java_arguments(fake, command)
            assert C1 in arguments and arguments[-1] == command, (command, arguments)
        server = java_arguments(fake, "lsp", "--stdio")
        assert C1 not in server and "-XX:+TieredCompilation" in server, server
        assert server[-3:] == ["sprig.compiler.cli.Main", "lsp", "--stdio"], server
    print("launcher JIT: short commands use C1 only; the language server keeps tiered compilation")


if __name__ == "__main__":
    main()
