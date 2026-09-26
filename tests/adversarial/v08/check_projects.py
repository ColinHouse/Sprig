#!/usr/bin/env python3
"""Adversarial manifest validation, unresolved dependency and discovery tests."""
import json
from pathlib import Path
import subprocess
import tempfile

CLI = Path(__file__).resolve().parents[3] / 'bin/sprig'
cases={
'invalid-source-path':'[project]\nname="a"\nsource="\x00"\n',
'duplicate-root-key':'exports=[]\nexports=[]\n[project]\nname="a"\n',
'blank-entry':'[project]\nname="a"\nentry=""\n',
'unknown-bin-key':'[project]\nname="a"\n[[bin]]\nname="tool"\nentr="src/main.spr"\n',
'duplicate-project':'[project]\nname="a"\n[project]\nname="b"\n',
'unknown-project-field':'[project]\nname="a"\nentr="src/other.spr"\n',
'unknown-table':'[project]\nname="a"\n[dependencies]\nfoo="bar"\n',
'source-array':'[project]\nname="a"\nsource=["src"]\n',
'entry-array':'[project]\nname="a"\nentry=["src/main.spr"]\n',
'duplicate-bins':'[project]\nname="a"\n[[bin]]\nname="x"\nentry="src/main.spr"\n[[bin]]\nname="x"\nentry="src/other.spr"\n',
'duplicate-dependencies':'[project]\nname="a"\n[[dependency]]\nname="x"\npath="../x"\n[[dependency]]\nname="x"\npath="../y"\n',
'no-dependency-source':'[project]\nname="a"\n[[dependency]]\nname="x"\n',
'git-branch-with-path':'[project]\nname="a"\n[[dependency]]\nname="x"\npath="../x"\nbranch="main"\n',
'maven-range':'[project]\nname="a"\n[[jvm]]\ngroup="g"\nartifact="a"\nversion="[1,2)"\n',
'maven-wildcard':'[project]\nname="a"\n[[jvm]]\ngroup="g"\nartifact="a"\nversion="1.+"\n',
'maven-latest':'[project]\nname="a"\n[[jvm]]\ngroup="g"\nartifact="a"\nversion="LATEST"\n',
'bad-string':'[project]\nname="a" "b"\n',
'bad-array':'exports=[,"a",,"b"]\n[project]\nname="a"\n',
}

def invoke(root, command, *args):
    p = subprocess.run([str(CLI), command, *map(str, args), '--json'], cwd=root,
                       text=True, capture_output=True, timeout=30)
    assert not p.stderr, p.stderr
    data = json.loads(p.stdout)
    assert data['exitCode'] == p.returncode, data
    return p.returncode, data


def main():
    failures = []
    with tempfile.TemporaryDirectory(prefix='sprig-manifest-') as tmp:
        root = Path(tmp)
        (root/'src').mkdir()
        source = root/'src/main.spr'
        source.write_text('print("hello")\n')
        unusual = root/'app\nprint("injected")\n#'
        unusual.mkdir()
        status, data = invoke(unusual, 'init')
        assert status == 0, data
        status, data = invoke(unusual, 'resolve')
        assert status == 0, data
        status, data = invoke(unusual, 'run')
        assert status == 0 and data['programOutput'] == 'Hello, Sprig!\n', data
        print('pass init directory name remains data, never source code')
        manifest = root/'sprig.toml' 
        for name, text in cases.items():
            manifest.write_text(text)
            try:
                status, data = invoke(root, 'project')
                assert status != 0 and 'SPR-PROJECT-MANIFEST' in [d['code'] for d in data['diagnostics']], data
                print('pass', name)
            except AssertionError as error:
                failures.append(name)
                print('FAIL', name, error)
        manifest.write_text('[project]\nname="a"\n[[bin]]\nname="a"\nentry="src/main.spr"\n[[bin]]\nname="b"\nentry="src/main.spr"\n')
        status, data = invoke(root, 'resolve')
        assert status == 0, data
        status, data = invoke(root, 'run')
        assert status == 1 and 'SPR-PROJECT-ENTRY' in [d['code'] for d in data['diagnostics']], data
        for name in ('a', 'b'):
            status, data = invoke(root, 'run', '--bin', name)
            assert status == 0 and data['programOutput'] == 'hello\n', data
        print('pass multiple binary selection and ambiguous default')
        standalone = root/'standalone.spr'
        standalone.write_text('print("standalone")\n')
        for kind, declaration, expected, project_code in (
            ('local', '[[dependency]]\nname="missing"\npath="../missing"\n',
             'SPR-DEP-NOT-FOUND', 'SPR-PROJECT-LOCK-MISSING'),
            ('git', '[[dependency]]\nname="remote"\ngit="file:///nonexistent/repo.git"\nbranch="main"\n',
             'SPR-DEP-GIT', 'SPR-PROJECT-LOCK-MISSING'),
            ('maven', '[[jvm]]\ngroup="org.example"\nartifact="missing"\nversion="1.0"\n',
             'SPR-DEP-MAVEN', 'SPR-DEP-MAVEN'),
        ):
            manifest.write_text('[project]\nname="a"\n'+declaration)
            (root/'sprig.lock').unlink(missing_ok=True)
            for mode in ('check', 'build', 'run'):
                try:
                    status, data = invoke(root, mode)
                    assert status != 0 and project_code in [d['code'] for d in data['diagnostics']], data
                    status, data = invoke(root, mode, standalone)
                    assert status == 0, data  # explicit file outside the source root ignores project
                    print('pass', kind, mode, 'explicit-file compatibility')
                except AssertionError as error:
                    failures.append(kind+'-'+mode)
                    print('FAIL', kind, mode, error)
            try:
                status, data = invoke(root, 'resolve')
                assert status == 1 and expected in [d['code'] for d in data['diagnostics']], data
                print('pass', kind, 'resolve reports', expected)
            except AssertionError as error:
                failures.append(kind+'-resolve')
                print('FAIL', kind, 'resolve', error)
    print(f'adversarial projects: {len(cases)+13} cases, {len(failures)} failures')
    return bool(failures)

if __name__ == '__main__':
    raise SystemExit(main())
