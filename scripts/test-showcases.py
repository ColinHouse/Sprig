#!/usr/bin/env python3
"""Exercise complete showcase projects in a checkout or an extracted SDK."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
AUDIT = 'Sprig repository audit\nfiles=3 directories=2 lines=11 code=6 skipped=0\n.md: files=1 lines=3\n.spr: files=1 lines=4\n.java: files=1 lines=4\nfindings=0\n'
OUTLINE = 'Sprig subset source outline\nstatements=4 expressions=4 functions=2 bindings=0\nfunction identity(1 params) -> Int\nfunction combine(2 params) -> Int\nreferences=[value, left, right]\ndiagnostics=0\n'
HTML = '<article id="sprig-the-jvm"><h1>Sprig &amp; The Jvm</h1></article>\n'

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--sdk', type=Path, default=ROOT)
    args = parser.parse_args()
    sdk = args.sdk.resolve()
    launcher = sdk / 'bin' / ('sprig.cmd' if os.name == 'nt' else 'sprig')
    projects = sdk / 'examples' / 'showcases'
    probe = (sdk / 'examples/stage1_frontend_probe/frontend.spr').read_text(encoding='utf-8')
    shared_frontend = (projects / 'source_analyzer/src/frontend.spr').read_text(encoding='utf-8')
    assert shared_frontend == probe[:probe.index('func run_source(')], 'showcase frontend diverged from tested stage-1 probe'

    def run(project, *arguments):
        result = subprocess.run([str(launcher), *arguments], cwd=project, text=True, encoding='utf-8', capture_output=True)
        assert result.returncode == 0, f'{arguments}:\n{result.stdout}\n{result.stderr}'
        return result.stdout

    for name in ('repository_audit', 'source_analyzer', 'maven_slug'):
        project = projects / name
        run(project, 'resolve')
        lock = (project / 'sprig.lock').read_bytes()
        run(project, 'resolve', '--offline')
        assert (project / 'sprig.lock').read_bytes() == lock, f'{name} lock changed offline'
        run(project, 'check', '--offline')
    assert run(projects / 'repository_audit', 'run', '--offline') == AUDIT
    assert run(projects / 'source_analyzer', 'run', '--offline') == OUTLINE
    assert run(projects / 'maven_slug', 'run', '--offline') == HTML
    metadata = json.loads(run(projects / 'maven_slug', 'api', 'org.apache.commons.text.StringEscapeUtils', '--json', '--offline'))
    assert metadata['className'] == 'org.apache.commons.text.StringEscapeUtils'
    assert any(method['name'] == 'escapeHtml4' and method['usableFromSprig'] for method in metadata['staticMethods'])
    assert len(metadata['classpath']) >= 2, 'Maven transitive classpath missing'
    with tempfile.TemporaryDirectory(prefix='sprig showcase space ') as temporary:
        work = Path(temporary)
        output = work / 'report with spaces.json'
        assert run(projects / 'repository_audit', 'run', '--offline', '--', 'fixtures/tree', str(output)) == AUDIT + 'JSON report written\n'
        document = json.loads(output.read_text(encoding='utf-8'))
        assert [entry['path'] for entry in document['files']] == ['README.md', 'src/main.spr', 'src/util.java']
        assert [entry['code'] for entry in document['files']] == [2, 2, 2]
        tree = work / 'tree'
        tree.mkdir()
        (tree / 'test.spr').write_text('\tprint(1) \n\n# comment\n', encoding='utf-8')
        (tree / '.git').mkdir()
        (tree / '.git' / 'ignored.spr').write_text('print(9)\n', encoding='utf-8')
        output2 = work / 'findings.json'
        run(projects / 'repository_audit', 'run', '--offline', '--', str(tree), str(output2))
        document = json.loads(output2.read_text(encoding='utf-8'))
        assert len(document['files']) == 1
        assert document['files'][0]['lines'] == 3
        assert document['files'][0]['code'] == 1
        assert document['findings'] == ['.git: skipped generated or dependency directory', 'test.spr: trailing whitespace on 1 lines', 'test.spr: tab indentation on 1 lines']
        bad = work / 'bad source.spr'
        bad.write_text('print(missing)\n', encoding='utf-8')
        diagnostics = run(projects / 'source_analyzer', 'run', '--offline', '--', str(bad))
        assert 'diagnostics=1\n' in diagnostics and "PROBE-NAME" in diagnostics and "unknown name 'missing'" in diagnostics
        article = work / 'article with spaces.html'
        rendered = run(projects / 'maven_slug', 'run', '--offline', '--', 'hello <JVM> & sprig', str(article))
        expected = '<article id="hello-jvm-sprig"><h1>Hello &lt;jvm&gt; &amp; Sprig</h1></article>\n'
        assert rendered == expected, repr(rendered)
        assert article.read_text(encoding='utf-8') == expected
    print('showcases: 3 project resolve/check/JVM runs, deterministic locks, JSON/findings, subset AST diagnostics, Maven API/transitive/offline passed')

if __name__ == '__main__':
    main()
