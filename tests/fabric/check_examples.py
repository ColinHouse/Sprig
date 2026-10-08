#!/usr/bin/env python3
"""Build the Fabric example mods of examples/ with Loom, outside the checkout.

Like check_template.py, this is an explicit host-framework test and not a
default gate: Loom needs Minecraft and Fabric, from ~/.gradle when they are
cached and from the network otherwise. The Minecraft-free part of each
example, its Sprig domain modules and their tests, runs in the default gate
(tests/examples/check_dogfood_programs.py).

    python3 tests/fabric/check_examples.py [--sdk DIR] [--offline]
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[2]
TEMPLATE = ROOT / "libraries" / "sprig-fabric" / "template"
# What each example's mod jar and generated Java must contain. A new
# examples/fabric_* directory must be listed here, so none is skipped.
EXAMPLES = {
    "fabric_waypoints": {
        "entries": ["dev/sprig/waypoints/SprigWaypoints.class",
                    "sprig/user/$M_main.class", "sprig/user/$WaypointMarker.class",
                    "sprig/user/$TabEntry.class", "sprig/user/$M_store.class",
                    "sprig/runtime/SprigRuntime.class",
                    "assets/sprig_waypoints/lang/en_us.json",
                    "assets/sprig_waypoints/items/waypoint_marker.json",
                    "assets/sprig_waypoints/models/item/waypoint_marker.json"],
        "generated": {"$WaypointMarker.java": ["extends net.minecraft.world.item.Item",
                                               "public net.minecraft.world.InteractionResult use("]},
    },
}
# The starter's verified combination; an example must not drift from it.
PINNED = ("minecraft_version", "loader_version", "fabric_api_version", "loom_version", "java_version")
WRAPPER = ("gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar",
           "gradle/wrapper/gradle-wrapper.properties")


def run(argv: list[str], *, cwd: Path, env: dict[str, str]) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(argv, cwd=cwd, env=env, text=True, capture_output=True)
    if result.returncode:
        print("command:", " ".join(argv), file=sys.stderr)
        print(result.stdout, end="", file=sys.stderr)
        print(result.stderr, end="", file=sys.stderr)
    return result


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def properties(path: Path) -> dict[str, str]:
    values = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, _, value = line.partition("=")
            values[key.strip()] = value.strip()
    return values


def check_example(name: str, spec: dict, *, sdk: Path, offline: bool) -> None:
    source = ROOT / "examples" / name
    for path in WRAPPER:
        require((source / path).read_bytes() == (TEMPLATE / path).read_bytes(),
                f"{name}/{path} must be the starter's, so both build with the same Gradle")
    example_properties = properties(source / "gradle.properties")
    template_properties = properties(TEMPLATE / "gradle.properties")
    for key in PINNED:
        require(example_properties.get(key) == template_properties.get(key),
                f"{name}: {key} must match the starter's verified combination")
    tests = sorted(path.name for path in (source / "tests").glob("*.spr"))
    require(tests, f"{name} must have Sprig tests")

    env = os.environ.copy()
    env["SPRIG_HOME"] = str(sdk)
    with tempfile.TemporaryDirectory(prefix="sprig fabric example ") as temp:
        project = Path(temp) / f"{name} with spaces"
        shutil.copytree(source, project, ignore=shutil.ignore_patterns(".gradle", "build", "run"))
        lock = project / "sprig.lock"
        require(lock.is_file(), f"{name} must carry the lock that `sprig resolve` generated")
        lock_bytes = lock.read_bytes()

        gradlew = str(project / ("gradlew.bat" if os.name == "nt" else "gradlew"))
        command = [gradlew, "--no-daemon", *(["--offline"] if offline else []), "clean", "build"]
        build = run(command, cwd=project, env=env)
        require(build.returncode == 0, f"{name}: Gradle clean build must check, test, compile and package the mod")
        output = build.stdout + build.stderr
        summary = '"summary":{"total":%d,"passed":%d,"failed":0}' % (len(tests), len(tests))
        require(summary in output, f"{name}: sprigTest must run and pass all {len(tests)} test files ({summary})")
        require(lock.read_bytes() == lock_bytes, f"{name}: the build must not rewrite sprig.lock")

        version = example_properties["mod_version"]
        jars = [path for path in (project / "build" / "libs").glob("*.jar") if not path.name.endswith("-sources.jar")]
        require(len(jars) == 1, f"{name}: expected one mod jar, found {[path.name for path in jars]}")
        with zipfile.ZipFile(jars[0]) as jar:
            names = jar.namelist()
            for entry in ["fabric.mod.json", *spec["entries"]]:
                require(names.count(entry) == 1, f"{name}: the mod jar must contain exactly one {entry}")
            metadata = json.loads(jar.read("fabric.mod.json").decode("utf-8"))
        require(metadata.get("version") == version, f"{name}: fabric.mod.json must carry version {version}")
        for kind, entrypoints in metadata.get("entrypoints", {}).items():
            for entrypoint in entrypoints:
                class_file = entrypoint.replace(".", "/") + ".class"
                require(class_file in names, f"{name}: the {kind} entrypoint {entrypoint} is not in the jar")

        generated = project / "build/generated/sprig/main/java/sprig/user"
        for file, fragments in spec["generated"].items():
            text = (generated / file).read_text(encoding="utf-8") if (generated / file).is_file() else ""
            for fragment in fragments:
                require(fragment in text, f"{name}: generated {file} must contain {fragment!r}")
        require(not list((project / "src").rglob("$M_*.java")), f"{name}: generated Java must stay under build/")
    print(f"pass {name}: Loom build, {len(tests)} Sprig test files, mod jar {jars[0].name}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--sdk", type=Path, default=Path(os.environ.get("SPRIG_HOME", ROOT)),
                        help="Sprig SDK root containing bin/sprig and libraries/sprig-gradle (default: this checkout)")
    parser.add_argument("--offline", action="store_true",
                        help="pass --offline to Gradle: Minecraft, Fabric and Loom must already be in ~/.gradle")
    args = parser.parse_args()
    sdk = args.sdk.resolve()
    launcher = sdk / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
    require(launcher.is_file(), f"Sprig launcher is missing from the selected SDK: {launcher}")
    present = sorted(path.name for path in (ROOT / "examples").glob("fabric_*") if path.is_dir())
    require(present == sorted(EXAMPLES), f"examples/fabric_* {present} must match the EXAMPLES table {sorted(EXAMPLES)}")
    for name, spec in EXAMPLES.items():
        check_example(name, spec, sdk=sdk, offline=args.offline)
    print(f"Fabric/Loom examples: {len(EXAMPLES)} passed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, RuntimeError, subprocess.SubprocessError, ValueError) as error:
        print(f"FAIL Fabric/Loom examples: {error}", file=sys.stderr)
        raise SystemExit(1)
