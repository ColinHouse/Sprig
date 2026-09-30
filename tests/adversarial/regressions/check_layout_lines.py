#!/usr/bin/env python3
"""Byte-preserving newline attacks against compiler, formatter, and grammar harness."""
from pathlib import Path
import json
import os
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[3]
SPRIG = ROOT / 'bin' / ('sprig.cmd' if sys.platform == 'win32' else 'sprig')


def command(args, **kwargs):
    return subprocess.run(list(map(str, args)), capture_output=True, text=True,
                          timeout=60, **kwargs)


def cli(verb, path, *flags):
    result = command([SPRIG, verb, path, '--json', *flags])
    assert not result.stderr, result.stderr
    body = json.loads(result.stdout)
    assert body['exitCode'] == result.returncode, body
    return result.returncode, body


def encoded(lines, endings, final):
    return ''.join(line + (endings[i % len(endings)] if i < len(lines)-1 or final else '')
                   for i, line in enumerate(lines)).encode('utf-8')


def main():
    # Expectations come from the explicit NEWLINE alternatives and formatter contract.
    lines = ['# header', '', 'func f() -> Int:', '    # inside',
             '    if true:', '        return (', '            1', '        )',
             '', '    return 2', '# outside', 'print(f())']
    expected_format = ('# header\n\nfunc f() -> Int:\n    # inside\n'
                       '    if true:\n        return (\n            1\n        )\n'
                       '\n    return 2\n# outside\nprint(f())\n').encode()
    failures = []
    count = 0
    with tempfile.TemporaryDirectory(prefix='sprig-layout-lines-') as tmp:
        work = Path(tmp)
        antlr = Path(os.environ.get('ANTLR_JAR', ROOT / 'build/deps/antlr-4.13.2-complete.jar')).resolve()
        harness = work / 'harness'
        harness.mkdir()
        for grammar in ('SprigLexer.g4', 'SprigParser.g4'):
            result = command(['java', '-jar', antlr, '-Dlanguage=Java', '-visitor',
                              '-no-listener', '-lib', harness, '-o', harness, grammar],
                             cwd=ROOT / 'grammar')
            assert result.returncode == 0, result.stderr
        sources = sorted(harness.glob('*.java')) + sorted((ROOT / 'tests/grammar/fixtures').glob('*.java'))
        result = command(['javac', '-cp', antlr, '-d', harness, *sources])
        assert result.returncode == 0, result.stderr
        parse = ['java', '-cp', os.pathsep.join(map(str, (harness, antlr))), 'ParseSmoke']
        # Start the mixed cycle with LF so a blank line cannot fuse CR + LF into CRLF.
        for name, endings in [('lf', ['\n']), ('crlf', ['\r\n']), ('cr', ['\r']),
                              ('mixed', ['\n', '\r\n', '\r'])]:
            for final in (False, True):
                label = f'{name}-{"newline" if final else "eof"}'
                path = work / (label + '.spr')
                path.write_bytes(encoded(lines, endings, final))
                stages = {}
                stages['independent-parser'] = command([*parse, path]).returncode == 0
                rc, body = cli('check', path)
                stages['static'] = rc == 0 and not body['diagnostics']
                rc, body = cli('run', path)
                stages['jvm'] = rc == 0 and not body['diagnostics'] and body['programOutput'] == '1\n'
                rc, body = cli('fmt', path)
                stages['format'] = rc == 0 and not body['diagnostics']
                if rc == 0:
                    stages['format-bytes'] = path.read_bytes() == expected_format
                    before = path.read_bytes()
                    rc, body = cli('fmt', path, '--check')
                    stages['idempotent'] = rc == 0 and path.read_bytes() == before
                    rc, body = cli('run', path)
                    stages['formatted-jvm'] = rc == 0 and body['programOutput'] == '1\n'
                count += 1
                bad = [stage for stage, passed in stages.items() if not passed]
                print('FAIL' if bad else 'PASS', label, ', '.join(bad))
                if bad:
                    failures.append(label)
            # Comments and blank lines must still advance diagnostic coordinates.
            for symbol, code in [('@', 'SPR-LEX-CHAR'), ('\t', 'SPR-LEX-TAB')]:
                path = work / f'{name}-bad-{len(symbol)}.spr'
                path.write_bytes(encoded(['# header', '', 'func f() -> Int:',
                                          '    ' + symbol + 'return 1'], endings, False))
                original = path.read_bytes()
                rc, body = cli('check', path)
                matching = [d for d in body['diagnostics'] if d['code'] == code]
                good = rc == 1 and any(d['range']['start'] == {'line': 3, 'character': 4}
                                      for d in matching)
                good &= command([*parse, path]).returncode != 0
                rc, body = cli('fmt', path)
                good &= rc == 1 and path.read_bytes() == original
                count += 1
                print('PASS' if good else 'FAIL', name, code)
                if not good:
                    failures.append(f'{name}-{code}')
    print(f'{count} newline attacks; {len(failures)} failures')
    return bool(failures)


if __name__ == '__main__':
    raise SystemExit(main())
