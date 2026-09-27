"""Formatter acceptance: byte safety, trivia, canonical output and JVM equivalence."""
import json, os, pathlib, subprocess, tempfile
ROOT = pathlib.Path(__file__).resolve().parents[2]
CLI = ROOT / 'bin/sprig'
def invoke(*args):
    return subprocess.run([str(CLI), *map(str,args)], capture_output=True, text=True, timeout=60)
def main():
    with tempfile.TemporaryDirectory(prefix='sprig-fmt-') as directory:
        path = pathlib.Path(directory) / 'main.spr'
        source = '# 模块 # ()\r\n\r\nfunc add(a:Int,b:Int)->Int: # signature\r\n  # body\r\n  return a+b # sum\r\n\r\nprint(add(1,2)) # EOF'
        expected = '# 模块 # ()\n\nfunc add(a: Int, b: Int) -> Int:  # signature\n    # body\n    return a + b  # sum\n\nprint(add(1, 2))  # EOF\n'
        path.write_bytes(source.encode())
        before = invoke('run',path)
        assert before.returncode == 0, before.stderr
        checked = invoke('fmt','--check','--json',path)
        assert checked.returncode == 1, checked.stdout + checked.stderr
        data = json.loads(checked.stdout)
        assert data['command']=='fmt' and data['changedFiles'], data
        assert path.read_bytes()==source.encode()
        result = invoke('fmt',path)
        assert result.returncode==0, result.stdout+result.stderr
        assert path.read_text()==expected, repr(path.read_text())
        assert invoke('fmt','--check',path).returncode==0
        assert invoke('fmt',path).returncode==0 and path.read_text()==expected
        after=invoke('run',path)
        assert (before.returncode,before.stdout,before.stderr)==(after.returncode,after.stdout,after.stderr)
        for source in ['enum F:\n  A\n  B\nlet x = match F.A: # map\n  # branch\n  case F.A:\n    1 # one\n  case F.B:\n    2\n', '', '# only\n# second', 'let x = [1,\n  2] # list\n', 'let x = -1 + +2\n', 'let x = "# a  b" # trailing\n', 'class C:\n  # field\n  let x: Int = 1\n  # method\n  func f() -> Int:\n    # nested\n    return x\n# top EOF' ]:
            path.write_text(source)
            result=invoke('fmt',path)
            assert result.returncode==0, result.stderr
            first=path.read_text()
            assert invoke('fmt',path).returncode==0 and path.read_text()==first
            for comment in [s for s in source.splitlines() if s.startswith('#')]:
                assert comment in first
        for source in ['let x =\n', 'func f() -> Int:\n\treturn 1\n', 'let x = (1\n']:
            path.write_text(source)
            result=invoke('fmt','--json',path)
            assert result.returncode!=0 and json.loads(result.stdout)['diagnostics']
            assert path.read_text()==source
        # Project selection must not format generated or dependency cache files.
        (path.parent/'sprig.toml').write_text('[project]\nname="fmt"\nversion="0.1.0"\nentry="src/main.spr"\n')
        (path.parent/'src').mkdir()
        (path.parent/'src/main.spr').write_text('print(1+2)\n')
        path.unlink()
        assert invoke('fmt',path.parent).returncode==0
        assert (path.parent/'src/main.spr').read_text()=='print(1 + 2)\n'
        (path.parent/'sprig.toml').write_text('[project]\nname="fmt"\nversion="0.1.0"\nsource="code"\nentry="code/main.spr"\n')
        (path.parent/'code').mkdir()
        (path.parent/'code/main.spr').write_text('print(2+3)\n')
        assert invoke('fmt',path.parent).returncode==0
        assert (path.parent/'code/main.spr').read_text()=='print(2 + 3)\n'
        cp=os.pathsep.join([str(ROOT/'build/sprig-compiler.jar'),str(ROOT/'tools/antlr-4.13.2-complete.jar')])
        subprocess.run(['javac','--release','17','-cp',cp,'-d',directory,str(ROOT/'tests/formatter/FormatterCorpus.java')],check=True)
        subprocess.run(['java','-cp',cp+os.pathsep+directory,'FormatterCorpus',*[str(ROOT/r) for r in ['std','libraries','examples','tests','acceptance']]],check=True,timeout=120)
    print('formatter: canonical trivia, CRLF, EOF, idempotence, runtime, JSON, invalid-file safety and project selection passed')
if __name__=='__main__': main()
