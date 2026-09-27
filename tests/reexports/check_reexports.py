"""Explicit facade acceptance, including real Java compilation and API metadata."""
import json, pathlib, subprocess, tempfile
ROOT=pathlib.Path(__file__).resolve().parents[2]
def invoke(*args, cwd=ROOT):
    return subprocess.run([str(ROOT/'bin/sprig'),*map(str,args)],capture_output=True,text=True,timeout=60,cwd=cwd)
def main():
    with tempfile.TemporaryDirectory(prefix='sprig-facade-') as d:
        d=pathlib.Path(d)
        (d/'core.spr').write_text('func answer() -> Int:\n    return 42\nclass Box:\n    let value: Int\nenum Flag:\n    On\n    Off\nvariant Option:\n    Some(value: Int)\n    None\nvar counter = 7\n')
        facade='import "./core.spr" as core\n\n'+''.join('export core.'+name+'\n' for name in ['answer','Box','Flag','Option','counter'])
        (d/'public.spr').write_text(facade)
        (d/'middle.spr').write_text('import "./public.spr" as public\nexport public.answer\nexport public.Box\n')
        (d/'main.spr').write_text('import "./public.spr" as public\nimport "./middle.spr" as middle\nprint(public.answer())\nprint(middle.Box(value=3).value)\nprint(public.counter)\npublic.counter = 8\nprint(public.counter)\nlet f: public.Flag = public.Flag.On\nlet o: public.Option = public.Option.Some(value=2)\nmatch o:\n    case public.Option.Some as some:\n        print(some.value)\n    case public.Option.None:\n        print(0)\n')
        result=invoke('run',d/'main.spr')
        assert result.returncode==0 and result.stdout=='42\n3\n7\n8\n2\n', result.stdout+result.stderr
        api=invoke('api',d/'public.spr','--json')
        assert api.returncode==0, api.stdout+api.stderr
        data=json.loads(api.stdout)
        entries=data['declarations']+data['variables']
        assert {e['name'] for e in entries}=={'answer','Box','Flag','Option','counter'}, entries
        assert all(e['reexported'] and e['originModule']=='./core.spr' for e in entries)
        assert invoke('api',d/'public.spr','--member','Box.value','--json').returncode==0
        (d/'other.spr').write_text('func answer() -> Int:\n    return 0\n')
        for code, source in [
            ('SPR-MODULE-EXPORT','import "./core.spr" as c\nimport "./other.spr" as o\nexport c.answer\nexport o.answer\n'),
            ('SPR-MODULE-EXPORT','import "./core.spr" as c\nexport c.missing\n'),
            ('SPR-MODULE-EXPORT','export missing.answer\n'),
            ('SPR-MODULE-EXPORT','import java.lang.Math as math\nexport math.PI\n'),
            ('SPR-MODULE-EXPORT','import "./core.spr" as c\nexport c.answer\nexport c.answer\n'),
            ('SPR-MODULE-EXPORT','import "./core.spr" as c\nexport c.answer\nfunc answer() -> Int:\n    return 0\n'),
            ('SPR-SYNTAX-ERROR','export *\n'),
            ('SPR-SYNTAX-ERROR','export import "./core.spr"\n'),
            ('SPR-MODULE-EXPORT-ORDER','import "./core.spr" as c\nprint(0)\nexport c.answer\n'),
            ('SPR-MODULE-EXPORT-ORDER','export c.answer\nimport "./core.spr" as c\n'),
        ]:
            (d/'bad.spr').write_text(source)
            result=invoke('check',d/'bad.spr','--json')
            assert result.returncode!=0 and code in result.stdout, (source,result.stdout,result.stderr)
        (d/'unsafe.spr').write_text(facade+'throw Error("API must not execute")\n')
        result=invoke('api',d/'unsafe.spr','--member','answer','--json')
        assert result.returncode==0 and 'SPR-RUNTIME' not in result.stdout, result.stdout+result.stderr
        previous='core'
        for i in range(20):
            name=f'chain{i}'
            (d/(name+'.spr')).write_text(f'import "./{previous}.spr" as previous\nexport previous.answer\n')
            previous=name
        (d/'chain_main.spr').write_text(f'import "./{previous}.spr" as public\nprint(public.answer())\n')
        direct=invoke('run',d/'chain_main.spr')
        assert direct.returncode==0 and direct.stdout=='42\n',direct.stdout+direct.stderr
        # Manifest file exports cannot be bypassed by direct import or API.
        package=d/'package'; consumer=d/'consumer'
        (package/'src').mkdir(parents=True); (consumer/'src').mkdir(parents=True)
        (package/'sprig.toml').write_text('[project]\nname="facade-lib"\nversion="0.1.0"\nentry="src/public.spr"\nexports=["public.spr"]\n')
        (package/'src/internal.spr').write_text('func answer() -> Int:\n    return 42\n')
        (package/'src/public.spr').write_text('import "./internal.spr" as internal\nexport internal.answer\n')
        (consumer/'sprig.toml').write_text('[project]\nname="consumer"\nversion="0.1.0"\nentry="src/main.spr"\n[[dependency]]\nname="pkg"\npath="../package"\n')
        main=consumer/'src/main.spr'; main.write_text('import "@pkg/public.spr" as public\nprint(public.answer())\n')
        result=invoke('resolve','--offline',cwd=consumer)
        assert result.returncode==0,result.stdout+result.stderr
        result=invoke('run',cwd=consumer)
        assert result.returncode==0 and result.stdout=='42\n',result.stdout+result.stderr
        result=invoke('api','@pkg/public.spr','--member','answer','--json',cwd=consumer)
        assert result.returncode==0,result.stdout+result.stderr
        main.write_text('import "@pkg/internal.spr" as internal\nprint(internal.answer())\n')
        result=invoke('check','--json',cwd=consumer)
        assert result.returncode!=0 and 'SPR-PROJECT-NOT-EXPORTED' in result.stdout,result.stdout+result.stderr
        result=invoke('api','@pkg/internal.spr','--json',cwd=consumer)
        assert result.returncode!=0 and 'SPR-PROJECT-NOT-EXPORTED' in result.stdout,result.stdout+result.stderr
        (d/'a.spr').write_text('import "./b.spr" as b\nexport b.answer\n')
        (d/'b.spr').write_text('import "./a.spr" as a\nexport a.answer\n')
        result=invoke('check',d/'a.spr','--json')
        assert result.returncode!=0 and 'SPR-' in result.stdout and 'Circular' in result.stdout
    print('reexports: facade declarations, chains, runtime mutation, API origins/member lookup, diagnostics and cycles passed')
if __name__=='__main__':main()
