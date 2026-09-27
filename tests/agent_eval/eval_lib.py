"""Shared helpers for task acceptance scripts (no LLM involvement)."""
import os
import subprocess
from pathlib import Path


def sprig(*args, cwd):
    launcher = os.environ.get("SPRIG")
    if not launcher:
        raise SystemExit("SPRIG environment variable must point at the SDK launcher")
    return subprocess.run([launcher, *map(str, args)], cwd=cwd, text=True,
                          encoding="utf-8", capture_output=True, timeout=180)


def require(condition, message):
    if not condition:
        raise SystemExit("FAIL: " + message)


def check_file(path):
    result = sprig("check", "--json", path, cwd=Path(path).parent)
    require(result.returncode == 0, "sprig check failed: " + result.stdout + result.stderr)


def run_file(path, *args):
    result = sprig("run", path, "--", *map(str, args), cwd=Path(path).parent)
    require(result.returncode == 0, "sprig run failed: " + result.stdout + result.stderr)
    return result.stdout


def expect_file_stdout(path, expected, *args):
    output = run_file(path, *args)
    require(output.rstrip("\n") == expected.rstrip("\n"),
            f"stdout was {output!r}, expected {expected!r}")


def resolve_project(project):
    result = sprig("resolve", "--offline", cwd=project)
    require(result.returncode == 0, "sprig resolve --offline failed: " + result.stdout + result.stderr)


def check_project(project):
    result = sprig("check", "--json", cwd=project)
    require(result.returncode == 0, "sprig check failed: " + result.stdout + result.stderr)


def run_project(project, *args):
    result = sprig("run", "--", *map(str, args), cwd=project)
    require(result.returncode == 0, "sprig run failed: " + result.stdout + result.stderr)
    return result.stdout


def expect_project_stdout(project, expected, *args):
    output = run_project(project, *args)
    require(output.rstrip("\n") == expected.rstrip("\n"),
            f"stdout was {output!r}, expected {expected!r}")


def source_text(root):
    texts = []
    for path in sorted(Path(root).rglob("*.spr")):
        texts.append(path.read_text(encoding="utf-8"))
    return "\n".join(texts)


def forbid(text, pattern, reason):
    require(pattern not in text, f"forbidden shortcut present ({pattern}): {reason}")
