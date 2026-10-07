"""Formatter acceptance: byte safety, trivia, canonical output and JVM equivalence."""
import json, os, pathlib, subprocess, tempfile
ROOT = pathlib.Path(__file__).resolve().parents[2]
CLI = ROOT / 'bin' / ('sprig.cmd' if os.name=='nt' else 'sprig')
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
        stamp=path.stat().st_mtime_ns
        assert invoke('fmt',path).returncode==0 and path.read_text()==expected
        assert path.stat().st_mtime_ns==stamp
        after=invoke('run',path)
        assert (before.returncode,before.stdout,before.stderr)==(after.returncode,after.stdout,after.stderr)
        # Import/declaration boundaries keep the comments' physical association.
        imports = 'import java.lang.Math as Math # first\n# between imports\n# () : export syntax\nimport java.lang.System as System # second\n\n# declaration\nclass Box:\n  let value:Int=1 # field\n# between declarations\nfunc answer()->Int:\n  return 2 # result\n# EOF'
        canonical = 'import java.lang.Math as Math  # first\n# between imports\n# () : export syntax\nimport java.lang.System as System  # second\n\n# declaration\nclass Box:\n    let value: Int = 1  # field\n# between declarations\nfunc answer() -> Int:\n    return 2  # result\n# EOF\n'
        path.write_text(imports)
        result=invoke('fmt',path)
        assert result.returncode==0 and path.read_text()==canonical,result.stdout+result.stderr
        assert invoke('fmt',path).returncode==0 and path.read_text()==canonical
        # Named arguments are written like Python keyword arguments: no spaces
        # around '=' inside a call, while assignments and defaults keep them.
        named = 'class P:\n  let x:Int=1\n  let y:Int=2\nvar p=P(x = 3,y = -4)\np = P(\n  x =5,\n  y= [1,2][0])\nprint(p.x)\n'
        named_canonical = 'class P:\n    let x: Int = 1\n    let y: Int = 2\nvar p = P(x=3, y=-4)\np = P(\n    x=5,\n    y=[1, 2][0])\nprint(p.x)\n'
        path.write_text(named)
        result=invoke('fmt',path)
        assert result.returncode==0 and path.read_text()==named_canonical, repr(path.read_text())+result.stdout+result.stderr
        assert invoke('fmt','--check',path).returncode==0
        # A line continued inside delimiters that starts with + or - after an operand
        # keeps the binary operator's spaces; after a comma or an opening bracket the
        # sign is unary. A comment line in between does not change that.
        continued = 'let a = "x"\nprint(a\n  +"y")\nlet total = (1\n  +2\n  # middle\n  -3)\nlet items = [1,\n  -2]\nprint(total)\n'
        continued_canonical = 'let a = "x"\nprint(a\n    + "y")\nlet total = (1\n    + 2\n    # middle\n    - 3)\nlet items = [1,\n    -2]\nprint(total)\n'
        path.write_text(continued)
        result=invoke('fmt',path)
        assert result.returncode==0 and path.read_text()==continued_canonical, repr(path.read_text())+result.stdout+result.stderr
        assert invoke('fmt','--check',path).returncode==0
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
        good=path.parent/'code/main.spr'
        good.write_text('print(3+4)\n')
        bad=path.parent/'code/bad.spr'; bad.write_text('let bad =\n')
        failed=invoke('fmt',path.parent,'--json')
        assert failed.returncode==1 and json.loads(failed.stdout)['diagnostics']
        assert good.read_text()=='print(3+4)\n' and bad.read_text()=='let bad =\n'
        bad.unlink()
        # Non-format commands must not silently rewrite source.
        assert invoke('check',good).returncode==0
        assert invoke('run',good).stdout=='7\n'
        assert good.read_text()=='print(3+4)\n'
        cp=os.pathsep.join([str(ROOT/'build/sprig-compiler.jar'),str(ROOT/'build/deps/antlr-4.13.2-complete.jar')])
        subprocess.run(['javac','--release','17','-cp',cp,'-d',directory,str(ROOT/'tests/formatter/FormatterCorpus.java')],check=True)
        subprocess.run(['java','-cp',cp+os.pathsep+directory,'FormatterCorpus',*[str(ROOT/r) for r in ['std','libraries','examples','tests']]],check=True,timeout=120)
    print('formatter: canonical trivia, CRLF, EOF, idempotence, runtime, JSON, invalid-file safety and project selection passed')
if __name__=='__main__': main()
