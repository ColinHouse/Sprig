#!/usr/bin/env python3
"""Build the same portable ZIP SDK on Linux, macOS, and Windows."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from build import ROOT, ANTLR_NAME, write_launchers


def output(*args):
    return subprocess.check_output([str(arg) for arg in args], cwd=ROOT, text=True, stderr=subprocess.STDOUT).strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--skip-build', action='store_true', help='package the existing successful build')
    args = parser.parse_args()
    if not args.skip_build:
        subprocess.run([sys.executable, str(ROOT / 'scripts/build.py')], check=True)
    launcher = ROOT / 'bin' / ('sprig.cmd' if os.name == 'nt' else 'sprig')
    version = output(launcher, 'version').split()[1]
    tag = 'v' + version
    expected = os.environ.get('SPRIG_PACKAGE_VERSION')
    if expected and expected != tag:
        raise RuntimeError(f'Tag {expected} does not match compiler {tag}')
    if expected:
        capabilities = json.loads(output(launcher, 'capabilities', '--json'))
        if capabilities['releaseStatus'] != 'prerelease; ' + tag:
            raise RuntimeError('Release packaging requires a compiler built from its clean exact tag')
        if output('git', 'describe', '--exact-match', '--tags', 'HEAD') != tag or output('git', 'status', '--porcelain'):
            raise RuntimeError('Release packaging requires the clean matching tagged source checkout')
    name = 'sprig-' + tag + '-jdk'
    dist = ROOT / 'dist'
    dist.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='sprig-package-') as temp:
        package = Path(temp) / name
        (package / 'lib').mkdir(parents=True)
        shutil.copy2(ROOT / 'build/sprig-compiler.jar', package / 'lib')
        shutil.copy2(ROOT / 'tools' / ANTLR_NAME, package / 'lib')
        resolver = sorted((ROOT / 'tools/resolver').glob('*.jar'))
        resolver_spec = json.loads((ROOT / 'tools/resolver-libraries.json').read_text(encoding='utf-8'))
        if {file.name for file in resolver} != set(resolver_spec['jars']):
            raise RuntimeError('Resolver libraries missing or unlisted: run scripts/build.py first')
        for file in resolver:
            if hashlib.sha256(file.read_bytes()).hexdigest() != resolver_spec['jars'][file.name]:
                raise RuntimeError('Resolver library checksum mismatch: ' + file.name)
        for file in resolver:
            shutil.copy2(file, package / 'lib')
        shutil.copytree(ROOT / 'tools/resolver/legal', package / 'legal/resolver')
        shutil.copy2(ROOT / 'tools/resolver-libraries.json', package / 'legal/resolver-libraries.json')
        for tree in ('runtime/src/main/java', 'examples', 'website/snippets', 'std', 'libraries'):
            if (ROOT / tree).is_dir():
                shutil.copytree(ROOT / tree, package / tree,
                                ignore=shutil.ignore_patterns('sprig.lock', '*.sqlite', '*.sqlite-*',
                                                             '__pycache__', 'sprig-build'))
        for source in ('tests/visitor/ast_visitor.spr', 'tests/runtime/20_string_codepoints.spr'):
            destination = package / Path(source).parent
            destination.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT / source, destination)
        docs = ['MATCH_EXPRESSIONS', 'MODULE_REEXPORTS', 'FORMATTER', 'INSTALL', 'QUICK_REFERENCE', 'FEATURE_STATUS_IMPLEMENTED', 'JVM_INTEROP', 'NUMERIC_SEMANTICS', 'DIAGNOSTIC_CODES', 'KNOWN_LIMITATIONS', 'HOST_SERVICES', 'GENERICS', 'PROJECTS', 'DEPENDENCIES', 'STANDARD_LIBRARY', 'SHOWCASES']
        (package / 'docs').mkdir()
        for doc in docs:
            file = ROOT / 'docs' / (doc + '.md')
            if file.is_file():
                shutil.copy2(file, package / 'docs')
        notes = ROOT / 'docs/releases' / ('RELEASE_NOTES-' + tag + '.md')
        if notes.is_file():
            (package / notes.name).write_text(re.sub(r'\]\(\.\./([^)]*\.md)\)', r'](docs/\1)', notes.read_text(encoding='utf-8')), encoding='utf-8')
        for file in ('AGENT_GUIDE.md', 'LICENSE', 'NOTICE', 'LICENSE_STATUS.md', 'THIRD_PARTY_NOTICES.md'):
            shutil.copy2(ROOT / file, package)
        (package / 'README.md').write_text(f'''# Sprig {tag} SDK

Experimental, Alpha, JDK 17+, language v0.8-dev. Supported: Linux/macOS.
Windows is an experimental preview, not a release-supported platform. Sprig is a small, explicit
JVM language for tools, automation and reliable application code. The SDK
contains the stage-0 compiler/runtime, ANTLR and pinned Maven Resolver libraries.
It compiles Sprig to Java, invokes javac, and runs on the JVM.

Start with INSTALL.md, AGENT_GUIDE.md, `sprig capabilities --json`, and
`sprig help --json`. See docs/ for static semantics and JVM boundaries.
Sprig is Apache-2.0; dependency licenses are in THIRD_PARTY_NOTICES.md and legal/.
''', encoding='utf-8')
        (package / 'INSTALL.md').write_text('''# Install and run

The supported managed installer for Linux/macOS is documented in
`docs/INSTALL.md`. It verifies the release checksum and keeps versioned SDKs
under `~/.sprig`. Requires JDK 17 or newer (`java` and `javac`) on PATH.
Windows is an experimental preview, not a release gate.

This archive can also be extracted manually. Add its `bin` directory to PATH;
invoke `bin/sprig` on Linux/macOS or `bin\\sprig.cmd` on Windows.

```text
sprig version
sprig check examples/hello.spr
sprig run examples/hello.spr
sprig init my-tool
cd my-tool
sprig resolve
sprig run
```

Maven dependencies need network access on first resolve; a complete cache can
be reused with `sprig resolve --offline` and `sprig run --offline`.
`sprig api java.time.LocalDate --json` inspects JVM APIs. Read docs/JVM_INTEROP.md
and docs/KNOWN_LIMITATIONS.md before relying on third-party calls.
''', encoding='utf-8')
        try:
            revision = output('git', 'rev-parse', 'HEAD')
            clean = not output('git', 'status', '--porcelain')
        except (OSError, subprocess.CalledProcessError):
            revision, clean = 'no Git metadata', False
        info = [f'Package version: {tag}', f'Source revision: {revision}', 'Working tree clean: ' + ('yes' if clean else 'no (development archive; revision alone does not identify all contents)'), 'Build Java:', output('java', '-version'), 'Bundled JAR SHA-256:']
        info += [hashlib.sha256(file.read_bytes()).hexdigest() + '  lib/' + file.name for file in sorted((package / 'lib').glob('*.jar'))]
        (package / 'BUILD_INFO.txt').write_text('\n'.join(info) + '\n', encoding='utf-8')
        (package / 'LEGAL_STATUS.txt').write_text('Sprig: Apache License 2.0 (LICENSE, NOTICE). ANTLR: BSD (THIRD_PARTY_NOTICES.md). Maven Resolver and bundled libraries: legal/resolver/ and legal/resolver-libraries.json.\n', encoding='utf-8')
        write_launchers(package, packaged=True)
        archive = dist / (name + '.zip')
        with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED) as zip:
            for file in sorted(package.rglob('*')):
                if file.is_file():
                    zip.write(file, file.relative_to(Path(temp)).as_posix())
        digest = hashlib.sha256(archive.read_bytes()).hexdigest()
        Path(str(archive) + '.sha256').write_text(digest + '  ' + archive.name + '\n', encoding='utf-8')
        print('Created ' + str(archive))
        print(digest + '  ' + archive.name)


if __name__ == '__main__':
    main()
