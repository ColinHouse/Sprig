#!/usr/bin/env python3
"""The dogfooding programs of #133 run from a copy of the repository.

Each program is copied with the libraries it depends on, resolved, checked,
run once as its README says, and its own `sprig test` suite is run; the blog
output and the CLI's JSON file are checked as a user would see them. The todo
test makes real HTTP requests through sprig-http, so no curl is needed.

The Fabric mod (examples/fabric_waypoints) runs here without Minecraft: its
domain modules are checked and its `sprig test` suite runs, and only its glue
module may import Minecraft or Fabric classes. Building the mod itself needs
Loom, so that is tests/fabric/check_examples.py, outside the default gate."""
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
PROGRAMS = ("blog", "todo", "tasks", "crawler")
FABRIC_MOD = "fabric_waypoints"
FABRIC_GLUE = "main.spr"  # the one module of the mod that talks to Minecraft
# Use the marker, then sneak-use it, as main.spr's act does for a player at 10 64 -20.
GAME = """public final class Game {
    public static void main(String[] args) {
        String file = PACKAGE.$M_store.fn$world_file(args[0]);
        PACKAGE.$Position here = new PACKAGE.$Position("minecraft:overworld", 10L, 64L, -20L);
        System.out.println(PACKAGE.$M_marker.fn$record_at(file, "Home", here));
        System.out.println(PACKAGE.$M_marker.fn$list_from(file, here));
    }
}
"""
GAME_OUTPUT = "[Saved Home at 10, 64, -20 (overworld).]\n[1 waypoint:,   Home: here]\n"
FAILURES = []
COUNT = 0


def check(name, ok, detail=""):
    global COUNT
    COUNT += 1
    print(("pass " if ok else "FAIL ") + name)
    if not ok:
        FAILURES.append(f"{name}: {detail}")


def sprig(cwd, *args, timeout=600):
    env = dict(os.environ)
    env.pop("JAVA_TOOL_OPTIONS", None)
    return subprocess.run([str(SPRIG), *map(str, args)], cwd=cwd, env=env, text=True,
                          encoding="utf-8", capture_output=True, timeout=timeout)


def main():
    with tempfile.TemporaryDirectory(prefix="sprig dogfood ") as temporary:
        work = Path(temporary)
        for library in ("sprig-sqlite", "sprig-web", "sprig-http", "sprig-cli"):
            shutil.copytree(ROOT / "libraries" / library, work / "libraries" / library,
                            ignore=shutil.ignore_patterns("sprig.lock", "sprig-build"))
        for program in PROGRAMS:
            shutil.copytree(ROOT / "examples" / program, work / "examples" / program,
                            ignore=shutil.ignore_patterns("sprig.lock", "sprig-build", "site"))
        shutil.copytree(ROOT / "examples" / FABRIC_MOD, work / "examples" / FABRIC_MOD,
                        ignore=shutil.ignore_patterns("sprig.lock", "sprig-build", ".gradle", "build", "run"))
        for program in PROGRAMS:
            project = work / "examples" / program
            resolved = sprig(project, "resolve")
            check(f"{program}-resolve", resolved.returncode == 0, resolved.stdout + resolved.stderr)
            checked = sprig(project, "check", "--offline", "--json")
            payload = json.loads(checked.stdout) if checked.stdout.strip().startswith("{") else {}
            check(f"{program}-check", checked.returncode == 0 and payload.get("diagnostics") == [], checked.stdout[-600:] + checked.stderr[-300:])
            tested = sprig(project, "test", "--offline")
            check(f"{program}-test", tested.returncode == 0 and "0 failed" in tested.stdout, tested.stdout[-800:] + tested.stderr[-300:])

        blog = work / "examples" / "blog"
        out = work / "blog site"
        ran = sprig(blog, "run", "--offline", "--", "posts", str(out))
        pages = sorted(p.name for p in out.glob("*.html")) if out.is_dir() else []
        check("blog-renders-site", ran.returncode == 0 and "index.html" in pages and len(pages) >= 3
              and (out / "style.css").is_file() and "<h1>" in (out / "index.html").read_text(encoding="utf-8"),
              f"exit={ran.returncode} pages={pages} {ran.stdout[-300:]} {ran.stderr[-300:]}")
        bad = blog / "bad posts"
        bad.mkdir()
        (bad / "broken.md").write_text("no front matter\n", encoding="utf-8")
        failed = sprig(blog, "run", "--offline", "--", str(bad), str(work / "unused"))
        check("blog-bad-post-exits-2", failed.returncode == 2 and "broken.md" in failed.stdout + failed.stderr,
              f"exit={failed.returncode} {failed.stdout[-300:]} {failed.stderr[-300:]}")

        tasks = work / "examples" / "tasks"
        # java.exe reads its command line in the ANSI code page (a documented limit), so
        # the Windows lane keeps the path and the title ASCII; Linux and macOS carry UTF-8.
        windows = os.name == "nt"
        store = work / ("tasks list.json" if windows else "tasks 清单.json")
        title = "milk" if windows else "milk 牛奶"
        added = sprig(tasks, "run", "--offline", "--", "--file", str(store), "add", "Buy", title)
        listed = sprig(tasks, "run", "--offline", "--", "--file", str(store), "list")
        done = sprig(tasks, "run", "--offline", "--", "--file", str(store), "done", "1")
        listed_all = sprig(tasks, "run", "--offline", "--", "--file", str(store), "list", "--all")
        check("tasks-cli-round-trip", added.returncode == 0 and listed.returncode == 0 and done.returncode == 0
              and "Buy " + title in listed.stdout and store.is_file()
              and listed_all.returncode == 0 and "Buy " + title in listed_all.stdout,
              f"{added.returncode} {added.stdout[-200:]} {listed.stdout[-200:]} {done.stdout[-200:]} {added.stderr[-200:]}")
        usage = sprig(tasks, "run", "--offline", "--", "frobnicate")
        check("tasks-unknown-command-fails", usage.returncode != 0, usage.stdout[-200:])

        crawler = work / "examples" / "crawler"
        crawled = sprig(crawler, "run", "--offline")
        again = sprig(crawler, "run", "--offline")
        check("crawler-deterministic-report", crawled.returncode == 0 and crawled.stdout == again.stdout
              and "index.html" in crawled.stdout and "FAILED" in crawled.stdout,
              f"exit={crawled.returncode} {crawled.stdout[-400:]} {crawled.stderr[-300:]}")

        mod = work / "examples" / FABRIC_MOD
        resolved = sprig(mod, "resolve")
        check(f"{FABRIC_MOD}-resolve", resolved.returncode == 0, resolved.stdout + resolved.stderr)
        minecraft = re.compile(r"^import (net\.minecraft|net\.fabricmc|com\.mojang)\.", re.M)
        importers = sorted(source.name for source in (mod / "src").glob("*.spr")
                           if minecraft.search(source.read_text(encoding="utf-8")))
        check(f"{FABRIC_MOD}-only-glue-imports-minecraft", importers == [FABRIC_GLUE], f"modules importing Minecraft or Fabric: {importers}")
        for source in sorted((mod / "src").glob("*.spr")):
            if source.name == FABRIC_GLUE:
                continue
            checked = sprig(mod, "check", source.relative_to(mod), "--offline", "--json")
            payload = json.loads(checked.stdout) if checked.stdout.strip().startswith("{") else {}
            check(f"{FABRIC_MOD}-check-{source.stem}", checked.returncode == 0 and payload.get("diagnostics") == [],
                  checked.stdout[-600:] + checked.stderr[-300:])
        tests = len(list((mod / "tests").glob("*.spr")))
        tested = sprig(mod, "test", "--offline")
        check(f"{FABRIC_MOD}-test-without-minecraft", tested.returncode == 0 and tests > 0 and f"{tests} passed, 0 failed" in tested.stdout,
              tested.stdout[-800:] + tested.stderr[-300:])

        # In the game no generated main runs: SprigWaypoints calls a function of
        # main.spr, and Minecraft calls the marker's use method. The modules'
        # top-level bindings (the file name, the format version, the list limit)
        # are there only because the first call from Java initializes them. This
        # enters the domain modules the way main.spr's act does, from Java.
        built = sprig(mod, "build", "src/marker.spr", "--offline", "--json")
        payload = json.loads(built.stdout) if built.stdout.strip().startswith("{") else {}
        package = payload.get("mainClass", "").rpartition(".")[0]
        game = work / "game"
        game.mkdir()
        (game / "Game.java").write_text(GAME.replace("PACKAGE", package), encoding="utf-8")
        javac, java = shutil.which("javac"), shutil.which("java")
        classes = payload.get("classes", "")
        compiled = subprocess.run([javac, "-encoding", "UTF-8", "-cp", classes, "-d", str(game / "classes"),
                                   str(game / "Game.java")], text=True, encoding="utf-8", capture_output=True)
        world = work / "waypoint world"
        world.mkdir()
        played = subprocess.run([java, "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-cp",
                                 os.pathsep.join([str(game / "classes"), classes]), "Game", str(world)],
                                text=True, encoding="utf-8", capture_output=True)
        saved = world / "sprig_waypoints.json"
        check(f"{FABRIC_MOD}-entered-from-java", built.returncode == 0 and compiled.returncode == 0
              and played.returncode == 0 and played.stdout == GAME_OUTPUT
              and saved.is_file() and '"version": 1' in saved.read_text(encoding="utf-8"),
              f"{built.stdout[-300:]} {compiled.stderr[-600:]} {played.stdout[-300:]} {played.stderr[-900:]}")

    print(f"dogfood programs: {COUNT - len(FAILURES)} checks passed, {len(FAILURES)} failed")
    for failure in FAILURES:
        print("  " + failure)
    return 1 if FAILURES else 0


if __name__ == "__main__":
    sys.exit(main())
