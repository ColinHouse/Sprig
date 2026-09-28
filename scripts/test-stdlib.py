#!/usr/bin/env python3
"""Focused JVM behavior contracts for the small practical standard layer."""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--launcher', type=Path, default=ROOT / 'bin' / ('sprig.cmd' if os.name == 'nt' else 'sprig'))
    options = parser.parse_args()
    launcher = str(options.launcher.resolve())
    with tempfile.TemporaryDirectory(prefix='sprig std space ') as work:
        environment = dict(os.environ, SPRIG_HOST_TEST='host environment ✓')
        environment.pop('SPRIG_HOST_UNSET_TEST', None)
        host = subprocess.run([launcher, 'run', str(ROOT / 'tests/stdlib/host.spr'), '--', str(Path(work) / 'nested')], cwd=ROOT, env=environment, text=True, encoding='utf-8', capture_output=True)
        expected = '你好\nSprig\ntrue\ntrue\n1\n3\n3\ntrue\ntrue\ntrue\nhost environment ✓\n1\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\n'
        assert host.returncode == 0, host.stderr
        assert host.stdout == expected, repr(host.stdout)
        assert (Path(work) / 'nested/héllo.txt').read_text(encoding='utf-8') == '你好\nSprig'
    result = subprocess.run([launcher, 'run', str(ROOT / 'tests/stdlib/json.spr')], cwd=ROOT, text=True, encoding='utf-8', capture_output=True)
    expected = '{"ok":true,"nested":[null,12.50,"a\\nb",{"x":-2e3}]}\n"你好"\nrejected trailing comma\nrejected duplicate key\nrejected leading zero\nrejected trailing text\n'
    assert result.returncode == 0, result.stderr
    assert result.stdout == expected, repr(result.stdout)
    edges = subprocess.run([launcher, 'run', str(ROOT / 'tests/stdlib/json_edges.spr')], cwd=ROOT, text=True, encoding='utf-8', capture_output=True)
    assert edges.returncode == 0, edges.stderr
    lines = edges.stdout.splitlines()
    assert lines[0] == 'invalid JSON rejected=19'
    assert lines[1] == '[-0,0.0,1E+2,-9223372036854775809]'
    import json
    assert json.loads(lines[2]) == '\b\f\t\r\n\x00/\\"'
    independently_built_depth_128 = '[' * 128 + 'null' + ']' * 128
    assert lines[3:11] == [
        'true',
        'parse depth 128 accepted=128',
        f'depth-128 output length={len(independently_built_depth_128)}',
        'parse depth 129 rejected=true',
        f'serialize depth 128 accepted={len(independently_built_depth_128)}',
        'serialize depth 129 rejected=true',
        'supplementary pair="' + json.loads('"\\ud83d\\ude00"') + '"',
        'escaped duplicate rejected=true',
    ]
    assert lines[11] == 'large exponent=1e9999'
    assert lines[12:] == ['serializer rejected invalid number', 'serializer rejected duplicate keys']
    lookup = subprocess.run([launcher, 'run', str(ROOT / 'tests/stdlib/json_lookup.spr')], cwd=ROOT,
                            text=True, encoding='utf-8', capture_output=True)
    assert lookup.returncode == 0, (lookup.stdout, lookup.stderr)
    assert lookup.stdout.splitlines() == [
        'Missing', 'Found:null', 'Found:false', 'Found:0', 'Found:""',
        'Found:{"x":1}', 'Found:"值"', 'Missing', 'object unchanged',
        'wrong-kind=5', 'duplicate lookup rejected=2', 'parser duplicates unchanged']
    print('stdlib: UTF-8/path/list/args/environment/text/time and recursive JSON JVM contracts passed')

if __name__ == '__main__':
    main()
