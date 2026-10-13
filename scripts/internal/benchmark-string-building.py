#!/usr/bin/env python3
"""Compare immutable += with StringBuilder; manual, bounded benchmark, not a CI threshold.

Both variants run in the same JVM with three warmups and seven measured samples
by default. Time covers only each construction; exact expected-text assertions
run outside the timed interval. Compilation/startup are excluded. Order alternates.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import statistics
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SPRIG = ROOT / 'bin' / ('sprig.cmd' if os.name == 'nt' else 'sprig')
SOURCE = '''import java.lang.StringBuilder as StringBuilder
import java.lang.System as System

func concatenate(count: Int) -> String:
    var result = ""
    for index in range(count):
        result += "row,oak,12\\n"
    return result

func build_text(count: Int) -> String:
    let builder = StringBuilder()
    for index in range(count):
        builder.append("row,oak,12\\n")
    return builder.toString()

func sample(kind: Int, count: Int, expected: String, measured: Bool) -> Unit:
    let start = System.nanoTime()
    let result = if kind == 0:
        concatenate(count)
    else:
        build_text(count)
    let elapsed = System.nanoTime() - start
    assert(result == expected)
    if measured:
        print(kind + " " + elapsed)

let expected = "row,oak,12\\n".repeat(ROWS)
for turn in range(WARMUPS + SAMPLES):
    if turn % 2 == 0:
        sample(0, ROWS, expected, turn >= WARMUPS)
        sample(1, ROWS, expected, turn >= WARMUPS)
    else:
        sample(1, ROWS, expected, turn >= WARMUPS)
        sample(0, ROWS, expected, turn >= WARMUPS)
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rows', type=int, default=20000)
    parser.add_argument('--warmups', type=int, default=3)
    parser.add_argument('--samples', type=int, default=7)
    args = parser.parse_args()
    if not 1 <= args.rows <= 100000 or args.warmups < 1 or args.samples < 3:
        parser.error('rows must be 1..100000, warmups >=1, samples >=3')
    source = SOURCE.replace('ROWS', str(args.rows)).replace('WARMUPS', str(args.warmups)).replace('SAMPLES', str(args.samples))
    with tempfile.TemporaryDirectory(prefix='sprig-string-bench-') as tmp:
        path = Path(tmp) / 'benchmark.spr'
        path.write_text(source, encoding='utf-8')
        built = subprocess.run([str(SPRIG), 'build', str(path), '-d', str(Path(tmp) / 'out'), '--json'],
                               text=True, capture_output=True, check=True, cwd=ROOT)
        report = json.loads(built.stdout)
        result = subprocess.run(['java', '-Xms256m', '-Xmx256m', '-cp', report['classes'], report['mainClass']],
                                text=True, capture_output=True, check=True, cwd=ROOT)
    times = {'concatenate': [], 'builder': []}
    for line in result.stdout.splitlines():
        kind, nanoseconds = line.split()
        times['concatenate' if kind == '0' else 'builder'].append(int(nanoseconds) / 1000000)
    assert all(len(values) == args.samples for values in times.values())
    expected = ('row,oak,12\n' * args.rows).encode('utf-8')
    print(json.dumps({
        'platform': platform.platform(), 'machine': platform.machine(),
        'java': subprocess.run(['java', '-version'], text=True, capture_output=True, check=True).stderr.strip(),
        'rows': args.rows, 'warmups_per_variant': args.warmups, 'samples_per_variant': args.samples,
        'heap': '256 MiB', 'milliseconds': times,
        'median_ms': {name: statistics.median(values) for name, values in times.items()},
        'expected_utf8_bytes': len(expected), 'expected_sha256': hashlib.sha256(expected).hexdigest(),
        'validation': 'Every result equals the expected repeated text inside the JVM, after timing.'
    }, indent=2))


if __name__ == '__main__':
    main()
