#!/usr/bin/env python3
"""Independent edge identity, symlink and Git integrity regressions."""
import hashlib, json, os, subprocess, tempfile
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
CLI = ROOT / 'bin/sprig'
count = 0

def cmd(cwd, *args, env=None):
    return subprocess.run([str(CLI), *args, '--json'], cwd=cwd, env=env, capture_output=True, text=True, timeout=90)

def expect(cwd, args, code=None, output=None, env=None):
    global count
    p = cmd(cwd, *args, env=env)
    d = json.loads(p.stdout)
    assert p.returncode == (1 if code else 0), (args, p.stdout, p.stderr)
    if code: assert code in [x['code'] for x in d['diagnostics']], d
    if output is not None: assert d['programOutput'] == output, d
    count += 1
    return d

def write(p, s):
    p.parent.mkdir(parents=True, exist_ok=True); p.write_text(s)

def project(p, deps=(), exports=('lib.spr',)):
    s = '[project]\nname="same-name"\nexports=[' + ','.join(json.dumps(x) for x in exports) + ']\n'
    for name, path in deps: s += f'[[dependency]]\nname="{name}"\npath="{path}"\n'
    write(p/'sprig.toml', s)

def git(p, *a):
    r = subprocess.run(['git', '-C', str(p), *a], capture_output=True, text=True)
    assert r.returncode == 0, r.stderr
    return r.stdout.strip()

def main():
    global count
    with tempfile.TemporaryDirectory(prefix='sprig-cleanup-') as t:
        b = Path(t)
        for x,n in [('ua',11),('ub',22)]:
            project(b/x); write(b/x/'src/lib.spr', f'func v() -> Int:\n    return {n}\n')
        for x,u in [('a','ua'),('b','ub')]:
            project(b/x,[('util','../'+u)])
            write(b/x/'src/lib.spr','import "@util/lib.spr" as u\nfunc v() -> Int:\n    return u.v()\n')
        app=b/'app'; project(app,[('a','../a'),('b','../b')])
        write(app/'src/main.spr','import "@a/lib.spr" as a\nimport "@b/lib.spr" as b\nprint(a.v())\nprint(b.v())\n')
        expect(app,['resolve']); original=(app/'sprig.lock').read_text()
        assert 'root/@a/@util' in original and 'root/@b/@util' in original
        expect(app,['resolve']); assert original==(app/'sprig.lock').read_text(); count+=1
        expect(app,['run','--offline'],output='11\n22\n')
        # Local source edits require no resolve.
        write(b/'ua/src/lib.spr','func v() -> Int:\n    return 33\n')
        expect(app,['run','--offline'],output='33\n22\n')
        # Diamond reuses one physical package through two distinct edges.
        project(b/'b',[('util','../ua')]); expect(app,['resolve'])
        expect(app,['run','--offline'],output='33\n33\n')
        d=expect(app,['deps'])
        assert sum(x['direct'] for x in d['sprigDependencies'])==2
        assert len({x['id'] for x in d['sprigDependencies']})==4
        original=(app/'sprig.lock').read_text()
        mutations=[original.replace('lock-version = 2','lock-version = 1'),
                   original.replace('id = "root/@a"\n','',1),
                   original.replace('id = "root/@b"','id = "root/@a"'),
                   original.replace('owner = "root"','owner = "root/@missing"',1),
                   original.replace('name = "a"','name = "wrong"',1),
                   original.replace('manifest-sha256 = "','manifest-sha256 = "z',1)]
        for s in mutations:
            write(app/'sprig.lock',s); expect(app,['check'],'SPR-PROJECT-LOCK-SCHEMA')
        write(app/'sprig.lock',original)
        # Well-formed wrong digests are stale, rather than silently accepted.
        lines=original.splitlines(); indices=[i for i,x in enumerate(lines) if x.startswith('manifest-sha256')]
        lines[indices[-1]]='manifest-sha256 = "'+'0'*64+'"'
        write(app/'sprig.lock','\n'.join(lines)+'\n'); expect(app,['check'],'SPR-PROJECT-LOCK-STALE')
        write(app/'sprig.lock',original)
        # Edge encoding preserves existing string aliases, including Unicode.
        project(app,[('a.b','../ua'),('数値','../ub')])
        write(app/'src/main.spr','import "@a.b/lib.spr" as a\nimport "@数値/lib.spr" as b\nprint(a.v())\nprint(b.v())\n')
        expect(app,['resolve']); expect(app,['check','--offline'])
        expect(app,['run','--offline'],output='33\n22\n')
        # Logical exports, real path confinement; internal symlinks allowed.
        dep=b/'dep'; project(dep,exports=('lib.spr','escape.spr','link/x.spr','internal.spr'))
        write(dep/'src/lib.spr','func v() -> Int:\n    return 7\n'); write(b/'outside/x.spr','print(999)\n')
        (dep/'src/escape.spr').symlink_to(b/'outside/x.spr')
        (dep/'src/link').symlink_to(b/'outside',target_is_directory=True)
        (dep/'src/internal.spr').symlink_to('lib.spr')
        project(app,[('dep','../dep')]); expect(app,['resolve'])
        for module,code in [('lib.spr',None),('escape.spr','SPR-DEP-NOT-FOUND'),('link/x.spr','SPR-DEP-NOT-FOUND'),('internal.spr',None)]:
            write(app/'src/main.spr',f'import "@dep/{module}" as d\nprint(d.v())\n')
            expect(app,['check'],code)
        # Isolated cache avoids modifying any user's dependencies.
        remote=b/'remote'; project(remote); write(remote/'src/lib.spr','func v() -> Int:\n    return 5\n')
        git(remote,'init','-q','-b','main'); git(remote,'add','.'); git(remote,'-c','user.name=t','-c','user.email=t@t','commit','-qm','A')
        sha=git(remote,'rev-parse','HEAD'); url=remote.as_uri()
        write(app/'sprig.toml',f'[project]\nname="gitapp"\n[[dependency]]\nname="g"\ngit="{url}"\nbranch="main"\n')
        write(app/'src/main.spr','import "@g/lib.spr" as d\nprint(d.v())\n')
        env=os.environ.copy(); env['JAVA_TOOL_OPTIONS']=f'-Duser.home={b}/home'
        # Concurrent resolve against the same empty object/checkout cache.
        ps=[subprocess.Popen([str(CLI),'resolve','--json'],cwd=app,env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True) for _ in range(2)]
        for p in ps:
            out,err=p.communicate(timeout=90); assert p.returncode==0,(out,err); count+=1
        expect(app,['run','--offline'],output='5\n',env=env)
        cache=b/'home/.sprig/git'; checkout=cache/'checkouts'/hashlib.sha256(url.encode()).hexdigest()[:24]/sha
        assert not list(cache.rglob('*.tmp-*')); count+=1
        # Every mutation must be rejected, including ignored/untracked content.
        tests=[('src/lib.spr','func v() -> Int:\n    return 999\n'),('.sprig-revision','0'*40+'\n'),('evil.spr','print(999)\n')]
        for name,s in tests:
            p=checkout/name; old=p.read_bytes() if p.exists() else None; write(p,s)
            expect(app,['run','--offline'],'SPR-DEP-GIT',env=env)
            if old is None: p.unlink()
            else: p.write_bytes(old)
        git(checkout,'update-index','--assume-unchanged','src/lib.spr')
        source=checkout/'src/lib.spr'; old=source.read_bytes(); write(source,'func v() -> Int:\n    return 9\n')
        expect(app,['check','--offline'],'SPR-DEP-GIT',env=env)
        source.write_bytes(old); git(checkout,'update-index','--no-assume-unchanged','src/lib.spr')
        git(checkout,'checkout','--orphan','wrong-head'); git(checkout,'-c','user.name=t','-c','user.email=t@t','commit','-qm','wrong')
        expect(app,['check','--offline'],'SPR-DEP-GIT',env=env)
        git(checkout,'checkout','--detach',sha)
        expect(app,['run','--offline'],output='5\n',env=env)
        original=(app/'sprig.lock').read_text()
        write(app/'sprig.lock',original.replace(f'revision = "{sha}"','revision = "bad"'))
        expect(app,['check'],'SPR-PROJECT-LOCK-SCHEMA',env=env)
        write(app/'sprig.lock',original)
        env_empty=env.copy(); env_empty['JAVA_TOOL_OPTIONS']=f'-Duser.home={b}/emptyhome'
        expect(app,['check','--offline'],'SPR-DEP-OFFLINE',env=env_empty)
        # Project inspection must never reveal credentials from declarations.
        write(app/'sprig.toml','[project]\nname="credential-test"\n[[dependency]]\nname="g"\ngit="https://user:secret@example.invalid/repo.git"\n')
        p=cmd(app,'project'); assert 'secret' not in p.stdout and 'secret' not in p.stderr; count+=1
    print(f'resolver cleanup: {count} checks passed')
if __name__=='__main__': main()
