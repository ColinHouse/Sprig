"""If expression tests assert front-end errors, typing and independent runtime effects."""
import json
import os
import pathlib
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[2]


def invoke(*args):
    return subprocess.run([str(ROOT / 'bin' / ('sprig.cmd' if os.name == 'nt' else 'sprig')), *map(str, args)],
                          capture_output=True, text=True, timeout=60)


HEADER = '''enum Flag:
    On
    Off
'''

# Programs written for the if statement's narrowing, which an elif that saw the
# earlier conditions false would reject: they compile and print as before.
COMPATIBLE_NARROWING = [
    ('check-repeated-in-elif', '''func label(count: Int?, verbose: Bool) -> String:
    if count == null:
        return "none"
    elif verbose:
        if count != null:
            return "count = " + count
        return "?"
    return "quiet"
print(label(3, true))
''', 'count = 3\n'),
    ('var-in-else-after-elif', '''class Node:
    let value: Int
    let next: Node?
func total(head: Node?, skip: Bool) -> Int:
    if head == null:
        return 0
    elif skip:
        return -1
    else:
        var node: Node? = head
        var sum = 0
        while node != null:
            let current = node
            if current != null:
                sum += current.value
                node = current.next
        return sum
print(total(Node(value=1, next=Node(value=2, next=null)), false))
''', '3\n'),
    ('lambda-in-elif', '''func later(make: fn() -> String?) -> Int:
    return 1
func f(name: String?, loud: Bool) -> Int:
    if name == null:
        return 0
    elif loud:
        let again: fn() -> String? = fn() => name
        return later(again)
    return 2
print(f("x", true))
''', '1\n'),
    ('elif-checks-again', '''func f(x: Int?) -> String:
    if x == null:
        return "none"
    elif x != null and x > 5:
        return "big"
    return "small"
print(f(7))
''', 'big\n'),
    ('expression-elif-checks-again', '''func f(x: Int?) -> String:
    return if x == null:
        "none"
    elif x != null and x > 5:
        "big"
    else:
        "small"
print(f(7) + " " + f(2) + " " + f(null))
''', 'big small none\n'),
    ('expression-check-repeated-in-elif', '''func label(count: Int?, verbose: Bool) -> String:
    return if count == null:
        "none"
    elif verbose:
        if count != null:
            "count = " + count
        else:
            "?"
    else:
        "quiet"
print(label(3, true))
''', 'count = 3\n'),
    ('expression-else-without-elif', '''func f(x: Int?) -> Int:
    return if x == null:
        0
    else:
        x + 1
print(f(4))
''', '5\n'),
]

# The same branch in both forms, with the codes both report (none when both compile).
SAME_NARROWING = [
    ('elif-sees-earlier-condition-false',
     'func f(x: Int?) -> String:\n    if x == null:\n        return "none"\n    elif x > 5:\n        return "big"\n'
     '    return "small"\nprint(f(7))\n',
     'func f(x: Int?) -> String:\n    return if x == null:\n        "none"\n    elif x > 5:\n        "big"\n'
     '    else:\n        "small"\nprint(f(7))\n',
     []),
    ('else-after-elif',
     'func f(x: Int?, v: Bool) -> String:\n    if x == null:\n        return "none"\n    elif v:\n        return "v"\n'
     '    else:\n        return "q " + x\nprint(f(3, true))\n',
     'func f(x: Int?, v: Bool) -> String:\n    return if x == null:\n        "none"\n    elif v:\n        "v"\n'
     '    else:\n        "q " + x\nprint(f(3, true))\n',
     []),
    ('var-never-narrows',
     'var x: Int? = 3\nif x == null:\n    print("none")\nelif true:\n    print("v " + x)\n',
     'var x: Int? = 3\nlet s = if x == null:\n    "none"\nelif true:\n    "v " + x\nelse:\n    "q"\nprint(s)\n',
     ['SPR-TYPE-NULLABLE']),
    ('non-null-compared-with-null',
     'func f(n: Int) -> String:\n    if n == null:\n        return "none"\n    return "n"\nprint(f(1))\n',
     'func f(n: Int) -> String:\n    return if n == null:\n        "none"\n    else:\n        "n"\nprint(f(1))\n',
     ['SPR-TYPE-MISMATCH']),
]


def diagnostics(path):
    result = invoke('check', path, '--json')
    return result, json.loads(result.stdout)['diagnostics']


def main():
    with tempfile.TemporaryDirectory(prefix='sprig-if-expr-') as d:
        p = pathlib.Path(d) / 'main.spr'
        # Conditions run once each, in source order, until one holds; only the
        # chosen branch runs, after its condition.
        positive = HEADER + '''var log = ""
func test(name: String, result: Bool) -> Bool:
    log += name
    return result
func value(name: String, result: Int) -> Int:
    log += name
    return result
let first = if test("a", true):
    value("A", 1)
elif test("b", true):
    value("B", 2)
else:
    value("E", 3)
print(first)
print(log)
log = ""
let last = if test("a", false):
    value("A", 1)
elif test("b", false):
    value("B", 2)
else:
    value("E", 3)
print(last)
print(log)
func pick(flag: Flag, n: Int) -> Int:
    return match flag:
        case Flag.On:
            if n > 0:
                n
            else:
                -n
        case Flag.Off:
            0
let picked = if pick(Flag.On, -4) == 4:
    match Flag.Off:
        case Flag.On:
            1
        case Flag.Off:
            2
else:
    3
print(picked)
let small: Int32 = if log == "":
    1
else:
    2
print(small)
let maybe: Int32? = null
let kept = if small == 2:
    maybe
else:
    5
print(kept)
'''
        p.write_text(positive)
        result = invoke('run', p)
        assert result.returncode == 0 and result.stdout == '1\naA\n3\nabE\n2\n2\nnull\n', result.stdout + result.stderr
        effects = HEADER + '''func failing(code: Int) -> Int throws Error:
    throw Error("failed " + code)
func effect(code: Int) -> Int throws Error:
    return if code > 0:
        failing(code)
    else:
        0
print(effect(0))
try:
    print(effect(7))
catch problem: Error:
    print(problem.message)
generic T:
    func either(first: Bool, a: T, b: T) -> T:
        return if first:
            a
        else:
            b
print(either[String](false, "a", "b") + either[Int](true, 1, 2))
func describe(value: Int?) -> String:
    return if value == null:
        "none"
    else:
        if value < 0:
            "negative " + (0 - value)
        else:
            "value " + value
print(describe(null) + ", " + describe(-2) + ", " + describe(3))
let step = fn(n: Int) => if n % 2 == 0:
    n.divTrunc(2)
else:
    3 * n + 1
print(step(6) + step(3))
'''
        p.write_text(effects)
        result = invoke('run', p)
        assert result.returncode == 0 and result.stdout == '0\nfailed 7\nb1\nnone, negative 2, value 3\n13\n', \
            result.stdout + result.stderr
        assert invoke('fmt', p).returncode == 0
        formatted = invoke('run', p)
        assert (formatted.returncode, formatted.stdout, formatted.stderr) == (result.returncode, result.stdout, result.stderr)
        p.write_text(effects.replace('func effect(code: Int) -> Int throws Error:', 'func effect(code: Int) -> Int:'))
        result = invoke('check', p, '--json')
        assert result.returncode != 0 and 'SPR-FLOW-THROWS' in result.stdout, result.stdout + result.stderr

        negatives = [
            ('SPR-TYPE-CONDITION', 'let n = 1\nlet x = if n:\n    1\nelse:\n    2\n'),
            ('SPR-TYPE-CONDITION', 'let b: Bool? = true\nlet x = if b:\n    1\nelse:\n    2\n'),
            ('SPR-TYPE-CONDITION', 'let x = if true:\n    1\nelif "yes":\n    2\nelse:\n    3\n'),
            ('SPR-TYPE-MISMATCH', 'let x = if true:\n    1\nelse:\n    "two"\n'),
            ('SPR-TYPE-MISMATCH', 'let x: String = if true:\n    1\nelse:\n    "two"\n'),
            ('SPR-NUM-CONVERSION', 'let x = if true:\n    1\nelse:\n    2.5\n'),
            ('SPR-TYPE-NULL', 'let x: String = if true:\n    "a"\nelse:\n    null\n'),
            ('SPR-TYPE-NULLABLE', 'let n: Int? = null\nlet x: Int = if true:\n    n\nelse:\n    0\n'),
            ('SPR-TYPE-UNIT', 'let x = if true:\n    print(1)\nelse:\n    print(2)\n'),
            ('SPR-TYPE-UNIT', 'let x = if true:\n    print(1)\nelse:\n    2\n'),
            ('SPR-TYPE-INFER', 'let x = if true:\n    null\nelse:\n    null\n'),
            ('SPR-TYPE-RETURN', 'func f() -> Unit:\n    return if true:\n        1\n    else:\n        2\n'),
            ('SPR-NUM-MIXED', 'var v: Int? = 3\nlet x = if v != null:\n    v + 1\nelse:\n    0\n'),
            ('SPR-TYPE-CAPTURE', 'func f(c: Bool) -> Int:\n    var n = 1\n    let g = fn() => if c:\n        n\n    else:\n        0\n    return g()\n'),
            ('SPR-NAME-FORWARD-REFERENCE', 'let x = if true:\n    later\nelse:\n    0\nlet later = 1\n'),
            ('SPR-NAME-UNRESOLVED', 'let x = if true:\n    missing_name\nelse:\n    0\n'),
            ('SPR-FLOW-UNREACHABLE', 'func f(c: Bool) -> Int:\n    return if c:\n        1\n    else:\n        2\n    print(3)\n'),
        ]
        for code, body in negatives:
            p.write_text(HEADER + body)
            result = invoke('check', p, '--json')
            assert result.returncode != 0 and code in result.stdout, (body, result.stdout, result.stderr)
            assert 'SPR-JVM-INTERNAL' not in result.stdout

        # A String's += joins its operand, and an if or match expression there is typed
        # on its own, as `s += 2` is; a numeric += keeps the target as the context.
        p.write_text(HEADER + '''class Label:
    var text: String = "c="
let c = "x".length() > 0
var s = "n="
s += 1
s += if c:
    2
else:
    3
s += match Flag.Off:
    case Flag.On:
        0.5
    case Flag.Off:
        4.5
print(s)
let label = Label()
label.text += if c:
    true
else:
    false
print(label.text)
var total: Float = 1.0
total += if c:
    2
else:
    3
print(total)
''')
        result = invoke('run', p)
        assert (result.returncode, result.stdout) == (0, 'n=124.5\nc=true\n3.0\n'), result.stdout + result.stderr
        p.write_text('let c = true\nlet missing: Int? = null\nvar s = "n="\ns += if c:\n    missing\nelse:\n    1\n')
        result, found = diagnostics(p)
        assert [d['code'] for d in found] == ['SPR-TYPE-NULLABLE'], found

        # Two cases of one variant: the first branch made the result that case, and
        # the hint names the variant to write on the target.
        p.write_text('variant Shape:\n    Circle(radius: Float)\n    Square(side: Float)\nlet c = true\n'
                     'let shape = if c:\n    Shape.Circle(radius=1.0)\nelse:\n    Shape.Square(side=2.0)\n')
        result, found = diagnostics(p)
        assert [d['code'] for d in found] == ['SPR-TYPE-MISMATCH'] \
            and "'let value: Shape = if ...'" in found[0].get('hint', ''), found
        p.write_text('variant Shape:\n    Circle(radius: Float)\n    Square(side: Float)\nlet c = true\n'
                     'let shape: Shape = if c:\n    Shape.Circle(radius=1.0)\nelse:\n    Shape.Square(side=2.0)\nprint(shape)\n')
        result = invoke('run', p)
        assert result.returncode == 0 and result.stdout == 'Circle(radius=1.0)\n', result.stdout + result.stderr

        # Narrowing is the if statement's rule, in both forms (#125): a branch sees
        # its own condition true and every earlier condition false, the else sees all
        # of them false, and a name declared nullable may still be compared with null
        # while narrowed. Code written before elif chains narrowed keeps compiling: a
        # check repeated in an elif and `elif x != null and ...`; a var or a lambda
        # that takes a narrowed name declares its type, as in a then branch.
        for name, body, expected in COMPATIBLE_NARROWING:
            p.write_text(body)
            result = invoke('run', p)
            assert (result.returncode, result.stdout) == (0, expected), (name, result.stdout + result.stderr)
        # Converting between the two forms never changes what type-checks.
        for name, statement, expression, codes in SAME_NARROWING:
            reports = []
            for body in (statement, expression):
                p.write_text(body)
                result, found = diagnostics(p)
                reports.append([d['code'] for d in found])
            assert reports[0] == reports[1] == codes, (name, reports)

        # The hint for two cases of one variant spells the variant as this module
        # writes it: through the import alias when it comes from another module.
        shapes = pathlib.Path(d) / 'shapes.spr'
        shapes.write_text('variant Shape:\n    Circle(radius: Float)\n    Square(side: Float)\n')
        p.write_text('import "./shapes.spr" as shapes\nlet c = true\nlet shape = if c:\n'
                     '    shapes.Shape.Circle(radius=1.0)\nelse:\n    shapes.Shape.Square(side=2.0)\n')
        result, found = diagnostics(p)
        assert [d['code'] for d in found] == ['SPR-TYPE-MISMATCH'] \
            and "'let value: shapes.Shape = if ...'" in found[0].get('hint', ''), found

        # A missing else is reported once, and the rest of the program is still
        # resolved and checked in the same round: the editor keeps its features, and an
        # error after the if expression is not held back until the else is written.
        p.write_text('let n = 1\nlet x = if n > 0:\n    1\nlet y: String = x\nprint(missing)\n')
        result, found = diagnostics(p)
        assert [(d['code'], d['range']['start']['line']) for d in found] \
            == [('SPR-SYNTAX-ERROR', 1), ('SPR-NAME-UNRESOLVED', 4)], found

        # A long elif chain compiles: javac parses a nested conditional recursively
        # and overflowed at about 1,500 levels, so long chains use a flat form.
        lines = ['let n = 1499', 'let label = if n == 0:', '    "zero"']
        for i in range(1, 1500):
            lines += [f'elif n == {i}:', f'    "v{i}"']
        lines += ['else:', '    "other"', 'print(label)']
        p.write_text('\n'.join(lines) + '\n')
        result = invoke('run', p)
        assert (result.returncode, result.stdout) == (0, 'v1499\n'), result.stdout[:300] + result.stderr[:300]

        # Each malformed shape is one targeted syntax error at the place to fix.
        syntax = [
            ('missing-else', 'let n = 1\nlet x = if n > 0:\n    1\nprint(x)\n', 'needs an else branch', (1, 8)),
            ('missing-else-after-elif', 'let n = 1\nlet x = if n > 0:\n    1\nelif n < 0:\n    2\nprint(x)\n',
             'needs an else branch', (1, 8)),
            ('nested-missing-else', 'let n = 1\nlet x = if n > 0:\n    if n > 5:\n        1\nelse:\n    2\n',
             'needs an else branch', (2, 4)),
            ('one-line', 'let n = 1\nlet x = if n > 0: 1 else: 2\n', 'its own indented line', (1, 18)),
            ('one-line-else', 'let n = 1\nlet x = if n > 0:\n    1\nelse: 2\n', 'its own indented line', (3, 6)),
            ('block-branch', 'let n = 1\nlet x = if n > 0:\n    let y = 1\n    y\nelse:\n    2\n', 'exactly one expression', (2, 4)),
            ('two-lines', 'let n = 1\nlet x = if n > 0:\n    1\n    2\nelse:\n    3\n', 'exactly one expression', (3, 4)),
            ('in-call', 'let n = 1\nprint(if n > 0:\n    1\nelse:\n    2)\n', 'inside parentheses', (1, 6)),
            ('in-list', 'let n = 1\nlet xs = [if n > 0:\n    1\nelse:\n    2]\n', 'inside parentheses', (1, 10)),
            ('lambda-in-call', 'let xs = [1].map(fn(n: Int) => if n > 0:\n    n\nelse:\n    0)\n', 'inside parentheses', (0, 31)),
            ('operand', 'let n = 1\nlet x = 1 + if n > 0:\n    1\nelse:\n    2\n', "operand of '+'", (1, 12)),
            ('not-operand', 'let n = 1\nlet x = not if n > 0:\n    true\nelse:\n    false\n', "operand of 'not'", (1, 12)),
        ]
        for name, body, text, (line, character) in syntax:
            p.write_text(body)
            result, found = diagnostics(p)
            assert result.returncode == 1 and len(found) == 1, (name, found)
            first = found[0]
            assert first['code'] == 'SPR-SYNTAX-ERROR' and text in first['message'] and first.get('hint'), (name, first)
            start = first['range']['start']
            assert (start['line'], start['character']) == (line, character), (name, start)
        # An unrelated error after a malformed if expression is still reported.
        p.write_text('let n = 1\nlet xs = [if n > 0:\n    1\nelse:\n    2]\nlet y = = 3\n')
        result, found = diagnostics(p)
        assert [d['range']['start']['line'] for d in found] == [1, 5], found
        # 'else if' inside an if expression keeps its mechanical rewrite to 'elif'.
        p.write_text('let n = 1\nlet x = if n > 0:\n    1\nelse if n < 0:\n    2\nelse:\n    3\n')
        result, found = diagnostics(p)
        assert found and found[0]['suggestedEdits'] and found[0]['suggestedEdits'][0]['newText'] == 'elif', found
        # At the start of a statement one token decides between the if or match statement and
        # an expression statement, so a mistake in a later branch is one error where it is,
        # not also an error on the statement's first line.
        statements = [
            ('else-if', 'let x = 3\nif x > 5:\n    print(1)\nelse if x > 1:\n    print(2)\n',
             "Sprig spells else-if as 'elif'", (3, 5)),
            ('else-if-in-function', 'func f(x: Int) -> Int:\n    if x > 5:\n        return 1\n'
             '    else if x > 1:\n        return 2\n    return 3\nprint(f(2))\n', "Sprig spells else-if as 'elif'", (3, 9)),
            ('match-case-colon', HEADER + 'let f = Flag.On\nmatch f:\n    case Flag.On:\n        print(1)\n'
             '    case Flag.Off\n        print(2)\n', "':'", (7, 17)),
        ]
        for name, body, text, (line, character) in statements:
            p.write_text(body)
            result, found = diagnostics(p)
            assert result.returncode == 1 and len(found) == 1, (name, found)
            assert found[0]['code'] == 'SPR-SYNTAX-ERROR' and text in found[0]['message'], (name, found)
            start = found[0]['range']['start']
            assert (start['line'], start['character']) == (line, character), (name, start)
        # Recovery stays in step after a one-line if expression: the statements after it
        # and the next function or method are not reported as well. An assignment in a
        # branch is a branch with a statement, and no grammar predicate's text reaches
        # the user, as the first error or as a follow-on one.
        single = [
            ('one-line-in-function', 'func g(x: Int) -> Unit:\n    let y = if x > 1: 1 else: 2\n    print(y)\n'
             'func h() -> Int:\n    return 1\n', 'its own indented line', (1, 22)),
            ('one-line-else-in-function', 'func g(x: Int) -> Unit:\n    let y = if x > 1:\n        1\n    else: 2\n'
             '    print(y)\nfunc h() -> Int:\n    return 1\n', 'its own indented line', (3, 10)),
            ('one-line-in-method', 'class C:\n    func g(x: Int) -> Unit:\n        let y = if x > 1: 1 else: 2\n'
             '        print(y)\n    func h() -> Int:\n        return 1\n', 'its own indented line', (2, 26)),
            ('missing-else-at-end', 'func g(x: Int) -> Int:\n    return if x > 1:\n        1\n',
             'needs an else branch', (1, 11)),
            ('unindented-branches', 'func g(n: Int) -> Int:\n    return if n > 0:\n    1\n    else:\n    2\n'
             'func h() -> Int:\n    return 1\n', 'its own indented line', (2, 4)),
            ('assignment-in-branch', 'var y = 0\nlet c = true\nlet x = if c:\n    y = 1\nelse:\n    2\n',
             'exactly one expression', (3, 4)),
            ('compound-assignment-in-branch', 'var y = 0\nlet c = true\nlet x = if c:\n    2\nelse:\n    y += 1\n',
             'exactly one expression', (5, 4)),
            ('question-after-expression', 'let n = 3\nlet x = n?\n', "Expected the end of the line, found '?'",
             (1, 9)),
            ('assignment-in-match-branch', HEADER + 'let f = Flag.On\nvar y = 0\nlet x = match f:\n    case Flag.On:\n'
             '        y = 1\n    case Flag.Off:\n        2\n', "Expected the end of the line, found '='", (7, 10)),
            ('first-match-case-colon', HEADER + 'let f = Flag.On\nmatch f:\n    case Flag.On\n        print(1)\n'
             '    case Flag.Off:\n        print(2)\n', "':'", (5, 16)),
            ('conform-to', 'class A:\n    pass\nconform A ot Runnable\n', "Expected 'to'", (2, 10)),
        ]
        for name, body, text, (line, character) in single:
            p.write_text(body)
            result, found = diagnostics(p)
            assert result.returncode == 1 and len(found) == 1, (name, found)
            assert text in found[0]['message'] and 'predicate' not in found[0]['message'], (name, found)
            start = found[0]['range']['start']
            assert (start['line'], start['character']) == (line, character), (name, start)
        # The end of the file is left out only when the broken if expression reaches it.
        p.write_text('let n = 1\nlet x = if n > 0: 1 else: 2\nfunc g() -> Int:\n')
        result, found = diagnostics(p)
        # The missing body is reported in source terms (#142), not as ANTLR's INDENT token.
        assert [(d['range']['start']['line'], "Expected an indented block after 'func ...:'" in d['message'])
                for d in found] == [(1, False), (3, True)], found

        # Help, capabilities and docs state the narrowing that is implemented.
        claims = [invoke('help', topic, '--json').stdout for topic in ('nullability', 'match', 'language')]
        claims.append(invoke('capabilities', '--json').stdout)
        for name in ('docs/language/if-expressions.md', 'docs/language/quick-reference.md',
                     'docs/language/feature-status.md', 'docs/tooling/agent-guide.md',
                     'website/en/guide/language-tour.md', 'website/guide/language-tour.md'):
            claims.append((ROOT / name).read_text(encoding='utf-8'))
        # #125: each elif and the else see every earlier condition false. The pre-#125 wording
        # ("never the earlier ones false", "only when there is no elif") must be gone everywhere,
        # and the capability inventory and the quick reference must state the implemented rule.
        for text in claims:
            for stale in ('never the earlier ones false', 'earlier ones false', 'only when there is no elif',
                          'elif or else after if x == null', 'start of a line', '行首'):
                assert stale not in text, stale
        flat = [' '.join(text.split()) for text in claims]
        assert 'every earlier condition false' in flat[3], 'capabilities must state the elif narrowing rule'
        assert 'every earlier condition false' in flat[5], 'quick-reference must state the elif narrowing rule'
        assert "`if`/`elif`/`else` is a value expression" in (ROOT / 'docs/tooling/agent-guide.md').read_text()
        # With type argument inference: an if expression's value passed on through a let, a lambda
        # whose body is an if expression, and inferred calls inside the branches.
        p.write_text('import "@std/lists.spr" as lists\ngeneric T:\n    func both(a: T, b: T) -> T:\n'
                     '        return b\nlet small: Int32 = 3\nlet c = true\nlet chosen: Int32 = if c:\n    1\n'
                     'else:\n    2\nprint(both(small, chosen))\nlet names: List[String] = ["Ada", "Grace", "Al"]\n'
                     'let classify = fn(n: String) => if n.length() > 2:\n    "long"\nelse:\n    "short"\n'
                     'print(lists.group_by(names, classify))\nlet label = if c:\n    both("x", "y")\nelse:\n'
                     '    both("z", "w")\nprint(label)\nlet first = if c:\n    lists.first(names)\nelse:\n'
                     '    null\nprint(first)\n')
        result = invoke('run', p)
        assert result.returncode == 0 and result.stdout == ('1\n[Group(key=long, items=[Ada, Grace]), '
                                                            'Group(key=short, items=[Al])]\ny\nAda\n'), \
            result.stdout + result.stderr
        print('if expressions: evaluation order, nesting with match, typing, narrowing, effects, '
              'negative cases and targeted syntax errors passed')


if __name__ == '__main__':
    main()
