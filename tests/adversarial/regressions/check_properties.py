#!/usr/bin/env python3
"""Small, seeded semantic programs with independent integer reference values."""
from pathlib import Path
import json
import random
import subprocess
import sys
import tempfile
ROOT = Path(__file__).resolve().parents[3]
SPRIG = ROOT/'bin'/('sprig.cmd' if sys.platform == 'win32' else 'sprig')
SEEDS = (9282026, 314159)

def call(command, file):
    result = subprocess.run([str(SPRIG), command, str(file), '--json'], text=True, capture_output=True, timeout=35)
    assert result.stdout, (command, file, result.returncode, result.stderr)
    data = json.loads(result.stdout)
    assert not result.stderr and result.returncode == data['exitCode'], result
    return result.returncode, data

def expression(rng, depth):
    if depth == 0:
        value = rng.randint(-20, 20)
        return str(value), value
    left, a = expression(rng, depth-1)
    right, b = expression(rng, depth-1)
    op = rng.choice(('+', '-', '*'))
    return f'({left} {op} {right})', {'+': lambda:a+b, '-': lambda:a-b, '*': lambda:a*b}[op]()

def main():
    programs = comparisons = 0
    with tempfile.TemporaryDirectory(prefix='sprig-properties-') as work:
        work = Path(work)
        for seed in SEEDS:
            rng = random.Random(seed)
            for number in range(12):
                n = rng.randint(1, 5)
                selected = rng.randrange(n)
                branches = list(range(n)); rng.shuffle(branches)
                values = [expression(rng, rng.randint(0, 3)) for _ in range(n)]
                prefix = 'generic T:\n    class Box:\n        let value: T\nvariant Value:\n'
                prefix += ''.join(f'    C{i}(value: Box[Int])\n' for i in range(n))
                prefix += f'func input() -> Value:\n    print("input")\n    return Value.C{selected}(value=Box[Int](value=7))\n'
                prefix += 'func branch(value: Int, index: Int) -> Int:\n    print(index)\n    return value\n'
                expr = prefix + 'let result = match input():\n'
                stmt = prefix + 'var result: Int = 0\nmatch input():\n'
                for i in branches:
                    header = f'    case Value.C{i} as bound:\n'
                    result = f'branch({values[i][0]} + bound.value.value, {i})'
                    expr += header + '        ' + result + '\n'
                    stmt += header + '        result = ' + result + '\n'
                expr += 'print(result)\n'; stmt += 'print(result)\n'
                expected = f'input\n{selected}\n{values[selected][1]+7}\n'
                for form, source in [('expression',expr), ('statement',stmt)]:
                    path = work/f'{seed}-{number}-{form}.spr'; path.write_text(source)
                    rc, data = call('run', path)
                    assert rc == 0 and data.get('programOutput','').replace('\r\n','\n') == expected, (seed, number, form, source, data)
                    comparisons += 1
                    rc, formatted = call('fmt', path); assert rc == 0, formatted
                    canonical = path.read_bytes()
                    rc, formatted = call('fmt', path); assert rc == 0 and canonical == path.read_bytes(), formatted
                    rc, data = call('run', path)
                    assert rc == 0 and data.get('programOutput','').replace('\r\n','\n') == expected, (seed, number, form, data)
                    comparisons += 1
                # Delete a case even when it is never selected by this run:
                # static exhaustiveness must not depend on the runtime value.
                missing = branches[-1]
                lines = expr.splitlines(keepends=True)
                for k, line in enumerate(lines):
                    if line.startswith(f'    case Value.C{missing} '):
                        del lines[k:k+2]; break
                bad = work/'missing.spr'; bad.write_text(''.join(lines))
                rc, data = call('check', bad)
                code = 'SPR-MATCH-NONEXHAUSTIVE' if n > 1 else 'SPR-SYNTAX-ERROR'
                assert rc == 1 and any(d['code'] == code for d in data['diagnostics']), (seed, number, data)
                programs += 1
            print(f'seed {seed}: 12 cases passed', flush=True)
    print(f'{programs} seeded variants/expression trees, {comparisons} exact JVM comparisons; 24 missing-case rejections')
if __name__ == '__main__': main()
