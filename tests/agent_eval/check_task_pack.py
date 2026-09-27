#!/usr/bin/env python3
"""Keep the agent task pack deterministic: initial states fail, known solutions pass."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
RUNNER = HERE / "run_tasks.py"

SOLUTIONS = {
    "01_collections": {
        "main.spr": '''let values = [3, -1, 4, 2, -5]

var total = 0
var positive = 0
for value in values:
    total += value
    if value > 0:
        positive += 1
print("total=" + total.toString())
print("positive=" + positive.toString())
''',
    },
    "02_nullability": {
        "main.spr": '''func first(values: List[String]) -> String?:
    if values.size() == 0:
        return null
    return values.get(0)

let values: List[String] = ["sprig"]
let name = first(values)
if name != null:
    print(name)
''',
    },
    "03_variants": {
        "main.spr": '''variant Shape:
    Circle(radius: Int)
    Square(side: Int)

func area(shape: Shape) -> Int:
    match shape:
        case Shape.Circle as circle:
            return circle.radius * circle.radius * 3
        case Shape.Square as square:
            return square.side * square.side

print(area(Shape.Circle(radius=2)))
print(area(Shape.Square(side=3)))
''',
    },
    "04_generics": {
        "main.spr": '''generic T:
    class Box:
        let value: T

let box = Box[Int](value=41)
print(box.value + 1)
''',
    },
    "05_json_transform": {
        "main.spr": '''import "@std/files.spr" as files
import "@std/json.spr" as json
import "@std/process.spr" as process

func count_items(document: json.Value) -> Int throws Error:
    match json.find_member(document, "items"):
        case json.Lookup.Found as found:
            match found.value:
                case json.Value.Array as items:
                    return items.values.size()
                case json.Value.Null:
                    return 0
                case json.Value.Boolean:
                    return 0
                case json.Value.Number:
                    return 0
                case json.Value.Text:
                    return 0
                case json.Value.Object:
                    return 0
        case json.Lookup.Missing:
            return 0
        case json.Lookup.NotObject:
            return 0

let path = process.arguments().get(0)
let document = json.parse(files.read_utf8(path))
print("items=" + count_items(document).toString())
''',
    },
    "06_unsupported_recovery": {
        "main.spr": '''class Dog:
    func speak() -> String:
        return "dog says woof"

class Cat:
    func speak() -> String:
        return "cat says meow"

let dog = Dog()
let cat = Cat()
print(dog.speak())
print(cat.speak())
''',
    },
    "07_module_repair": {
        "sprig.toml": '''[project]
name = "module-repair"
version = "0.1.0"
language = "0.8"
source = "src"
entry = "src/main.spr"
''',
        "src/math.spr": '''func square(value: Int) -> Int:
    return value * value
''',
        "src/main.spr": '''import "./math.spr" as m

print(m.square(4))
''',
    },
}


def run(*args):
    return subprocess.run([sys.executable, str(RUNNER), *map(str, args)], cwd=ROOT,
                          env=dict(os.environ, PYTHONUTF8="1"), text=True,
                          encoding="utf-8", capture_output=True, timeout=1800)


self_check = run("--expect-unsolved")
if self_check.returncode != 0:
    print(self_check.stdout + self_check.stderr)
    raise SystemExit("task pack fixture self-check failed")

failures = []
with tempfile.TemporaryDirectory(prefix="sprig-task-pack-") as temp:
    work = Path(temp)
    for task, files in SOLUTIONS.items():
        submission = work / task
        for relative, content in files.items():
            path = submission / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
        result = run("--task", task, "--submission", submission)
        if result.returncode != 0:
            failures.append(task)
            print(f"FAIL {task}\n{result.stdout}{result.stderr}")

if failures:
    print("task pack: known solutions rejected: " + ", ".join(failures))
    raise SystemExit(1)
print(f"task pack: {len(SOLUTIONS)} initial states fail and known solutions pass")
