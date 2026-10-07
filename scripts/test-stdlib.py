#!/usr/bin/env python3
"""Focused JVM behavior contracts for the small practical standard layer."""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]

def check_process_io(launcher):
    """Standard input, standard error and exit status of @std/process.spr, through real pipes."""
    import json
    source = str(ROOT / 'tests/stdlib/process_io.spr')

    def run(mode, *extra, stdin=b'', as_json=False):
        command = [launcher, 'run', source] + (['--json'] if as_json else []) + ['--', mode, *extra]
        result = subprocess.run(command, cwd=ROOT, input=stdin, capture_output=True)
        # Bytes in, bytes out: line endings and UTF-8 are part of the contract.
        return result.returncode, result.stdout.decode('utf-8').replace('\r\n', '\n'), \
            result.stderr.decode('utf-8').replace('\r\n', '\n')

    # read_line: LF and CRLF end a line, text is UTF-8, the last line needs no ending, null at the end.
    code, out, err = run('line', stdin='first\r\n第二行 😀\n\nlast'.encode('utf-8'))
    assert (code, err) == (0, ''), (code, out, err)
    assert out.splitlines() == [
        '1 [first] 5: 102 105 114 115 116',
        '2 [第二行 😀] 5: 31532 20108 34892 32 128512',
        '3 [] 0:',
        '4 [last] 4: 108 97 115 116',
        'end after 4', 'true', '0',
    ], repr(out)
    code, out, err = run('line')
    assert (code, out.splitlines(), err) == (0, ['end after 0', 'true', '0'], ''), (code, out, err)
    # read_lines: every remaining line, no element for the final line ending, then nothing left.
    code, out, err = run('lines', stdin=b'a\nb\r\n\nc\n')
    assert (code, out.splitlines(), err) == (0, ['4', '[a]', '[b]', '[]', '[c]', '0'], ''), (code, out, err)
    code, out, err = run('lines', stdin=b'a\rb')  # a lone CR ends a line too
    assert (code, out.splitlines(), err) == (0, ['2', '[a]', '[b]', '0'], ''), (code, out, err)
    # read_all after read_line: the rest, with its line endings unchanged (13 10 is CR LF).
    code, out, err = run('rest', stdin=b'head\r\ntail\r\nx')
    assert (code, out.splitlines(), err) == (0, ['[head]', '7: 116 97 105 108 13 10 120'], ''), (code, out, err)
    # Bytes that are not UTF-8 are an Error, never a replacement character.
    code, out, err = run('lines', stdin=b'ok\n\xff\xfe\n')
    assert code == 1 and out == '' and 'Cannot read standard input: it is not valid UTF-8' in err, (code, out, err)

    # print_error goes to standard error; exit flushes and sets the status the run command forwards.
    code, out, err = run('fail')
    assert (code, out, err) == (3, 'before\n', 'problem: 说明 😀\n'), (code, out, err)
    code, out, err = run('ok')
    assert (code, out, err) == (0, 'done\n', 'note\n'), (code, out, err)
    code, out, err = run('status', '255')
    assert (code, out, err) == (255, '', ''), (code, out, err)
    code, out, err = run('status', '300')
    assert code == 1 and 'SPR-RUNTIME-EXCEPTION' in err \
        and 'Exit status must be between 0 and 255: 300' in err, (code, out, err)

    # --json: standard error is carried next to programOutput, and standard input is empty.
    code, out, err = run('fail', as_json=True)
    envelope = json.loads(out)
    assert code == 3 and envelope['exitCode'] == 3, (code, out, err)
    assert envelope['programOutput'].replace('\r\n', '\n') == 'before\n', envelope
    assert envelope['programErrorOutput'].replace('\r\n', '\n') == 'problem: 说明 😀\n', envelope
    assert [d['code'] for d in envelope['diagnostics']] == ['SPR-PROGRAM-EXIT'], envelope
    code, out, err = run('lines', stdin=b'not delivered\n', as_json=True)
    envelope = json.loads(out)
    assert code == 0 and envelope['programOutput'].replace('\r\n', '\n') == '0\n0\n', (code, out, err)
    assert 'programErrorOutput' not in envelope, envelope

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
    with tempfile.TemporaryDirectory(prefix='sprig std file errors ') as work:
        data = Path(work) / 'data'
        (data / 'sub').mkdir(parents=True)
        (data / 'ok.txt').write_text('café', encoding='utf-8')
        (data / 'input.bin').write_bytes(b'\xff\xfe\x00bad')
        (data / 'exists.txt').write_text('x', encoding='utf-8')
        errors = subprocess.run([launcher, 'run', str(ROOT / 'tests/stdlib/file_errors.spr')], cwd=work, text=True, encoding='utf-8', capture_output=True)
        assert errors.returncode == 0, errors.stderr
        assert errors.stdout.splitlines() == [
            'read ok: ok café',
            'read missing: cannot read data/missing.txt: no such file',
            'read directory: cannot read data: it is a directory',
            'read invalid: cannot read data/input.bin: not valid UTF-8',
            'read lines missing: cannot read data/missing.txt: no such file',
            'write missing parent: cannot write data/no-such-dir/out.txt: the parent directory does not exist',
            'write directory: cannot write data/sub: it is a directory',
            'atomic missing parent: cannot write data/no-such-dir/out.txt: the parent directory does not exist',
            'make dir over file: cannot create directory data/exists.txt: a file with that name already exists',
            'copy missing: cannot copy data/missing.txt to data/copy.txt: the source does not exist',
            'copy onto existing: cannot copy data/ok.txt to data/exists.txt: the target already exists',
            "copy into missing dir: cannot copy data/ok.txt to data/nowhere/copy.txt: the target's parent directory does not exist",
            'copy directory: cannot copy data/sub to data/copy2: the source is a directory',
            'move missing: cannot move data/missing.txt to data/moved.txt: the source does not exist',
            'remove missing: cannot remove data/missing.txt: no such file',
            'remove directory: cannot remove data/sub: it is a directory',
            'list missing: cannot list data/missing: no such directory',
            'list file: cannot list data/ok.txt: it is not a directory',
            'walk missing: cannot list data/missing: no such directory',
            'file name of root: cannot take the file name of /: a root has none',
            '2',
        ], repr(errors.stdout)
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
    assert lines[12:14] == ['serializer rejected invalid number', 'serializer rejected duplicate keys']
    # A token cut short by the end of the text fails at its own offset; one that
    # ends exactly at the end is read. 東 makes the offsets code points.
    assert lines[14:] == [
        'JSON at code point offset 0: expected true',
        'JSON at code point offset 1: expected null',
        'JSON at code point offset 5: expected false',
        'JSON at code point offset 5: expected ]',
        'JSON at code point offset 4: expected :',
        'JSON at code point offset 6: expected }',
        'JSON at code point offset 5: expected true',
        'JSON at code point offset 0: expected true',
        'JSON at code point offset 2: expected ]',
        'parsed true',
        'parsed [null]',
        'parsed {"東":false}',
    ], repr(lines[14:])
    lookup = subprocess.run([launcher, 'run', str(ROOT / 'tests/stdlib/json_lookup.spr')], cwd=ROOT,
                            text=True, encoding='utf-8', capture_output=True)
    assert lookup.returncode == 0, (lookup.stdout, lookup.stderr)
    assert lookup.stdout.splitlines() == [
        'Missing', 'Found:null', 'Found:false', 'Found:0', 'Found:""',
        'Found:{"x":1}', 'Found:"值"', 'Missing', 'object unchanged',
        'wrong-kind=5', 'duplicate lookup rejected=2', 'parser duplicates unchanged']
    math_result = subprocess.run([launcher, 'run', str(ROOT / 'tests/stdlib/math.spr')],
                                 cwd=ROOT, text=True, encoding='utf-8', capture_output=True)
    assert math_result.returncode == 0, (math_result.stdout, math_result.stderr)
    assert math_result.stdout.splitlines() == [
        '0', '17', '17', '3', '7', '-1', '0', '1', '10', '0', '5',
        '1', '-2', '-2', '1', '0', '1', '3', '1000',
        'clamp rejected', 'division rejected', 'negative sqrt rejected',
    ], repr(math_result.stdout)
    lists_result = subprocess.run([launcher, 'run', str(ROOT / 'tests/stdlib/lists.spr')],
                                  cwd=ROOT, text=True, encoding='utf-8', capture_output=True)
    assert lists_result.returncode == 0, (lists_result.stdout, lists_result.stderr)
    assert lists_result.stdout.splitlines() == [
        'water', 'bread', 'tea', 'coffee', 'cake',
        'tea', 'coffee', 'water', 'cake', 'bread',
        'drink 3 750', 'food 2 650',
        '[-0.0, 0.0, 1.0, 2.5, NaN]',
        '3', 'NaN 1', '0.0 2', 'NaN 1',
        '0', '0', '16',
        # find: first match in list order, null when none, stops at the match
        'bread', 'true', '5', '2',
        # any, all: empty-list answers and where each stops
        'true', 'false', 'false', 'true', '2',
        'true', 'false', 'true', 'false', '2',
        # count, sum, sum_by
        '3', '0', '6', '0', '1400', '0',
        # sorted: a new list, input unchanged, the order of MutableList.sort()
        '[apple, apple, fig, pear]', '[pear, apple, fig, apple]', '[-0.0, 0.0, 1.0, 2.5, NaN]', '0',
        # rethrows: a throwing lambda makes the helper call throw; the first
        # bad value ends the try block
        '850', '2', '[100, 300, 450]', '850', '100', 'true', 'caught not a number: x',
    ], repr(lists_result.stdout)
    helpers_result = subprocess.run([launcher, 'run', str(ROOT / 'tests/stdlib/helpers.spr')],
                                    cwd=ROOT, text=True, encoding='utf-8', capture_output=True)
    assert helpers_result.returncode == 0, (helpers_result.stdout, helpers_result.stderr)
    assert helpers_result.stdout.splitlines() == [
        # nulls.or_else and nulls.require, then counting through nullable map reads
        'fallback', 'set', 'set', 'rejected: name is required', '12',
        '{tea: 2, rice: 1}', '0',
        # text.pad_left and text.pad_right: repeated fill cut to fit, widths in code points
        '[007]', '[ab...]', '[xyxyxyxabc]', '[abc123]', '[long]', '[same]', '[a]', '[a]',
        '[😀😀東] 3',
        # text.is_ascii_digit, then text.is_ascii_letter
        'true', 'true', 'false', 'false', 'false', 'false', 'false',
        'true', 'true', 'false', 'false', 'false', 'false',
        # test.equal_int, equal_bool and equal_text report both values
        'sum: expected 4, got 5',
        'flag: expected true, got false',
        r'body: expected "a\nb", got "a\r\nb\t\"q\"\\"',
    ], repr(helpers_result.stdout)
    check_process_io(launcher)
    with tempfile.TemporaryDirectory(prefix='sprig std batteries ') as scratch:
        batteries = subprocess.run([launcher, 'run', str(ROOT / 'tests/stdlib/batteries.spr'), '--', scratch],
                                   cwd=ROOT, text=True, encoding='utf-8', capture_output=True)
    assert batteries.returncode == 0, (batteries.stdout, batteries.stderr)
    assert batteries.stdout.splitlines() == [
        # sets: insertion order, membership, add/remove report change, algebra
        '[pear, apple, fig]', '3', 'true', 'false', 'true', 'false', 'true', 'false', '[apple, fig, plum]',
        '[3, 1, 2, 4]', '[3, 2]', '[1]', '0',
        # random: same seed same draws, bounds, a permutation, choice, checked bounds, UUID text
        'true', 'true', 'true', '[1, 2, 3, 4, 5]', '5', 'only',
        'caught next_int bound must be positive: 0', 'caught choice needs a non-empty list', '36', 'true',
        # regex
        'true', 'false', '66', 'true', '[66, 99]', 'a-b-c', 'host:ada', '[a, b, c]', 'caught invalid pattern',
        # dates
        '2026-10-06', 'false', '2026-11-05', '2025-12-31', '87', '-87', '2', '2026 10 6', '10',
        'caught not a date: yesterday',
        # lists: first/last, take/drop, reversed, distinct, index_of, enumerate, zip
        'pear', 'apple', 'true', '[pear, apple]', '[pear, apple, fig, apple]', '[apple]', '[]',
        '[apple, fig, apple, pear]', '[pear, apple, fig]', '2', '-1', '0:a', '1:b', 'a=1', 'b=2',
        'caught take count must not be negative: -1',
        # text: strip_prefix/strip_suffix, fixed rounds half away from zero
        'lock', 'sprig.lock', 'sprig', '3', '1.01', '3.00', '-1.3', 'caught fixed decimals must not be negative: -1',
        # files: read_lines splits like text.lines; walk is sorted and recursive
        '[one, two, three]', '/a.txt', '/b.txt', '/deep/c.txt',
        # time: sleep waits at least the given time on the monotonic clock
        'true', 'caught sleep millis must not be negative: -1',
    ], repr(batteries.stdout)
    runner = subprocess.run([launcher, 'run', str(ROOT / 'tests/stdlib/runner.spr')],
                            cwd=ROOT, text=True, encoding='utf-8', capture_output=True)
    # A failed check does not stop the program; finish() ends it with status 1.
    assert runner.returncode == 1, (runner.returncode, runner.stdout, runner.stderr)
    assert runner.stdout.splitlines() == [
        'ok adds', 'ok parses', 'ok compares lists', 'ok compares text',
        'FAIL fails on purpose: sum: expected 5, got 4',
        'FAIL sees the parse error: not a number: x',
        'ok rejects text', 'FAIL expected an error: no Error was thrown',
        'ok runs a child', 'ok keeps the old name',
        '7 passed, 3 failed',
    ], repr(runner.stdout)
    assert 'not reached' not in runner.stdout
    practical_outputs = []
    for timezone in ('UTC', 'Pacific/Honolulu'):
        with tempfile.TemporaryDirectory(prefix='sprig std practical ') as work:
            env = dict(os.environ, TZ=timezone)
            result = subprocess.run(
                [launcher, 'run', str(ROOT / 'tests/stdlib/practical.spr'), '--',
                 str(Path(work) / 'files'), str(ROOT)], cwd=ROOT, env=env,
                text=True, encoding='utf-8', capture_output=True)
            assert result.returncode == 0, (timezone, result.stdout, result.stderr)
            lines = result.stdout.splitlines()
            assert lines == [
                'true', 'true', 'true', 'true', '你好 Sprig', 'true', 'true', '你好 Sprig',
                'true', 'true', 'keep', '原子写入 ✓', 'true', 'keep', 'true', 'true', 'true', 'true',
                '', 'only', 'a::::c', '东✓😀✓é', 'ab',
                '1970-01-01T00:00:00Z', '1969-12-31T23:59:59.999Z',
                '1709210096789', '2024-02-29T12:34:56.789Z', 'true', 'true', 'true', 'true'
            ], (timezone, repr(lines))
            practical_outputs.append(lines)
    assert practical_outputs[0] == practical_outputs[1], practical_outputs
    print('stdlib: UTF-8/path/file operations/temp file, file failure messages, UTC parse/format across timezones, text.join, list/null/text helpers, test checks and runner, process input/output/exit/run, sets/random/regex/dates and recursive JSON contracts passed')

if __name__ == '__main__':
    main()
