#!/usr/bin/env python3
"""Release contracts exercised outside the checkout: bundled std and Java-only build."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import re

ROOT = Path(__file__).resolve().parents[2]
CLI = ROOT / 'bin' / ('sprig.cmd' if os.name == 'nt' else 'sprig')

def run(*args, cwd):
    return subprocess.run([str(CLI), *map(str, args)], cwd=cwd, text=True,
                          encoding='utf-8', capture_output=True)

def main():
    with tempfile.TemporaryDirectory(prefix='sprig release boundary ') as directory:
        home = Path(directory)
        source = home / 'main.spr'
        source.write_text('import "@std/text.spr" as text\nprint(text.trim("  hello  "))\n', encoding='utf-8')
        checked = run('run', source, '--json', cwd=home)
        assert checked.returncode == 0, checked.stdout + checked.stderr
        assert json.loads(checked.stdout)['programOutput'] == 'hello\n'
        output = home / 'generated'
        built = run('build', source, '--emit-java-only', '-d', output, '--json', cwd=home)
        assert built.returncode == 0, built.stdout + built.stderr
        envelope = json.loads(built.stdout)
        assert envelope['exitCode'] == 0
        assert envelope['javaSources'] and envelope['mainClass']
        assert envelope['javacInvoked'] is False
        assert list((output / 'java').rglob('*.java'))
        assert not list(output.rglob('*.class'))
        rejected = run('run', source, '--emit-java-only', '--json', cwd=home)
        assert rejected.returncode == 2, rejected.stdout
        source.write_text('let x: Int = "bad"\n', encoding='utf-8')
        bad = run('build', source, '--emit-java-only', '-d', home / 'bad', '--json', cwd=home)
        assert bad.returncode == 1 and 'SPR-TYPE-ASSIGN' in bad.stdout
        assert not (home / 'bad').exists()
        source.write_text('import "@std/../runtime/no.spr" as bad\n', encoding='utf-8')
        escape = run('check', source, '--json', cwd=home)
        assert escape.returncode == 1 and 'SPR-DEP-NOT-FOUND' in escape.stdout
        project = home / 'project'
        assert run('init', project, '--json', cwd=home).returncode == 0
        assert run('resolve', '--offline', '--json', cwd=project).returncode == 0
        lockpath = project / 'sprig.lock'
        lock = dict(re.findall(r'^([a-z0-9-]+) = "([^"]*)"$', lockpath.read_text(encoding='utf-8'), re.M))
        assert lock['stdlib-version'] == '0.4.0-alpha.1'
        digest = hashlib.sha256()
        for file in sorted((ROOT / 'std').glob('*.spr')):
            digest.update(file.name.encode('utf-8') + b'\0' + file.read_bytes() + b'\0')
        assert lock['stdlib-sha256'] == digest.hexdigest()
        lockpath.write_text(lockpath.read_text().replace(lock['stdlib-sha256'], '0' * 64), encoding='utf-8')
        stale = run('check', '--offline', '--json', cwd=project)
        assert stale.returncode == 1 and 'SPR-PROJECT-LOCK-STALE' in stale.stdout
        manifest = project / 'sprig.toml'
        manifest.write_text(manifest.read_text() + '\n[[dependency]]\nname = "std"\npath = "."\n', encoding='utf-8')
        shadow = run('resolve', '--offline', '--json', cwd=project)
        assert shadow.returncode == 1 and 'reserved' in shadow.stdout.lower()
    print('release hardening: standalone @std, traversal/shadow rejection, std lock integrity, Java-only output/static gate/option contract passed')

if __name__ == '__main__':
    main()
