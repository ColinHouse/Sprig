"""Explicit facade acceptance, including real Java compilation and API metadata."""
import json, os, pathlib, subprocess, tempfile
ROOT=pathlib.Path(__file__).resolve().parents[2]
def invoke(*args, cwd=ROOT):
    return subprocess.run([str(ROOT/'bin'/('sprig.cmd' if os.name=='nt' else 'sprig')),*map(str,args)],capture_output=True,text=True,timeout=60,cwd=cwd)
def main():
    with tempfile.TemporaryDirectory(prefix='sprig-facade-') as d:
        d=pathlib.Path(d)
        legacy=d/'legacy.spr'
        legacy.write_text('let export = 7\nclass Box:\n    let export: Int\nfunc identity(export: Int) -> Int:\n    return export\nprint(identity(export) + Box(export=2).export)\n')
        result=invoke('run',legacy)
        assert result.returncode==0 and result.stdout=='9\n',result.stdout+result.stderr
        (d/'core.spr').write_text('func answer() -> Int:\n    return 42\nclass Box:\n    let value: Int\nenum Flag:\n    On\n    Off\nvariant Option:\n    Some(value: Int)\n    None\nvar counter = 7\n')
        with (d/'core.spr').open('a') as core:
            core.write('generic T:\n    variant Envelope:\n        Ok(value: T)\n        Err(message: String)\n')
        facade='import "./core.spr" as core\n\n'+''.join('export core.'+name+'\n' for name in ['answer','Box','Flag','Option','counter','Envelope'])
        (d/'public.spr').write_text(facade)
        (d/'middle.spr').write_text('import "./public.spr" as public\nexport public.answer\nexport public.Box\n')
        (d/'main.spr').write_text('import "./public.spr" as public\nimport "./middle.spr" as middle\nprint(public.answer())\nprint(middle.Box(value=3).value)\nprint(public.counter)\npublic.counter = 8\nprint(public.counter)\nlet f: public.Flag = public.Flag.On\nlet o: public.Option = public.Option.Some(value=2)\nmatch o:\n    case public.Option.Some as some:\n        print(some.value)\n    case public.Option.None:\n        print(0)\n')
        with (d/'main.spr').open('a') as main:
            main.write('let envelope: public.Envelope[Int] = public.Envelope[Int].Ok(value=21)\nlet computed = match envelope:\n    case public.Envelope.Ok as ok:\n        ok.value * 2\n    case public.Envelope.Err:\n        0\nprint(computed)\n')
        result=invoke('run',d/'main.spr')
        assert result.returncode==0 and result.stdout=='42\n3\n7\n8\n2\n42\n', result.stdout+result.stderr
        api=invoke('api',d/'public.spr','--json')
        assert api.returncode==0, api.stdout+api.stderr
        data=json.loads(api.stdout)
        entries=data['declarations']+data['variables']
        assert {e['name'] for e in entries}=={'answer','Box','Flag','Option','counter','Envelope'}, entries
        assert all(e['reexported'] and e['originModule']=='./core.spr' for e in entries)
        assert invoke('api',d/'public.spr','--member','Box.value','--json').returncode==0
        (d/'other.spr').write_text('func answer() -> Int:\n    return 0\n')
        for code, source in [
            ('SPR-MODULE-EXPORT','import "./core.spr" as c\nimport "./other.spr" as o\nexport c.answer\nexport o.answer\n'),
            ('SPR-MODULE-EXPORT','import "./core.spr" as c\nimport "./middle.spr" as m\nexport c.answer\nexport m.answer\n'),
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
        (d/'extra.spr').write_text('func greeting() -> String:\n    return "hello"\n')
        (d/'multi.spr').write_text('import "./core.spr" as core\nimport "./extra.spr" as extra\nexport core.answer\nexport extra.greeting\n')
        (d/'multi_main.spr').write_text('import "./multi.spr" as m\nprint(m.answer())\nprint(m.greeting())\n')
        result=invoke('run',d/'multi_main.spr')
        assert result.returncode==0 and result.stdout=='42\nhello\n',result.stdout+result.stderr
        # A bundled module is named by its import: a path from the facade to the SDK differs per machine.
        (d/'std_facade.spr').write_text('import "@std/text.spr" as text\nimport "./core.spr" as core\nexport text.trim\nexport core.answer\n')
        (d/'std_main.spr').write_text('import "./std_facade.spr" as facade\nprint("[" + facade.trim("  a ") + "]")\n')
        result=invoke('run',d/'std_main.spr')
        assert result.returncode==0 and result.stdout=='[a]\n',result.stdout+result.stderr
        api=invoke('api',d/'std_facade.spr','--json')
        assert api.returncode==0, api.stdout+api.stderr
        origins={e['name']:e['originModule'] for e in json.loads(api.stdout)['declarations']}
        assert origins=={'trim':'@std/text.spr','answer':'./core.spr'}, origins
        previous='core'
        for i in range(20):
            name=f'chain{i}'
            (d/(name+'.spr')).write_text(f'import "./{previous}.spr" as previous\nexport previous.answer\n')
            previous=name
        (d/'chain_main.spr').write_text(f'import "./{previous}.spr" as public\nprint(public.answer())\n')
        direct=invoke('run',d/'chain_main.spr')
        assert direct.returncode==0 and direct.stdout=='42\n',direct.stdout+direct.stderr
        (d/'direct_main.spr').write_text('import "./core.spr" as core\nprint(core.answer())\n')
        defining=invoke('run',d/'direct_main.spr')
        assert (defining.returncode,defining.stdout,defining.stderr)==(direct.returncode,direct.stdout,direct.stderr)
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
