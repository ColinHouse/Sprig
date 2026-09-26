#!/usr/bin/env python3
"""Independent v0.8 adversarial corpus: frontend, javac, JVM and truncation."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
CLI = ROOT / 'bin/sprig'


def command(*args):
    p = subprocess.run([str(CLI), *map(str, args), '--json'], capture_output=True,
                       text=True, timeout=30)
    assert not p.stderr, (args, p.stderr)
    data = json.loads(p.stdout)
    assert data['exitCode'] == p.returncode, (args, data)
    assert not any(d['code'] == 'SPR-JVM-INTERNAL' for d in data['diagnostics']), data
    assert all('capabilities are not implemented yet' not in d['message'] for d in data['diagnostics']), data
    return p.returncode, data


def main():
    failures = []
    rows = json.loads((HERE / 'cases.json').read_text())
    with tempfile.TemporaryDirectory(prefix='sprig-v08-regression-') as temp:
        for case in rows:
            try:
                source = HERE / 'fixtures' / (case['name'] + '.spr')
                for mode in ('check', 'build'):
                    args = [mode, source]
                    if mode == 'build': args += ['-d', Path(temp) / case['name']]
                    status, data = command(*args)
                    if 'code' in case:
                        assert status == 1 and case['code'] in [d['code'] for d in data['diagnostics']], data
                    else:
                        assert status == 0 and data['diagnostics'] == [], data
                if 'output' in case:
                    status, data = command('run', source)
                    assert status == 0 and data.get('programOutput') == case['output'], data
                elif 'runtimeMessage' in case:
                    status, data = command('run', source)
                    assert status == 1 and any(d['code'] == 'SPR-RUNTIME-EXCEPTION'
                            and d['phase'] == 'RUNTIME' and case['runtimeMessage'] in d['message']
                            for d in data['diagnostics']), data
                    assert data.get('programOutput') == '', data
                print('pass', case['name'])
            except (AssertionError, ValueError, subprocess.TimeoutExpired) as error:
                failures.append(case['name'])
                print('FAIL', case['name'], str(error))
        # Every prefix is checked, including prefixes ending inside [Args]/payloads.
        source = (HERE / 'fixtures' / '3params.spr').read_text()
        fuzz = Path(temp) / 'truncated.spr'
        for length in range(len(source) + 1):
            fuzz.write_text(source[:length])
            try:
                status, data = command('check', '--syntax-only', fuzz)
                assert status in (0, 1), data
            except (AssertionError, ValueError, subprocess.TimeoutExpired) as error:
                failures.append('prefix-' + str(length))
                print('FAIL prefix', length, str(error))
    print(f'adversarial generics: {len(rows)} cases, {len(source)+1} prefixes, {len(failures)} failures')
    return bool(failures)


if __name__ == '__main__':
    sys.exit(main())
