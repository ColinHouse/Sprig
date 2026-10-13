#!/usr/bin/env python3
"""CSV package contract, Maven lock/offline use, UTF-8 IO and runnable example."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
CLI = ROOT / 'bin' / ('sprig.cmd' if os.name == 'nt' else 'sprig')


def command(project, *args):
    result = subprocess.run([str(CLI), *map(str, args)], cwd=project,
                            capture_output=True, text=True, encoding='utf-8', timeout=120)
    assert result.returncode == 0, (args, result.stdout, result.stderr)
    return result.stdout


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--offline', action='store_true')
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix='sprig csv contract space ') as temp:
        work = Path(temp)
        shutil.copytree(ROOT / 'libraries/sprig-csv', work / 'libraries/sprig-csv',
                        ignore=shutil.ignore_patterns('sprig.lock', 'sprig-build'))
        project = work / 'examples/csv'
        shutil.copytree(ROOT / 'examples/csv', project,
                        ignore=shutil.ignore_patterns('sprig.lock', 'sprig-build'))
        resolved = json.loads(command(project, 'resolve', *(['--offline'] if args.offline else []), '--json'))
        assert resolved['exitCode'] == 0
        lock = (project / 'sprig.lock').read_bytes()
        command(project, 'resolve', '--offline')
        assert (project / 'sprig.lock').read_bytes() == lock
        deps = json.loads(command(project, 'deps', '--json'))['jvmDependencies']
        assert any(d['group'] == 'org.apache.commons' and d['artifact'] == 'commons-csv'
                   and d['version'] == '1.14.1' and d['resolved'] for d in deps), deps
        shutil.copy(ROOT / 'tests/csv/contract.spr', project / 'src/contract.spr')
        malformed = work / 'malformed.csv'
        malformed.write_text('name\n"unfinished', encoding='utf-8')
        invalid = work / 'invalid-utf8.csv'
        invalid.write_bytes(b'name\n\xff\n')
        output = work / 'round trip.csv'
        assert command(project, 'run', 'src/contract.spr', '--offline', '--', output,
                       work / 'missing.csv', invalid, malformed,
                       work / 'missing-parent/out.csv') == 'CSV contract PASS\n'
        assert not output.exists()
        example = work / 'example output.csv'
        assert command(project, 'run', '--offline', '--', example) == 'CSV rows=2\n世界: tea, cake\nZoë: first line\nsecond line\n'
        assert example.read_bytes() == 'name,note\r\n世界,"tea, cake"\r\nZoë,"first line\nsecond line"\r\n'.encode('utf-8')
    print('CSV Sprig contract + locked Maven/offline + UTF-8 IO + example PASS')


if __name__ == '__main__':
    main()
