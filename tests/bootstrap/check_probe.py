#!/usr/bin/env python3
"""Exercise the Sprig-written frontend probe through check, javac and JVM."""
import json
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / "sprig"
PROGRAM = ROOT / "examples" / "stage1_frontend_probe" / "frontend.spr"
GOLDEN = PROGRAM.with_suffix(".out").read_text(encoding="utf-8")


def call(*args):
    return subprocess.run([str(SPRIG), *map(str, args)], cwd=ROOT,
                          text=True, capture_output=True)


checked = call("check", PROGRAM, "--json")
assert checked.returncode == 0, checked.stdout
assert json.loads(checked.stdout)["diagnostics"] == []
with tempfile.TemporaryDirectory(prefix="sprig-stage1-probe-") as temp:
    built = call("build", PROGRAM, "-d", Path(temp) / "build", "--json")
    assert built.returncode == 0, built.stdout
    assert any((Path(temp) / "build" / "classes").rglob("*.class"))
    evolved = Path(temp) / "evolved.spr"
    original = PROGRAM.read_text(encoding="utf-8")
    needle = "    Invalid(span: Span)\n\nvariant Stmt:"
    assert needle in original
    evolved.write_text(original.replace(needle,
        "    Invalid(span: Span)\n    Extra(span: Span)\n\nvariant Stmt:", 1), encoding="utf-8")
    outcome = call("check", evolved, "--json")
    codes = [d["code"] for d in json.loads(outcome.stdout)["diagnostics"]]
    assert outcome.returncode == 1 and "SPR-MATCH-NONEXHAUSTIVE" in codes, codes

executed = call("run", PROGRAM, "--json")
data = json.loads(executed.stdout)
assert executed.returncode == 0, executed.stdout
assert data["programOutput"] == GOLDEN, (data["programOutput"], GOLDEN)
assert "diagnostics=0" in GOLDEN
assert "PROBE-LEX 3:1 [22,24)" in GOLDEN
assert "PROBE-PARSE 1:5 [4,5)" in GOLDEN
assert "PROBE-NAME 3:9 [25,32)" in GOLDEN
print("stage-1 frontend probe: check, javac build, JVM golden, malformed ranges, exhaustive evolution passed")
