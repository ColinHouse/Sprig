#!/usr/bin/env python3
"""Distinct Sprig declarations must retain their identity through JVM lowering."""
import os
import json
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[3]
SPRIG = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
CASES = {
    "same_basename": "42\nforty\n",
    "sanitized": "5\nsix\n",
    "underscore": "7\nlocal\n",
    "module_type": "8\n",
    "module_suffix": "1\n2\n3\n",
    "variant_binary": "Case\n9\n",
    "mixed": "4\ntrue\nvariant\n",
}


def identity_cases(fixtures):
    """Bounded path/alias metamorphisms with independently specified outputs."""
    source = ('class Box:\n    let value: Int\n'
              'func read(box: Box) -> Int:\n    return box.value\n'
              'var counter = 0\nprint("INITIALIZATION_SENTINEL")\n')
    chain = fixtures / "facade_chain"
    chain.mkdir()
    (chain / "core.spr").write_text(source)
    names = ("Box", "read", "counter")
    previous = "core"
    for index in range(40):
        current = f"chain{index}"
        (chain / f"{current}.spr").write_text(
            f'import "./{previous}.spr" as source\n'
            + ''.join(f'export source.{name}\n' for name in names))
        previous = current
    for side in ("left", "right"):
        (chain / f"{side}.spr").write_text(
            'import "./core.spr" as source\n'
            + ''.join(f'export source.{name}\n' for name in names))
    (chain / "main.spr").write_text(
        'import "./chain39.spr" as chain\n'
        'import "./left.spr" as left\nimport "./right.spr" as right\n'
        'import "./core.spr" as core\n'
        'chain.counter = 7\nprint(left.counter)\n'
        'right.counter = 9\nprint(core.counter)\n'
        'print(chain.read(right.Box(value=11)))\n')
    direct = fixtures / "direct_import"
    direct.mkdir()
    (direct / "core.spr").write_text(source)
    (direct / "main.spr").write_text(
        'import "./core.spr" as core\n'
        'core.counter = 7\nprint(core.counter)\n'
        'core.counter = 9\nprint(core.counter)\n'
        'print(core.read(core.Box(value=11)))\n')

    for mode, second in (("same_module", "./model.spr"),
                         ("copied_module", "./copy/model.spr")):
        folder = fixtures / mode
        (folder / "copy").mkdir(parents=True)
        (folder / "model.spr").write_text(source)
        (folder / "copy/model.spr").write_text(source)
        (folder / "main.spr").write_text(
            'import "./model.spr" as a\n'
            f'import "{second}" as b\n'
            'a.counter = 3\nprint(a.counter)\nprint(b.counter)\n')
        (folder / "cross.spr").write_text(
            'import "./model.spr" as a\n'
            f'import "{second}" as b\n'
            'print(b.read(a.Box(value=4)))\n')
    return {
        "facade_chain": "INITIALIZATION_SENTINEL\n7\n9\n11\n",
        "direct_import": "INITIALIZATION_SENTINEL\n7\n9\n11\n",
        "same_module": "INITIALIZATION_SENTINEL\n3\n3\n",
        "copied_module": "INITIALIZATION_SENTINEL\nINITIALIZATION_SENTINEL\n3\n0\n",
    }


def main():
    failures = []
    with tempfile.TemporaryDirectory(prefix="sprig-type-names-") as temp:
        fixtures = Path(temp) / "names"
        shutil.copytree(Path(__file__).with_name("names"), fixtures)
        cases = CASES | identity_cases(fixtures)
        for name, expected in cases.items():
            source = fixtures / name / "main.spr"
            # Separate static acceptance from javac and actual JVM behavior.
            for command in ("check", "run"):
                result = subprocess.run([str(SPRIG), command, str(source)],
                                        cwd=temp, capture_output=True, text=True,
                                        timeout=60)
                ok = result.returncode == 0 and (command == "check" or result.stdout == expected)
                if not ok:
                    failures.append(f"{name}/{command}: exit={result.returncode}, "
                                    f"stdout={result.stdout!r}, stderr={result.stderr!r}")
                print(f"{'PASS' if ok else 'FAIL'} {name}/{command}")
            # Formatter identity: canonical bytes must preserve re-exports,
            # module identity and the independently specified output.
            formatted = subprocess.run([str(SPRIG), "fmt", str(source.parent), "--json"],
                                       cwd=temp, capture_output=True, text=True, timeout=60)
            rerun = subprocess.run([str(SPRIG), "run", str(source)],
                                   cwd=temp, capture_output=True, text=True, timeout=60)
            idempotent = subprocess.run([str(SPRIG), "fmt", str(source.parent), "--check", "--json"],
                                        cwd=temp, capture_output=True, text=True, timeout=60)
            try:
                format_clean = (not json.loads(formatted.stdout)["diagnostics"]
                                and not json.loads(idempotent.stdout)["diagnostics"])
            except (ValueError, KeyError, TypeError):
                format_clean = False
            ok = (formatted.returncode == 0 and idempotent.returncode == 0 and format_clean
                  and rerun.returncode == 0 and rerun.stdout == expected)
            if not ok:
                failures.append(f"{name}/format-identity: exit={formatted.returncode}, "
                                f"stdout={rerun.stdout!r}, stderr={formatted.stderr!r}{idempotent.stderr!r}")
            print(f"{'PASS' if ok else 'FAIL'} {name}/format-identity")
        for mode in ("same_module", "copied_module"):
            cross = subprocess.run([str(SPRIG), "check", str(fixtures / mode / "cross.spr"),
                                    "--json"], cwd=temp, capture_output=True, text=True, timeout=60)
            try:
                diagnostics = json.loads(cross.stdout)["diagnostics"]
                codes = [diagnostic["code"] for diagnostic in diagnostics]
                ok = (cross.returncode == (0 if mode == "same_module" else 1)
                      and codes == ([] if mode == "same_module" else ["SPR-TYPE-MISMATCH"]))
            except (ValueError, KeyError, TypeError):
                ok = False
            print(f"{'PASS' if ok else 'FAIL'} {mode}/nominal-identity")
            if not ok:
                failures.append(f"{mode}/nominal-identity: {cross.stdout!r} {cross.stderr!r}")
        api = subprocess.run([str(SPRIG), "api", str(fixtures / "facade_chain/chain39.spr"),
                              "--json"], cwd=temp, capture_output=True, text=True, timeout=60)
        try:
            data = json.loads(api.stdout)
            entries = data["declarations"] + data["variables"]
            ok = (api.returncode == 0 and not api.stderr
                  and "INITIALIZATION_SENTINEL" not in api.stdout
                  and {entry["name"] for entry in entries} == {"Box", "read", "counter"}
                  and all(entry["originModule"] == "./core.spr" for entry in entries))
        except (ValueError, KeyError, TypeError):
            ok = False
        print(f"{'PASS' if ok else 'FAIL'} facade_chain/api-no-init")
        if not ok:
            failures.append(f"facade_chain/api-no-init: {api.stdout!r} {api.stderr!r}")
    for failure in failures:
        print(failure)
    print(f"type naming: {len(cases) * 3 + 3} checks, {len(failures)} failures")
    return bool(failures)


if __name__ == "__main__":
    raise SystemExit(main())
