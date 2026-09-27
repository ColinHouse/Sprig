#!/usr/bin/env python3
"""Portable stage-0 build. Requires Python 3 and JDK 17+ on PATH."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
ANTLR_NAME = 'antlr-4.13.2-complete.jar'
ANTLR_SHA256 = 'eae2dfa119a64327444672aff63e9ec35a20180dc5b8090b7a6ab85125df4d76'


def run(*args, cwd=ROOT):
    subprocess.run([str(arg) for arg in args], cwd=cwd, check=True)


def write_launchers(home, packaged=False):
    """Both launchers locate their home from their own path, including spaces."""
    bindir = home / 'bin'
    bindir.mkdir(parents=True, exist_ok=True)
    unix_cp = '$HERE/lib/*' if packaged else '$HERE/build/sprig-compiler.jar:$HERE/tools/' + ANTLR_NAME + ':$HERE/tools/resolver/*'
    windows_cp = '%SPRIG_HOME%\\lib\\*' if packaged else '%SPRIG_HOME%\\build\\sprig-compiler.jar;%SPRIG_HOME%\\tools\\' + ANTLR_NAME + ';%SPRIG_HOME%\\tools\\resolver\\*'
    (bindir / 'sprig').write_text('#!/bin/sh\nset -eu\nHERE="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"\nexec java -Dfile.encoding=UTF-8 -cp "' + unix_cp + '" -Dsprig.home="$HERE" sprig.compiler.cli.Main "$@"\n', encoding='utf-8')
    (bindir / 'sprig').chmod(0o755)
    (bindir / 'sprig.cmd').write_bytes(('@echo off\r\nsetlocal\r\nfor %%I in ("%~dp0..") do set "SPRIG_HOME=%%~fI"\r\njava -Dfile.encoding=UTF-8 -cp "' + windows_cp + '" "-Dsprig.home=%SPRIG_HOME%" sprig.compiler.cli.Main %*\r\nexit /b %errorlevel%\r\n').encode('utf-8'))


def main():
    for tool in ('java', 'javac', 'jar'):
        if not shutil.which(tool):
            raise RuntimeError('JDK 17+ required on PATH: missing ' + tool)
    antlr = Path(os.environ.get('ANTLR_JAR', ROOT / 'tools' / ANTLR_NAME)).resolve()
    if not antlr.is_file():
        print('Downloading pinned ANTLR 4.13.2...', flush=True)
        antlr.parent.mkdir(parents=True, exist_ok=True)
        temporary = antlr.with_suffix('.download')
        try:
            urllib.request.urlretrieve('https://repo.maven.apache.org/maven2/org/antlr/antlr4/4.13.2/antlr4-4.13.2-complete.jar', temporary)
            if hashlib.sha256(temporary.read_bytes()).hexdigest() != ANTLR_SHA256:
                raise RuntimeError('Downloaded ANTLR JAR checksum mismatch')
            temporary.replace(antlr)
        finally:
            temporary.unlink(missing_ok=True)
    actual = hashlib.sha256(antlr.read_bytes()).hexdigest()
    if actual != ANTLR_SHA256:
        raise RuntimeError(f'ANTLR checksum mismatch: expected {ANTLR_SHA256}, got {actual}')
    installed_antlr = ROOT / 'tools' / ANTLR_NAME
    if antlr != installed_antlr.resolve():
        installed_antlr.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(antlr, installed_antlr)
    run(sys.executable, ROOT / 'tools/fetch-resolver.py')
    resolver = sorted((ROOT / 'tools/resolver').glob('*.jar'))
    if not resolver:
        raise RuntimeError('No pinned resolver libraries were fetched')
    build = ROOT / 'build'
    for directory in ('gen', 'classes'):
        shutil.rmtree(build / directory, ignore_errors=True)
        (build / directory).mkdir(parents=True)
    (build / 'sprig-compiler.jar').unlink(missing_ok=True)
    print('Generating ANTLR parser...', flush=True)
    run('java', '-jar', antlr, '-Dlanguage=Java', '-visitor', '-no-listener', '-package', 'sprig.compiler.parser', '-o', build / 'gen', 'SprigLexer.g4', cwd=ROOT / 'grammar')
    run('java', '-jar', antlr, '-Dlanguage=Java', '-visitor', '-no-listener', '-package', 'sprig.compiler.parser', '-lib', build / 'gen', '-o', build / 'gen', 'SprigParser.g4', cwd=ROOT / 'grammar')
    sources = sorted(file for directory in (build / 'gen', ROOT / 'compiler/src/main/java', ROOT / 'runtime/src/main/java') for file in directory.rglob('*.java'))
    # javac argument-file quoting handles checkout paths with spaces on every OS.
    (build / 'sources.txt').write_text('\n'.join('"' + file.as_posix().replace('"', '\\"') + '"' for file in sources) + '\n', encoding='utf-8')
    print('Compiling compiler + runtime...', flush=True)
    run('javac', '--release', '17', '-encoding', 'UTF-8', '-Xlint:-options', '-cp', os.pathsep.join(str(file) for file in [antlr, *resolver]), '-d', build / 'classes', '@' + str(build / 'sources.txt'))
    resources = ROOT / 'compiler/src/main/resources'
    if resources.is_dir():
        shutil.copytree(resources, build / 'classes', dirs_exist_ok=True)
    # Only an exact clean release tag labels its artifact as a prerelease.
    catalog_file = build / 'classes/sprig/compiler/tooling/catalog.properties'
    catalog = catalog_file.read_text(encoding='utf-8')
    version = next(line.split('=', 1)[1] for line in catalog.splitlines() if line.startswith('compilerVersion='))
    try:
        tag = subprocess.check_output(['git', 'describe', '--exact-match', '--tags', 'HEAD'], cwd=ROOT, stderr=subprocess.DEVNULL, text=True).strip()
        clean = not subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT, text=True).strip()
    except (OSError, subprocess.CalledProcessError):
        tag, clean = '', False
    if tag == 'v' + version and clean:
        catalog = '\n'.join('releaseStatus=prerelease; ' + tag if line.startswith('releaseStatus=') else line for line in catalog.splitlines()) + '\n'
        catalog_file.write_text(catalog, encoding='utf-8')
    run('jar', '--create', '--file', build / 'sprig-compiler.jar', '-C', build / 'classes', '.')
    write_launchers(ROOT)
    cli = ROOT / 'bin' / ('sprig.cmd' if os.name == 'nt' else 'sprig')
    run(cli, 'version')
    print('Built Sprig stage-0 compiler: ' + str(cli))


if __name__ == '__main__':
    try:
        main()
    except (OSError, RuntimeError, subprocess.CalledProcessError) as error:
        print('Build failed: ' + str(error), file=sys.stderr)
        sys.exit(1)
