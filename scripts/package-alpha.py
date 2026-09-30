#!/usr/bin/env python3
"""Build the same portable ZIP SDK on Linux, macOS, and Windows."""
import argparse
import hashlib
import json
import os
import posixpath
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


def rewrite_archive_links(text, source, archive_target, archive_paths, package):
    """Map repository-local Markdown links onto the compatibility archive tree."""
    def replace(match):
        href = match.group(1)
        target, separator, anchor = href.partition('#')
        if not target or re.match(r'^[a-z][a-z0-9+.-]*:', target, re.I) or target.startswith('/'):
            return match.group(0)
        source_target = (ROOT / source).parent.joinpath(target).resolve()
        try:
            source_rel = source_target.relative_to(ROOT.resolve()).as_posix()
        except ValueError:
            return match.group(0)
        archive_rel = archive_paths.get(source_rel)
        if archive_rel is None and package.joinpath(source_rel).exists():
            archive_rel = source_rel
        if archive_rel is None:
            return match.group(0)
        mapped = posixpath.relpath(archive_rel, posixpath.dirname(archive_target) or '.')
        return match.group(0).replace(href, mapped + (separator + anchor if separator else ''))
    return re.sub(r'\]\(([^)\s]+)\)', replace, text)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--skip-build', action='store_true', help='package the existing successful build')
    args = parser.parse_args()
    if not args.skip_build:
        subprocess.run([sys.executable, str(ROOT / 'scripts/build.py')], check=True)
    launcher = ROOT / 'bin' / ('sprig.cmd' if os.name == 'nt' else 'sprig')
    version = output(launcher, 'version').split()[1]
    tag = 'v' + version
    maturity = 'Beta' if '-beta.' in version else 'Alpha' if '-alpha.' in version else 'Stable'
    maturity_label = 'Experimental, ' + maturity if maturity in ('Alpha', 'Beta') else 'Stable'
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
        shutil.copy2(ROOT / 'build/deps' / ANTLR_NAME, package / 'lib')
        resolver = sorted((ROOT / 'build/deps/resolver').glob('*.jar'))
        resolver_spec = json.loads((ROOT / 'scripts/internal/resolver-libraries.json').read_text(encoding='utf-8'))
        if {file.name for file in resolver} != set(resolver_spec['jars']):
            raise RuntimeError('Resolver libraries missing or unlisted: run scripts/build.py first')
        for file in resolver:
            if hashlib.sha256(file.read_bytes()).hexdigest() != resolver_spec['jars'][file.name]:
                raise RuntimeError('Resolver library checksum mismatch: ' + file.name)
        for file in resolver:
            shutil.copy2(file, package / 'lib')
        shutil.copytree(ROOT / 'build/deps/resolver/legal', package / 'legal/resolver')
        shutil.copy2(ROOT / 'scripts/internal/resolver-libraries.json', package / 'legal/resolver-libraries.json')
        for tree in ('runtime/src/main/java', 'examples', 'website/snippets', 'std', 'libraries'):
            if (ROOT / tree).is_dir():
                shutil.copytree(ROOT / tree, package / tree,
                                ignore=shutil.ignore_patterns('sprig.lock', '*.sqlite', '*.sqlite-*',
                                                             '__pycache__', 'sprig-build'))
        # The repository examples index points to VitePress pages, which are
        # not part of the portable SDK archive. Give SDK users links that are
        # present in the package instead of shipping broken relative links.
        examples_readme = package / 'examples/README.md'
        if examples_readme.is_file():
            examples_index = examples_readme.read_text(encoding='utf-8')
            examples_index = examples_index.replace(
                'Begin with the [executable bilingual tutorial](../website/tutorial.md).',
                'Begin with the [Sprig Agent Guide](../AGENT_GUIDE.md).')
            examples_index = examples_index.replace(
                '../website/guide/fabric.md', '../docs/SHOWCASES.md')
            examples_readme.write_text(examples_index, encoding='utf-8')
        for source in ('tests/visitor/ast_visitor.spr', 'tests/runtime/20_string_codepoints.spr'):
            destination = package / Path(source).parent
            destination.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT / source, destination)
        docs = {
            'MATCH_EXPRESSIONS': 'docs/language/match-expressions.md',
            'MODULE_REEXPORTS': 'docs/language/module-reexports.md',
            'QUICK_REFERENCE': 'docs/language/quick-reference.md',
            'FEATURE_STATUS_IMPLEMENTED': 'docs/language/feature-status.md',
            'KNOWN_LIMITATIONS': 'docs/language/known-limitations.md',
            'NUMERIC_SEMANTICS': 'docs/language/numeric-semantics.md',
            'NUMERIC_DESIGN_DECISIONS': 'docs/language/numeric-design-decisions.md',
            'GENERICS': 'docs/language/generics.md',
            'INSTALL': 'docs/projects/install.md', 'PROJECTS': 'docs/projects/projects.md',
            'DEPENDENCIES': 'docs/projects/dependencies.md',
            'STANDARD_LIBRARY': 'docs/projects/standard-library.md',
            'FORMATTER': 'docs/tooling/formatter.md', 'TESTING': 'docs/tooling/testing.md',
            'DIAGNOSTIC_CODES': 'docs/tooling/diagnostic-codes.md',
            'SHOWCASES': 'docs/tooling/showcases.md',
            'JVM_INTEROP': 'docs/jvm/interop.md', 'WRAP': 'docs/jvm/wrap.md',
            'JVM_CONFORMANCE': 'docs/jvm/conformance.md', 'HOST_SERVICES': 'docs/jvm/host-services.md',
        }
        archive_paths = {source: 'docs/' + archive_name + '.md'
                         for archive_name, source in docs.items()}
        archive_paths.update({
            'docs/tooling/agent-guide.md': 'AGENT_GUIDE.md',
            'docs/contributing/license-status.md': 'LICENSE_STATUS.md',
            'LICENSE': 'LICENSE', 'NOTICE': 'NOTICE',
            'THIRD_PARTY_NOTICES.md': 'THIRD_PARTY_NOTICES.md',
            'README.md': 'README.md',
        })
        for tree in ('runtime/src/main/java', 'examples', 'website/snippets', 'std', 'libraries'):
            for source_file in (ROOT / tree).rglob('*.md'):
                archive_paths[source_file.relative_to(ROOT).as_posix()] = source_file.relative_to(ROOT).as_posix()
        (package / 'docs').mkdir()
        for archive_name, source in docs.items():
            shutil.copy2(ROOT / source, package / 'docs' / (archive_name + '.md'))
        notes = ROOT / 'docs/releases' / (tag + '.md')
        if notes.is_file():
            (package / ('RELEASE_NOTES-' + tag + '.md')).write_text(notes.read_text(encoding='utf-8'), encoding='utf-8')
        for archive_name, source in {'LICENSE_STATUS.md': 'docs/contributing/license-status.md', 'LICENSE': 'LICENSE', 'NOTICE': 'NOTICE', 'THIRD_PARTY_NOTICES.md': 'THIRD_PARTY_NOTICES.md'}.items():
            shutil.copy2(ROOT / source, package / archive_name)
        guide = (ROOT / 'docs/tooling/agent-guide.md').read_text(encoding='utf-8')
        for source_path, archive_path in {
            'docs/language/quick-reference.md': 'docs/QUICK_REFERENCE.md',
            'docs/language/feature-status.md': 'docs/FEATURE_STATUS_IMPLEMENTED.md',
            'docs/language/known-limitations.md': 'docs/KNOWN_LIMITATIONS.md',
            'docs/language/numeric-semantics.md': 'docs/NUMERIC_SEMANTICS.md',
            'docs/language/generics.md': 'docs/GENERICS.md',
            'docs/projects/install.md': 'docs/INSTALL.md', 'docs/projects/projects.md': 'docs/PROJECTS.md',
            'docs/projects/dependencies.md': 'docs/DEPENDENCIES.md',
            'docs/projects/standard-library.md': 'docs/STANDARD_LIBRARY.md',
            'docs/tooling/testing.md': 'docs/TESTING.md', 'docs/tooling/formatter.md': 'docs/FORMATTER.md',
            'docs/tooling/diagnostic-codes.md': 'docs/DIAGNOSTIC_CODES.md',
            'docs/jvm/interop.md': 'docs/JVM_INTEROP.md', 'docs/jvm/wrap.md': 'docs/WRAP.md',
            'docs/jvm/conformance.md': 'docs/JVM_CONFORMANCE.md',
        }.items():
            guide = guide.replace(source_path, archive_path)
        (package / 'AGENT_GUIDE.md').write_text(guide, encoding='utf-8')
        (package / 'README.md').write_text(f'''# Sprig {tag} SDK

{maturity_label}, JDK 17+, language v0.8-dev. Supported: Linux/macOS.
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
sprig check website/snippets/tutorial/hello.spr
sprig run website/snippets/tutorial/hello.spr
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
        archive_sources = {target: source for source, target in archive_paths.items()}
        archive_sources['RELEASE_NOTES-' + tag + '.md'] = 'docs/releases/' + tag + '.md'
        archive_sources['INSTALL.md'] = 'docs/projects/install.md'
        for markdown in package.rglob('*.md'):
            archive_target = markdown.relative_to(package).as_posix()
            source = archive_sources.get(archive_target, archive_target)
            if not (ROOT / source).is_file():
                continue
            contents = rewrite_archive_links(markdown.read_text(encoding='utf-8'), source,
                                             archive_target, archive_paths, package)
            markdown.write_text(contents, encoding='utf-8')
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
