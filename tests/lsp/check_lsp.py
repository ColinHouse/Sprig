#!/usr/bin/env python3
"""Real-process checks for `sprig lsp`: JSON-RPC over stdio against bin/sprig."""

import json
import os
from pathlib import Path
import queue
import subprocess
import sys
import tempfile
import threading
import time

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin" / ("sprig.cmd" if os.name == "nt" else "sprig")
COUNT = 0
TIMEOUT = 120

SHAPES = '''# A shape with a size.
variant Shape:
    Circle(radius: Float)
    Square(side: Float)

enum Color:
    Red
    Green

class Point:
    let x: Int
    var y: Int

    func moved(dx: Int) -> Point:
        return Point(x=x + dx, y=y)

# Area of a shape.
func area(shape: Shape) -> Float:
    return match shape:
        case Shape.Circle as circle:
            3.0 * circle.radius * circle.radius
        case Shape.Square as square:
            square.side * square.side
'''

MAIN = '''import "./shapes.spr" as shapes

func total(items: List[shapes.Shape]) -> Float:
    var sum = 0.0
    for item in items:
        sum += shapes.area(item)
    return sum

let p = shapes.Point(x=1, y=2)
let moved = p.moved(3)
let word = "abc"
let maybe: String? = "x"
if maybe != null:
    print(maybe.length())
print("😀😀" + word.toUpperCase())
print(moved.x)
print(total([shapes.Shape.Circle(radius=1.0)]))
try:
    throw Error("bad")
catch problem: Error:
    print(problem.toString() + problem.message)
'''

BAD = '''func broken() -> Int:
    return "text"
'''

USES_BAD = '''import "./bad.spr" as bad

print(bad.broken())
'''

# The compiler's mechanical rewrites, as in tests/agent_tooling/check_newcomer_hints.py:
# (name, source, code, whether the program checks cleanly once the fix is applied).
QUICK_FIXES = [
    ("std-import", 'let text = files.read_utf8("x.txt")\nprint(text.length())\n', "SPR-NAME-UNRESOLVED", False),
    ("throws-on-header", 'import "@std/process.spr" as process\nfunc count() -> Int:\n'
     '    return process.read_lines().size()\nprint(count())\n', "SPR-FLOW-THROWS", True),
    ("throws-added-to-list", 'import java.io.IOException as IOException\nfunc fail() -> Unit throws IOException:\n'
     '    throw IOException("x")\nfunc parse(text: String) -> Int throws Error:\n    throw Error(text)\n'
     'func wrap(text: String) -> Int throws IOException:\n    fail()\n    return parse(text)\nprint(wrap("1"))\n',
     "SPR-FLOW-THROWS", True),
    ("named-constructor", "class P:\n    let x: Int\n    let y: Int\nlet p = P(1, 2 + 3)\nprint(p.y)\n",
     "SPR-CALL-NAMED-REQUIRED", True),
    # After an emoji, compiler columns (code points) and LSP characters (UTF-16 units) differ.
    ("named-constructor-after-emoji", 'class P:\n    let x: Int\n    let y: Int\n'
     'let total = "😀😀".length() + P(1, 2).x\nprint(total)\n', "SPR-CALL-NAMED-REQUIRED", True),
    ("elif", "let x = 3\nif x > 5:\n    print(1)\nelse if x > 1:\n    print(2)\n", "SPR-SYNTAX-ERROR", True),
]

# What the test client declares: prepared rename and CodeAction literals, as VS Code's client does.
CLIENT_CAPABILITIES = {"textDocument": {
    "rename": {"prepareSupport": True},
    "codeAction": {"codeActionLiteralSupport": {"codeActionKind": {"valueSet": ["quickfix"]}},
                   "isPreferredSupport": True}}}

IF_EXPRESSIONS = '''func describe(value: Int?, limit: Int) -> String:
    let doubled = limit * 2
    return if value == null:
        "none"
    elif value != null and value > doubled:
        "big " + (value - doubled)
    else:
        "small " + doubled

let total = 3
let label = if total > 2:
    describe(total, total)
else:
    "few"
print(label)
'''


def check(name, ok, detail=""):
    global COUNT
    if not ok:
        raise AssertionError(name + (": " + str(detail) if detail != "" else ""))
    COUNT += 1
    print("pass", name)


def uri(path):
    return Path(path).resolve().as_uri()


class Client:
    """A minimal LSP client: Content-Length framing over the server's stdio."""

    def __init__(self, cwd):
        self.proc = subprocess.Popen([str(SPRIG), "lsp", "--stdio"], cwd=cwd, stdin=subprocess.PIPE,
                                     stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.incoming = queue.Queue()
        self.notifications = []
        self.stderr = []
        self.next_id = 0
        threading.Thread(target=self._read, daemon=True).start()
        threading.Thread(target=self._drain, daemon=True).start()

    def _drain(self):
        for line in self.proc.stderr:
            self.stderr.append(line.decode("utf-8", "replace"))

    def _read(self):
        while True:
            headers = {}
            while True:
                line = self.proc.stdout.readline()
                if not line:
                    self.incoming.put(None)
                    return
                line = line.decode("ascii").strip()
                if not line:
                    break
                key, value = line.split(":", 1)
                headers[key.strip().lower()] = value.strip()
            body = self.proc.stdout.read(int(headers["content-length"]))
            self.incoming.put(json.loads(body.decode("utf-8")))

    def _next(self, deadline):
        try:
            message = self.incoming.get(timeout=max(0.05, deadline - time.time()))
        except queue.Empty:
            return False
        if message is None:
            raise RuntimeError("server exited: " + "".join(self.stderr))
        self.notifications.append(message)
        return True

    def send(self, message):
        body = json.dumps(message).encode("utf-8")
        self.proc.stdin.write(b"Content-Length: %d\r\n\r\n" % len(body) + body)
        self.proc.stdin.flush()

    def notify(self, method, params):
        self.send({"jsonrpc": "2.0", "method": method, "params": params})

    def request(self, method, params):
        self.next_id += 1
        ident = self.next_id
        self.send({"jsonrpc": "2.0", "id": ident, "method": method, "params": params})
        deadline = time.time() + TIMEOUT
        while time.time() < deadline:
            for index, message in enumerate(self.notifications):
                if message.get("id") == ident and "method" not in message:
                    del self.notifications[index]
                    return message
            self._next(deadline)
        raise TimeoutError(method)

    def wait_diagnostics(self, path, predicate=lambda diagnostics: True):
        """The first diagnostics published for path, from now on, that satisfy predicate."""
        target = uri(path)
        deadline = time.time() + TIMEOUT
        seen = 0
        while time.time() < deadline:
            published = [m["params"] for m in self.notifications
                         if m.get("method") == "textDocument/publishDiagnostics"
                         and m["params"]["uri"] == target]
            for params in published[seen:]:
                if predicate(params["diagnostics"]):
                    self.notifications = [m for m in self.notifications
                                          if not (m.get("method") == "textDocument/publishDiagnostics"
                                                  and m["params"]["uri"] == target)]
                    return params["diagnostics"]
            seen = len(published)
            self._next(deadline)
        raise TimeoutError("diagnostics for " + str(path))

    def diagnostics_at(self, path, version):
        """The diagnostics published for one version of a document."""
        target = uri(path)
        deadline = time.time() + TIMEOUT
        while time.time() < deadline:
            for index, message in enumerate(self.notifications):
                params = message.get("params") or {}
                if (message.get("method") == "textDocument/publishDiagnostics" and params["uri"] == target
                        and params.get("version") == version):
                    self.notifications = [m for m in self.notifications[:index]
                                          if not (m.get("method") == "textDocument/publishDiagnostics"
                                                  and m["params"]["uri"] == target)] + self.notifications[index + 1:]
                    return params["diagnostics"]
            self._next(deadline)
        raise TimeoutError(f"diagnostics for {path} at version {version}")

    def open(self, path, text, version=1):
        self.notify("textDocument/didOpen", {"textDocument": {
            "uri": uri(path), "languageId": "sprig", "version": version, "text": text}})

    def change(self, path, text, version):
        self.notify("textDocument/didChange", {"textDocument": {"uri": uri(path), "version": version},
                                               "contentChanges": [{"text": text}]})

    def at(self, method, path, line, character, **extra):
        params = {"textDocument": {"uri": uri(path)}, "position": {"line": line, "character": character}}
        params.update(extra)
        return self.request(method, params)

    def stop(self, shutdown=True):
        if shutdown:
            self.request("shutdown", None)
        self.notify("exit", None)
        try:
            return self.proc.wait(timeout=30)
        finally:
            if self.proc.poll() is None:
                self.proc.kill()


def initialize(client, root, capabilities=CLIENT_CAPABILITIES):
    response = client.request("initialize", {"processId": None, "rootUri": uri(root), "capabilities": capabilities})
    client.notify("initialized", {})
    return response["result"]


def code_actions(client, path, range_, diagnostics, only=None):
    context = {"diagnostics": diagnostics}
    if only is not None:
        context["only"] = only
    response = client.request("textDocument/codeAction",
                              {"textDocument": {"uri": uri(path)}, "range": range_, "context": context})
    if "error" in response:
        raise AssertionError("textDocument/codeAction failed: " + json.dumps(response["error"]))
    return response["result"]


def utf16_range(text, compiler_range):
    """A compiler range, whose columns count code points, in LSP's UTF-16 characters."""
    lines = text.split("\n")

    def convert(point):
        prefix = lines[point["line"]][:point["character"]]
        return {"line": point["line"], "character": len(prefix.encode("utf-16-le")) // 2}
    return {"start": convert(compiler_range["start"]), "end": convert(compiler_range["end"])}


def apply_edits(text, edits):
    """The text after LSP edits (UTF-16 positions), applied last first as an editor does."""
    lines = text.split("\n")

    def index(point):
        content, units, column = lines[point["line"]], 0, 0
        while column < len(content) and units < point["character"]:
            units += len(content[column].encode("utf-16-le")) // 2
            column += 1
        return sum(len(line) + 1 for line in lines[:point["line"]]) + column
    for edit in sorted(edits, key=lambda e: (e["range"]["start"]["line"], e["range"]["start"]["character"]),
                       reverse=True):
        start, end = index(edit["range"]["start"]), index(edit["range"]["end"])
        text = text[:start] + edit["newText"] + text[end:]
    return text


def position(text, line, needle, occurrence=0, offset=0):
    """UTF-16 character of the n-th needle on a line, as LSP clients count it."""
    content = text.splitlines()[line]
    index = -1
    for _ in range(occurrence + 1):
        index = content.index(needle, index + 1)
    return len(content[:index].encode("utf-16-le")) // 2 + offset


def completion_labels(client, path, line, character):
    response = client.at("textDocument/completion", path, line, character)
    return [item["label"] for item in response["result"]["items"]]


def hover_text(client, path, line, character):
    result = client.at("textDocument/hover", path, line, character)["result"]
    return None if result is None else result["contents"]["value"]


def stop_quietly(client):
    """Ends a server that a failed check left running.

    A live server keeps its working directory locked on Windows, where
    sprig.cmd's JVM survives killing the launcher alone."""
    if client.proc.poll() is None:
        if os.name == "nt":
            subprocess.run(["taskkill", "/PID", str(client.proc.pid), "/T", "/F"], capture_output=True)
        client.proc.kill()


def check_workspace(directory):
    shapes, main = directory / "shapes.spr", directory / "main.spr"
    bad, uses_bad = directory / "bad.spr", directory / "uses_bad.spr"
    shapes.write_text(SHAPES, encoding="utf-8")
    main.write_text(MAIN, encoding="utf-8")
    bad.write_text(BAD, encoding="utf-8")
    uses_bad.write_text(USES_BAD, encoding="utf-8")
    run = subprocess.run([str(SPRIG), "run", str(main)], capture_output=True, text=True, cwd=directory)
    check("fixture-runs", run.returncode == 0 and run.stdout.endswith("4\n3.0\nbadbad\n"), run.stdout + run.stderr)

    client = Client(directory)
    early = client.request("textDocument/hover", {"textDocument": {"uri": uri(main)},
                                                  "position": {"line": 0, "character": 0}})
    check("request-before-initialize", early.get("error", {}).get("code") == -32002, early)
    capabilities = initialize(client, directory)["capabilities"]
    check("capabilities", capabilities["hoverProvider"] and capabilities["definitionProvider"]
          and capabilities["referencesProvider"] and capabilities["documentSymbolProvider"]
          and capabilities["documentFormattingProvider"]
          and capabilities["completionProvider"]["triggerCharacters"] == ["."]
          and capabilities["renameProvider"] == {"prepareProvider": True}
          and capabilities["codeActionProvider"] == {"codeActionKinds": ["quickfix"]}
          and capabilities["textDocumentSync"]["change"] == 1, capabilities)
    unknown = client.request("workspace/symbol", {"query": "x"})
    check("unknown-method", unknown.get("error", {}).get("code") == -32601, unknown)

    # Diagnostics come from the same pipeline as `sprig check`.
    client.open(main, MAIN)
    check("diagnostics-clean", client.wait_diagnostics(main) == [])
    broken = MAIN.replace("print(moved.x)", "print(moved.z)")
    client.change(main, broken, 2)
    errors = client.wait_diagnostics(main, lambda d: d != [])
    line = broken.splitlines().index("print(moved.z)")
    check("diagnostics-error", len(errors) == 1 and errors[0]["code"] == "SPR-NAME-UNRESOLVED"
          and errors[0]["range"]["start"]["line"] == line and errors[0]["severity"] == 1
          and "no member 'z'" in errors[0]["message"], errors)
    client.change(main, MAIN, 3)
    check("diagnostics-fixed", client.wait_diagnostics(main, lambda d: d == []) == [])

    # An unsaved buffer of an imported module is what dependents see.
    client.open(shapes, SHAPES.replace("func area(", "func surface("))
    overlay = client.wait_diagnostics(main, lambda d: d != [])
    check("unsaved-import-buffer", any("no member 'area'" in d["message"] for d in overlay), overlay)
    client.change(shapes, SHAPES, 2)
    check("unsaved-import-buffer-restored", client.wait_diagnostics(main, lambda d: d == []) == [])

    # An error inside an imported module is shown on the import that reaches it.
    client.open(uses_bad, USES_BAD)
    imported = client.wait_diagnostics(uses_bad, lambda d: d != [])
    related = imported[0].get("relatedInformation", [{}])[0].get("location", {})
    check("imported-module-error", len(imported) == 1 and imported[0]["range"]["start"]["line"] == 0
          and imported[0]["message"].startswith("bad.spr:2:")
          and related.get("uri") == uri(bad) and related["range"]["start"]["line"] == 1, imported)
    # Each diagnostic names its `sprig help` topic, as `sprig check --json` does.
    cli = subprocess.run([str(SPRIG), "check", str(bad), "--json"], capture_output=True, text=True,
                         encoding="utf-8", cwd=directory)
    topic = json.loads(cli.stdout)["diagnostics"][0].get("relatedHelp")
    check("diagnostic-help-topic", topic is not None and imported[0].get("data") == {"relatedHelp": topic},
          (topic, imported))
    client.notify("textDocument/didClose", {"textDocument": {"uri": uri(uses_bad)}})
    check("close-clears-diagnostics", client.wait_diagnostics(uses_bad, lambda d: d == []) == [])

    # Hover shows declarations in Sprig syntax with their comments and types.
    sum_line = 5
    area_hover = hover_text(client, main, sum_line, position(MAIN, sum_line, "area", offset=1))
    check("hover-function-comment", "func area(shape: Shape) -> Float" in area_hover
          and "Area of a shape." in area_hover, area_hover)
    sum_hover = hover_text(client, main, sum_line, position(MAIN, sum_line, "sum"))
    check("hover-local-type", "var sum: Float" in sum_hover and "Local variable in `total`" in sum_hover, sum_hover)
    field_line = MAIN.splitlines().index("print(moved.x)")
    field_hover = hover_text(client, main, field_line, position(MAIN, field_line, "x"))
    check("hover-field", "let x: Int" in field_hover and "Field of `Point`" in field_hover, field_hover)
    length_line = MAIN.splitlines().index("    print(maybe.length())")
    length_hover = hover_text(client, main, length_line, position(MAIN, length_line, "length"))
    check("hover-builtin-method", "length(...) -> Int" in length_hover, length_hover)
    # Sprig's Error is shown by its Sprig name, and its toString() is the builtin.
    error_line = MAIN.splitlines().index("    print(problem.toString() + problem.message)")
    to_string_hover = hover_text(client, main, error_line, position(MAIN, error_line, "toString"))
    check("hover-error-tostring", "Built-in method of `Error`" in to_string_hover, to_string_hover)
    binder_hover = hover_text(client, main, error_line, position(MAIN, error_line, "problem"))
    check("hover-error-binder", "problem: Error" in binder_hover and "SprigError" not in binder_hover,
          binder_hover)
    message_hover = hover_text(client, main, error_line, position(MAIN, error_line, "message"))
    check("hover-error-message", "message: String" in message_hover and "String?" not in message_hover,
          message_hover)
    narrowed = hover_text(client, main, length_line, position(MAIN, length_line, "maybe"))
    check("hover-narrowed-type", "let maybe: String?" in narrowed and "Here: `String`" in narrowed, narrowed)
    emoji_line = MAIN.splitlines().index('print("😀😀" + word.toUpperCase())')
    character = position(MAIN, emoji_line, "word")
    emoji = client.at("textDocument/hover", main, emoji_line, character + 1)["result"]
    check("utf16-positions", emoji is not None and emoji["range"]["start"]["character"] == character
          and "let word: String" in emoji["contents"]["value"], emoji)
    check("hover-nothing", client.at("textDocument/hover", main, 1, 0)["result"] is None)

    # Definition across modules and to an import's file.
    definition = client.at("textDocument/definition", main, sum_line,
                           position(MAIN, sum_line, "area"))["result"]
    check("definition-other-module", definition["uri"] == uri(shapes)
          and definition["range"]["start"] == {"line": 17, "character": 5}, definition)
    module = client.at("textDocument/definition", main, 0, 9)["result"]
    check("definition-import", module["uri"] == uri(shapes)
          and module["range"]["start"] == {"line": 0, "character": 0}, module)
    local = client.at("textDocument/definition", main, sum_line, position(MAIN, sum_line, "item"))["result"]
    check("definition-local", local["uri"] == uri(main) and local["range"]["start"] == {"line": 4, "character": 8},
          local)

    # References: every use of Point, in both files.
    point_line = MAIN.splitlines().index("let p = shapes.Point(x=1, y=2)")
    point = position(MAIN, point_line, "Point")
    references = client.at("textDocument/references", main, point_line, point,
                           context={"includeDeclaration": True})["result"]
    places = sorted((Path(r["uri"]).name, r["range"]["start"]["line"]) for r in references)
    check("references-across-files", places == [("main.spr", point_line), ("shapes.spr", 9),
                                                ("shapes.spr", 13), ("shapes.spr", 14)], places)
    without = client.at("textDocument/references", main, point_line, point,
                        context={"includeDeclaration": False})["result"]
    check("references-without-declaration", len(without) == 3, without)

    # Outline of the imported module.
    symbols = client.request("textDocument/documentSymbol", {"textDocument": {"uri": uri(shapes)}})["result"]
    outline = {s["name"]: [c["name"] for c in s.get("children", [])] for s in symbols}
    check("document-symbols", outline == {"Shape": ["Circle", "Square"], "Color": ["Red", "Green"],
                                          "Point": ["x", "y", "moved"], "area": []}, outline)
    circle = symbols[0]["children"][0]
    check("document-symbol-ranges", circle["selectionRange"]["start"] == {"line": 2, "character": 4}
          and circle["children"][0]["name"] == "radius", circle)

    # Completion: members after a dot, names in scope, and types.
    def complete_on(text, line, character, version):
        client.change(main, text, version)
        return completion_labels(client, main, line, character)

    end = len(MAIN.splitlines())
    labels = complete_on(MAIN + "print(moved.)\n", end, 12, 4)
    check("completion-class-members", labels[:4] == ["x", "y", "moved", "toString"], labels)
    labels = complete_on(MAIN + "print(shapes.)\n", end, 13, 5)
    check("completion-module-members", set(labels) == {"Shape", "Color", "Point", "area"}, labels)
    labels = complete_on(MAIN + "print(shapes.Shape.)\n", end, 19, 6)
    check("completion-variant-cases", labels == ["Circle", "Square"], labels)
    labels = complete_on(MAIN + "print(word.)\n", end, 11, 7)
    check("completion-string-methods", "length" in labels and "substring" in labels and "append" not in labels,
          labels)
    labels = complete_on(MAIN + "var counts = [1].toMutableList()\ncounts.\n", end + 1, 7, 8)
    check("completion-mutable-list", "append" in labels and "sort" in labels and "size" in labels, labels)
    inner = MAIN.replace("        sum += shapes.area(item)\n", "        sum += shapes.area(item)\n        it\n")
    labels = complete_on(inner, 6, 10, 9)
    check("completion-scope", labels[:3] == ["item", "sum", "items"] and "total" in labels
          and "print" in labels and "while" in labels, labels[:12])
    labels = complete_on(MAIN + "let q: Sh\n", end, 9, 10)
    check("completion-type-position", "shapes" in labels and "Int" in labels and "List" in labels
          and "print" not in labels and "while" not in labels, labels)
    unresolved = inner.replace("        it\n", "        items.\n") + "print(nowhere)\n"
    labels = complete_on(unresolved, 6, 14, 11)
    check("completion-written-type-despite-name-error", "size" in labels and "map" in labels, labels)
    client.change(main, unresolved.replace("        items.\n", ""), 12)
    client.wait_diagnostics(main, lambda d: any("nowhere" in x["message"] for x in d))
    local = client.at("textDocument/definition", main, sum_line, position(MAIN, sum_line, "item"))["result"]
    check("definition-despite-name-error", local is not None
          and local["range"]["start"] == {"line": 4, "character": 8}, local)
    labels = complete_on(MAIN + "# shapes.\n", end, 9, 13)
    check("completion-not-in-comment", labels == [], labels)
    labels = complete_on(MAIN + "let \n", end, 4, 14)
    check("completion-not-for-new-name", labels == [], labels)

    # Formatting is `sprig fmt`, as one whole-document edit.
    client.change(main, MAIN.replace("var sum = 0.0", "var sum   =   0.0"), 15)
    edits = client.request("textDocument/formatting", {"textDocument": {"uri": uri(main)},
                                                       "options": {"tabSize": 4, "insertSpaces": True}})["result"]
    check("formatting-edit", len(edits) == 1 and edits[0]["newText"] == MAIN
          and edits[0]["range"]["start"] == {"line": 0, "character": 0}, edits)
    client.change(main, MAIN, 16)
    edits = client.request("textDocument/formatting", {"textDocument": {"uri": uri(main)},
                                                       "options": {"tabSize": 4, "insertSpaces": True}})["result"]
    check("formatting-canonical", edits == [], edits)
    client.change(main, MAIN + "let = \n", 17)
    edits = client.request("textDocument/formatting", {"textDocument": {"uri": uri(main)},
                                                       "options": {"tabSize": 4, "insertSpaces": True}})["result"]
    check("formatting-syntax-error", edits is None, edits)

    # During a syntax error: no guessed answers, but the outline keeps the last parse.
    check("syntax-error-hover", client.at("textDocument/hover", main, sum_line, 9)["result"] is None)
    symbols = client.request("textDocument/documentSymbol", {"textDocument": {"uri": uri(main)}})["result"]
    check("syntax-error-outline", [s["name"] for s in symbols][:3] == ["total", "p", "moved"], symbols)
    client.change(main, MAIN, 18)
    client.wait_diagnostics(main, lambda d: d == [])

    # Rename is limited to locals and parameters and verified before answering.
    prepared = client.at("textDocument/prepareRename", main, 3, 9)["result"]
    check("prepare-rename", prepared["placeholder"] == "sum"
          and prepared["range"]["start"] == {"line": 3, "character": 8}, prepared)
    renamed = client.at("textDocument/rename", main, 3, 9, newName="acc")["result"]["changes"][uri(main)]
    check("rename-local", sorted((e["range"]["start"]["line"], e["range"]["start"]["character"]) for e in renamed)
          == [(3, 8), (5, 8), (6, 11)] and all(e["newText"] == "acc" for e in renamed), renamed)
    renamed = client.at("textDocument/rename", main, 2, 11, newName="values")["result"]["changes"][uri(main)]
    check("rename-parameter", len(renamed) == 2, renamed)
    capture = client.at("textDocument/rename", main, 3, 9, newName="item")
    check("rename-refuses-capture", capture.get("error", {}).get("code") == -32803
          and "would change what other names refer to" in capture["error"]["message"], capture)
    keyword = client.at("textDocument/rename", main, 3, 9, newName="while")
    check("rename-refuses-keyword", keyword.get("error", {}).get("code") == -32602, keyword)
    function = client.at("textDocument/prepareRename", main, 2, 6)
    check("rename-refuses-shared-name", function.get("error", {}).get("code") == -32803
          and "Only local variables and parameters" in function["error"]["message"], function)

    check("shutdown-exit", client.stop() == 0, "".join(client.stderr))
    unclean = Client(directory)
    initialize(unclean, directory)
    check("exit-without-shutdown", unclean.stop(shutdown=False) == 1)


def check_if_expressions(directory):
    """Names in if-expression conditions and branches: hover sees the if statement's narrowing, rename reaches them all."""
    path = directory / "choose.spr"
    path.write_text(IF_EXPRESSIONS, encoding="utf-8")
    client = Client(directory)
    try:
        initialize(client, directory)
        client.open(path, IF_EXPRESSIONS)
        check("if-expression-clean", client.wait_diagnostics(path) == [])
        narrowed = hover_text(client, path, 5, position(IF_EXPRESSIONS, 5, "value"))
        check("if-expression-hover-narrowed", "value: Int?" in narrowed and "Here: `Int`" in narrowed, narrowed)
        condition = hover_text(client, path, 2, position(IF_EXPRESSIONS, 2, "value"))
        check("if-expression-hover-condition", "value: Int?" in condition and "Here:" not in condition, condition)
        # An elif sees only its own condition: not the earlier one false, but the left side of its 'and'.
        elif_condition = hover_text(client, path, 4, position(IF_EXPRESSIONS, 4, "value"))
        check("if-expression-hover-elif-condition", "Here:" not in elif_condition, elif_condition)
        right_side = hover_text(client, path, 4, position(IF_EXPRESSIONS, 4, "value", 1))
        check("if-expression-hover-elif-and", "Here: `Int`" in right_side, right_side)
        references = client.at("textDocument/references", path, 1, position(IF_EXPRESSIONS, 1, "doubled"),
                               context={"includeDeclaration": True})["result"]
        places = sorted((r["range"]["start"]["line"], r["range"]["start"]["character"]) for r in references)
        check("if-expression-references", places == [(1, 8), (4, 35), (5, 26), (7, 19)], places)
        renamed = client.at("textDocument/rename", path, 0, position(IF_EXPRESSIONS, 0, "value"),
                            newName="amount")["result"]["changes"][uri(path)]
        places = sorted((e["range"]["start"]["line"], e["range"]["start"]["character"]) for e in renamed)
        check("if-expression-rename", places == [(0, 14), (2, 14), (4, 9), (4, 27), (5, 18)]
              and all(e["newText"] == "amount" for e in renamed), renamed)
        capture = client.at("textDocument/rename", path, 1, position(IF_EXPRESSIONS, 1, "doubled"), newName="value")
        check("if-expression-rename-refuses-capture", capture.get("error", {}).get("code") == -32803, capture)
        symbols = client.request("textDocument/documentSymbol", {"textDocument": {"uri": uri(path)}})["result"]
        check("if-expression-symbols", [s["name"] for s in symbols] == ["describe", "total", "label"], symbols)
        formatting = {"textDocument": {"uri": uri(path)}, "options": {"tabSize": 4, "insertSpaces": True}}
        check("if-expression-formatting-canonical", client.request("textDocument/formatting", formatting)["result"] == [])
        # A missing else shows in the editor as `sprig check` reports it.
        client.change(path, IF_EXPRESSIONS.replace('else:\n    "few"\n', ''), 2)
        errors = client.wait_diagnostics(path, lambda d: d != [])
        check("if-expression-missing-else", len(errors) == 1 and errors[0]["code"] == "SPR-SYNTAX-ERROR"
              and "needs an else branch" in errors[0]["message"]
              and errors[0]["range"]["start"] == {"line": 10, "character": 12}, errors)
        check("if-expression-shutdown", client.stop() == 0, "".join(client.stderr))
    finally:
        if client.proc.poll() is None:
            client.proc.kill()


def check_line_endings(directory):
    """Editors on Windows keep CRLF documents and send VS Code's URI spelling."""
    client = Client(directory)
    try:
        initialize(client, directory)
        source = "func twice(x: Int) -> Int:\n    return x * 2\n\nprint(twice(21))\n"
        crlf = source.replace("\n", "\r\n")
        path = directory / "crlf.spr"
        client.open(path, crlf)
        check("crlf-clean", client.wait_diagnostics(path) == [])
        format_params = {"textDocument": {"uri": uri(path)}, "options": {"tabSize": 4, "insertSpaces": True}}
        edits = client.request("textDocument/formatting", format_params)["result"]
        check("crlf-formatting-canonical", edits == [], edits)
        client.change(path, crlf.replace("x * 2", "x  *  2"), 2)
        edits = client.request("textDocument/formatting", format_params)["result"]
        check("crlf-formatting-keeps-line-endings", len(edits) == 1 and edits[0]["newText"] == crlf, edits)
        client.change(path, 'let a = 1\r\nlet b: Int = "x"\r\n', 3)
        diagnostics = client.wait_diagnostics(path, lambda d: d != [])
        check("crlf-diagnostic-position", diagnostics[0]["range"]["start"] == {"line": 1, "character": 13},
              diagnostics)
        # A quick fix that inserts a line keeps the document's CRLF line endings.
        unimported = 'let text = files.read_utf8("x.txt")\r\nprint(text.length())\r\n'
        client.change(path, unimported, 4)
        unresolved = [d for d in client.diagnostics_at(path, 4) if d["code"] == "SPR-NAME-UNRESOLVED"]
        actions = code_actions(client, path, unresolved[0]["range"], unresolved) if unresolved else []
        edits = actions[0]["edit"]["changes"][uri(path)] if len(actions) == 1 else []
        check("crlf-quick-fix", len(edits) == 1 and edits[0]["newText"] == 'import "@std/files.spr" as files\r\n'
              and edits[0]["range"]["start"] == {"line": 0, "character": 0}, actions)
        client.change(path, apply_edits(unimported, edits), 5)
        remaining = client.diagnostics_at(path, 5)
        check("crlf-quick-fix-applied", all(d["code"] != "SPR-NAME-UNRESOLVED" for d in remaining), remaining)
        if os.name == "nt":
            # VS Code writes a lowercase drive and an escaped colon; diagnostics
            # must come back under that exact URI.
            other = directory / "vscode uri.spr"
            plain = uri(other)
            spelled = "file:///" + plain[8].lower() + "%3A" + plain[10:]
            client.notify("textDocument/didOpen", {"textDocument": {
                "uri": spelled, "languageId": "sprig", "version": 1, "text": 'let n: Int = "x"\r\n'}})
            deadline = time.time() + TIMEOUT
            published = None
            while published is None and time.time() < deadline:
                published = next((m["params"] for m in client.notifications
                                  if m.get("method") == "textDocument/publishDiagnostics"
                                  and m["params"]["uri"] == spelled and m["params"]["diagnostics"]), None)
                if published is None:
                    client._next(deadline)
            check("vscode-windows-uri", published is not None
                  and published["diagnostics"][0]["code"] == "SPR-TYPE-ASSIGN", published)
        check("crlf-shutdown", client.stop() == 0, "".join(client.stderr))
    finally:
        stop_quietly(client)


def check_quick_fixes(directory):
    """Code actions carry the compiler's suggested edits, and applying one removes its diagnostic."""
    client = Client(directory)
    plain = None
    try:
        capabilities = initialize(client, directory)["capabilities"]
        check("quick-fix-capability", capabilities.get("codeActionProvider") == {"codeActionKinds": ["quickfix"]},
              capabilities)
        fixes = {}
        for name, source, code, clean in QUICK_FIXES:
            path = directory / (name + ".spr")
            path.write_text(source, encoding="utf-8")
            cli = subprocess.run([str(SPRIG), "check", str(path), "--json"], capture_output=True, text=True,
                                 encoding="utf-8", cwd=directory)
            reported = [d for d in json.loads(cli.stdout)["diagnostics"] if d["code"] == code]
            client.open(path, source)
            published = [d for d in client.diagnostics_at(path, 1) if d["code"] == code]
            check("quick-fix-diagnostic-" + name, len(reported) == 1 and len(reported[0]["suggestedEdits"]) == 1
                  and len(published) == 1, (reported, published))
            edit = reported[0]["suggestedEdits"][0]
            # The action is the CLI's edit in LSP positions, for the diagnostic as it was published.
            actions = code_actions(client, path, published[0]["range"], published)
            expected = {"title": edit["description"], "kind": "quickfix", "diagnostics": published,
                        "edit": {"changes": {uri(path): [{"range": utf16_range(source, edit["range"]),
                                                          "newText": edit["newText"]}]}},
                        "isPreferred": True}
            check("quick-fix-" + name, actions == [expected], actions)
            fixes[name] = actions[0]
            client.change(path, apply_edits(source, actions[0]["edit"]["changes"][uri(path)]), 2)
            after = client.diagnostics_at(path, 2)
            check("quick-fix-applied-" + name, after == [] if clean else all(d["code"] != code for d in after), after)
            client.notify("textDocument/didClose", {"textDocument": {"uri": uri(path)}})

        # The constructor arguments come after two emoji: UTF-16 characters, and the written text.
        name = "named-constructor-after-emoji"
        emoji = next(text for case, text, _, _ in QUICK_FIXES if case == name)
        edit = fixes[name]["edit"]["changes"][uri(directory / (name + ".spr"))]
        arguments = {"start": {"line": 3, "character": position(emoji, 3, "1")},
                     "end": {"line": 3, "character": position(emoji, 3, "2") + 1}}
        check("quick-fix-utf16-range", edit == [{"range": arguments, "newText": "x=1, y=2"}], edit)

        # The server answers from its own check: the client's diagnostics neither add nor remove fixes.
        source = next(text for case, text, _, _ in QUICK_FIXES if case == "elif")
        path = directory / "ranges.spr"
        client.open(path, source)
        diagnostic = client.diagnostics_at(path, 1)[0]
        cursor = diagnostic["range"]["start"]
        at_cursor = code_actions(client, path, {"start": cursor, "end": cursor}, [])
        check("quick-fix-at-cursor", [a["title"] for a in at_cursor] == [fixes["elif"]["title"]], at_cursor)
        first_line = {"start": {"line": 0, "character": 0}, "end": {"line": 0, "character": 9}}
        forged = dict(diagnostic, range=first_line, message="forged",
                      data={"suggestedEdits": [{"range": first_line, "newText": "oops", "description": "forged"}]})
        check("quick-fix-none-without-diagnostic", code_actions(client, path, first_line, [forged]) == [])
        check("quick-fix-only-other-kinds",
              code_actions(client, path, diagnostic["range"], [diagnostic], only=["refactor", "source"]) == [])
        check("quick-fix-only-quickfix",
              len(code_actions(client, path, diagnostic["range"], [diagnostic], only=["quickfix"])) == 1)

        # An imported file's error shows on the import, but its fix would edit that file: none here.
        (directory / "broken.spr").write_text(source, encoding="utf-8")
        importer = directory / "importer.spr"
        client.open(importer, 'import "./broken.spr" as broken\n\nprint(1)\n')
        shown = client.diagnostics_at(importer, 1)
        check("quick-fix-not-for-imported-file", len(shown) == 1 and shown[0]["code"] == "SPR-SYNTAX-ERROR"
              and code_actions(client, importer, shown[0]["range"], shown) == [], shown)
        check("quick-fix-shutdown", client.stop() == 0, "".join(client.stderr))

        # A fix is an edit, which a client without CodeAction literals cannot take.
        plain = Client(directory)
        capabilities = initialize(plain, directory, capabilities={})["capabilities"]
        check("quick-fix-needs-literal-support", "codeActionProvider" not in capabilities, capabilities)
        plain.open(path, source)
        plain.diagnostics_at(path, 1)
        check("quick-fix-none-without-literal-support",
              code_actions(plain, path, diagnostic["range"], [diagnostic]) == [])
        check("quick-fix-plain-shutdown", plain.stop() == 0, "".join(plain.stderr))
    finally:
        for started in (client, plain):
            if started is not None:
                stop_quietly(started)


INFERRED = '''import "@std/lists.spr" as lists

generic T:
    func identity(value: T) -> T:
        return value

generic T:
    class Box:
        let value: T

generic T:
    variant Option:
        Some:
            value: T
        None:

let a = identity(42)
let b = Box(value="x")
let c = Option.Some(value=1.5)
let d = identity[Int](7)
let e = lists.first(["a"])
let f = lists.Pair(first=1, second="x")
'''


def check_inferred_hover(directory):
    """Hover on a generic call written without type arguments shows the ones inferred."""
    client = Client(directory)
    try:
        initialize(client, directory)
        path = directory / "inferred.spr"
        client.open(path, INFERRED)
        check("inferred-clean", client.wait_diagnostics(path) == [])
        lines = INFERRED.splitlines()
        cases = [("let a = identity(42)", "identity", "Here: `identity[Int](...) -> Int`"),
                 ("let b = Box(value=\"x\")", "Box", "Here: `Box[String](...)`"),
                 ("let c = Option.Some(value=1.5)", "Some", "Here: `Option[Float].Some(...)`"),
                 # Spelled as this module writes it: through the import alias.
                 ("let e = lists.first([\"a\"])", "first", "Here: `lists.first[String](...) -> String?`"),
                 ("let f = lists.Pair(first=1, second=\"x\")", "Pair", "Here: `lists.Pair[Int, String](...)`")]
        for text, needle, expected in cases:
            line = lines.index(text)
            hover = hover_text(client, path, line, position(INFERRED, line, needle))
            check("hover-inferred-" + needle, expected in hover, hover)
        line = lines.index("let d = identity[Int](7)")
        written = hover_text(client, path, line, position(INFERRED, line, "identity"))
        check("hover-written-type-arguments", "Here:" not in written and "func identity" in written, written)
        check("inferred-shutdown", client.stop() == 0, "".join(client.stderr))
    finally:
        if client.proc.poll() is None:
            if os.name == "nt":
                subprocess.run(["taskkill", "/PID", str(client.proc.pid), "/T", "/F"], capture_output=True)
            client.proc.kill()


def check_project(directory):
    project = directory / "lspdemo"
    project.mkdir()
    created = subprocess.run([str(SPRIG), "init"], cwd=project, capture_output=True, text=True)
    check("project-init", created.returncode == 0, created.stdout + created.stderr)
    (project / "src/money.spr").write_text('func format_cents(cents: Int) -> String:\n'
                                           '    return cents.toString() + " cents"\n', encoding="utf-8")
    (project / "src/main.spr").write_text('import "./money.spr" as money\n\n'
                                          'print(money.format_cents(250))\n', encoding="utf-8")
    (project / "tests").mkdir()
    (project / "tests/money_test.spr").write_text('import "../src/money.spr" as money\n\n'
                                                  'assert(money.format_cents(1) == "1 cents")\n', encoding="utf-8")
    money = project / "src/money.spr"
    client = Client(project)
    initialize(client, project)
    client.open(money, money.read_text(encoding="utf-8"))
    missing = client.wait_diagnostics(money)
    check("project-lock-missing", len(missing) == 1 and missing[0]["code"] == "SPR-PROJECT-LOCK-MISSING"
          and "sprig resolve" in missing[0]["message"], missing)
    resolved = subprocess.run([str(SPRIG), "resolve", "--offline"], cwd=project, capture_output=True, text=True)
    check("project-resolve", resolved.returncode == 0, resolved.stdout + resolved.stderr)
    client.notify("textDocument/didSave", {"textDocument": {"uri": uri(money)}})
    check("project-checked-with-lock", client.wait_diagnostics(money, lambda d: d == []) == [])
    references = client.at("textDocument/references", money, 0, 7, context={"includeDeclaration": False})["result"]
    places = sorted("/".join(Path(r["uri"]).parts[-2:]) for r in references)
    check("project-references-in-unopened-files", places == ["src/main.spr", "tests/money_test.spr"], places)
    check("project-shutdown", client.stop() == 0, "".join(client.stderr))


def main():
    if not SPRIG.exists():
        print("Build first: python3 scripts/build.py", file=sys.stderr)
        sys.exit(2)
    with tempfile.TemporaryDirectory(prefix="sprig-lsp-") as temp:
        workspace = Path(temp).resolve() / "workspace"
        workspace.mkdir()
        check_workspace(workspace)
        conditionals = Path(temp).resolve() / "if expressions"
        conditionals.mkdir()
        check_if_expressions(conditionals)
        line_endings = Path(temp).resolve() / "line endings"
        line_endings.mkdir()
        check_line_endings(line_endings)
        quick_fixes = Path(temp).resolve() / "quick fixes"
        quick_fixes.mkdir()
        check_quick_fixes(quick_fixes)
        inferred = Path(temp).resolve() / "inferred"
        inferred.mkdir()
        check_inferred_hover(inferred)
        check_project(Path(temp).resolve())
    print(f"language server: {COUNT} passed, 0 failed")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print("FAIL language server:", exc, file=sys.stderr)
        raise
