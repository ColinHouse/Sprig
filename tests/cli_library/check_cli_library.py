#!/usr/bin/env python3
"""Exercise Sprig CLI parsing with real compiled JVM invocations."""
from pathlib import Path
import os
import subprocess

ROOT = Path(__file__).resolve().parents[2]
PROJECT = Path(__file__).resolve().parent
CLI = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")


def run(*args, expected=0):
    result = subprocess.run([str(CLI), "run", "--", *args], cwd=PROJECT, text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=40)
    assert result.returncode == expected, (args, result.returncode, result.stdout, result.stderr)
    return result.stdout.replace("\r\n", "\n"), result.stderr


def main():
    resolved = subprocess.run([str(CLI), "resolve"], cwd=PROJECT, text=True,
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=40)
    assert resolved.returncode == 0, (resolved.stdout, resolved.stderr)
    checked = subprocess.run([str(CLI), "check"], cwd=PROJECT, text=True,
                             stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=40)
    assert checked.returncode == 0, (checked.stdout, checked.stderr)

    out, _ = run("--verbose", "--output", "file name.json", "first", "second")
    assert "verbose=true\noutput=file name.json\n" in out and "positionals=[first, second]" in out, out
    out, _ = run("-v", "-o", "compact.json", "tail")
    assert "verbose=true\noutput=compact.json\n" in out and "positionals=[tail]" in out, out
    out, _ = run("--", "--literal", "-x")
    assert "verbose=false\noutput=<absent>\n" in out and "positionals=[--literal, -x]" in out, out
    out, _ = run("--empty=")
    assert "empty=\n" in out, out

    expected_errors = {
        ("--unknown",): "unknown option: --unknown",
        ("--output",): "missing value for --output",
        ("-o",): "missing value for -o",
        ("--verbose=yes",): "flag --verbose does not take a value",
        ("--output", "--", "x"): "missing value for --output",
        ("--output", "x", "-o", "y"): "duplicate option: --output",
        ("-vv",): "short options are single-letter only",
        ("bad-spec",): "duplicate short option specification: -x",
    }
    for args, message in expected_errors.items():
        out, _ = run(*args)
        # toString() on an Error is its message, with no Java class name in front.
        assert out.startswith("ERR:" + message), (args, message, out)

    help_block, _ = run()
    expected_usage = "Usage: probe [options] [--] [arguments...]\nCLI parser contract\n\nOptions:\n"
    assert expected_usage in help_block and "-v, --verbose" in help_block and "Treat all following arguments as positional." in help_block, help_block
    print("Sprig CLI library: flags, values, aliases, delimiter, positionals, duplicates, diagnostics and stable usage passed")


if __name__ == "__main__":
    main()
