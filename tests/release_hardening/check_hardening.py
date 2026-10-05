#!/usr/bin/env python3
"""Release contracts exercised outside the checkout: bundled std and Java-only build."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import re

ROOT = Path(__file__).resolve().parents[2]
CLI = ROOT / 'bin' / ('sprig.cmd' if os.name == 'nt' else 'sprig')
JAVA = shutil.which('java')

def run(*args, cwd):
    return subprocess.run([str(CLI), *map(str, args)], cwd=cwd, text=True,
                          encoding='utf-8', capture_output=True)

def run_with_sdk(home, *args, cwd):
    antlr = ROOT / 'build' / 'deps' / 'antlr-4.13.2-complete.jar'
    resolver = sorted((ROOT / 'build' / 'deps' / 'resolver').glob('*.jar'))
    classpath = [ROOT / 'build' / 'sprig-compiler.jar', antlr, *resolver]
    return subprocess.run([
        JAVA, '-Dfile.encoding=UTF-8', '-cp', os.pathsep.join(map(str, classpath)),
        f'-Dsprig.home={home}', 'sprig.compiler.cli.Main', *map(str, args)
    ], cwd=cwd, text=True, encoding='utf-8', capture_output=True)

def main():
    with tempfile.TemporaryDirectory(prefix='sprig release boundary ') as directory:
        home = Path(directory)
        source = home / 'main.spr'
        source.write_text('import "@std/text.spr" as text\nprint(text.trim("  hello  "))\n', encoding='utf-8')
        checked = run('run', source, '--json', cwd=home)
        assert checked.returncode == 0, checked.stdout + checked.stderr
        assert json.loads(checked.stdout)['programOutput'].replace('\r\n', '\n') == 'hello\n'
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
        project_source = project / 'src' / 'main.spr'
        project_source.parent.mkdir(parents=True, exist_ok=True)
        project_source.write_text(
            'import "@std/text.spr" as text\nprint(text.trim("  hello  "))\n', encoding='utf-8')
        assert run('resolve', '--offline', '--json', cwd=project).returncode == 0
        lockpath = project / 'sprig.lock'
        original_lock = lockpath.read_bytes()
        lock_text = original_lock.decode('utf-8')
        assert 'lock-version = 5\n' in lock_text
        assert 'stdlib-version' not in lock_text and 'stdlib-sha256' not in lock_text

        # Compiler identity is a project-lock contract; SDK std bytes are not.
        mismatched_lock, compiler_fields = re.subn(
            r'^compiler = "[^"]*"$', 'compiler = "0.0.0-test-mismatch"',
            lock_text, count=1, flags=re.M)
        assert compiler_fields == 1
        lockpath.write_text(mismatched_lock, encoding='utf-8')
        compiler_mismatch = run('check', '--offline', '--json', cwd=project)
        assert compiler_mismatch.returncode == 1 \
            and 'SPR-PROJECT-LOCK-STALE' in compiler_mismatch.stdout \
            and 'Compiler version in sprig.lock does not match this SDK' in compiler_mismatch.stdout \
            and 'sprig resolve' in compiler_mismatch.stdout
        assert run('resolve', '--offline', '--json', cwd=project).returncode == 0
        original_lock = lockpath.read_bytes()

        legacy_fields = original_lock.decode('utf-8').splitlines()
        manifest_line = next(i for i, line in enumerate(legacy_fields)
                             if line.startswith('manifest-sha256 = '))
        legacy_fields[manifest_line + 1:manifest_line + 1] = [
            'stdlib-version = "0.5.0-beta.1"', 'stdlib-sha256 = "' + '0' * 64 + '"']
        lockpath.write_text('\n'.join(legacy_fields) + '\n', encoding='utf-8')
        old_identity = run('check', '--offline', '--json', cwd=project)
        assert old_identity.returncode == 1 and 'SPR-PROJECT-LOCK-SCHEMA' in old_identity.stdout
        lockpath.write_bytes(original_lock)

        # Schema 4 is rejected by consumers; an explicit resolve writes schema 5.
        lockpath.write_bytes(original_lock.replace(b'lock-version = 5', b'lock-version = 4', 1))
        old_schema = run('check', '--offline', '--json', cwd=project)
        assert old_schema.returncode == 1 and 'SPR-PROJECT-LOCK-SCHEMA' in old_schema.stdout
        assert run('resolve', '--offline', '--json', cwd=project).returncode == 0
        assert lockpath.read_bytes() == original_lock

        # A second installed SDK with identical compiler bytes but one changed
        # std module must consume and regenerate the same project lock.
        fixture_sdk = home / 'fixture-sdk'
        shutil.copytree(ROOT / 'std', fixture_sdk / 'std')
        text_module = fixture_sdk / 'std' / 'text.spr'
        text_module.write_bytes(text_module.read_bytes() + b'\n# fixture-only SDK byte change\n')
        changed_std_check = run_with_sdk(fixture_sdk, 'check', '--offline', '--json', cwd=project)
        assert changed_std_check.returncode == 0, changed_std_check.stdout + changed_std_check.stderr
        fixture_source = fixture_sdk / 'std' / 'escape.spr'
        outside_module = home / 'outside.spr'
        outside_module.write_text('func escaped() -> Int:\n    return 1\n', encoding='utf-8')
        try:
            fixture_source.symlink_to(outside_module)
        except (OSError, NotImplementedError):
            pass
        else:
            project_source.write_text('import "@std/escape.spr" as escape\n', encoding='utf-8')
            symlink_escape = run_with_sdk(fixture_sdk, 'check', '--offline', '--json', cwd=project)
            assert symlink_escape.returncode == 1 and 'SPR-DEP-NOT-FOUND' in symlink_escape.stdout
            fixture_source.unlink()
            project_source.write_text(
                'import "@std/text.spr" as text\nprint(text.trim("  hello  "))\n', encoding='utf-8')
        lockpath.unlink()
        regenerated = run_with_sdk(fixture_sdk, 'resolve', '--offline', '--json', cwd=project)
        assert regenerated.returncode == 0, regenerated.stdout + regenerated.stderr
        assert lockpath.read_bytes() == original_lock

        missing_sdk = home / 'missing-sdk'
        missing_sdk.mkdir()
        missing_root = run_with_sdk(missing_sdk, 'check', '--offline', '--json', cwd=project)
        assert missing_root.returncode == 1 and 'SPR-DEP-NOT-FOUND' in missing_root.stdout

        project_source.write_text('import "@std/missing.spr" as missing\n', encoding='utf-8')
        missing_std = run_with_sdk(fixture_sdk, 'check', '--offline', '--json', cwd=project)
        assert missing_std.returncode == 1 and 'SPR-DEP-NOT-FOUND' in missing_std.stdout
        manifest = project / 'sprig.toml'
        manifest.write_text(manifest.read_text() + '\n[[dependency]]\nname = "std"\npath = "."\n', encoding='utf-8')
        shadow = run('resolve', '--offline', '--json', cwd=project)
        assert shadow.returncode == 1 and 'reserved' in shadow.stdout.lower()
    print('release hardening: installed @std, traversal/shadow/missing-module rejection, schema-5 lock identity, SDK std-byte anti-churn, Java-only output/static gate/option contract passed')

if __name__ == '__main__':
    main()
