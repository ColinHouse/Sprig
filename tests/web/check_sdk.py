"""Relocated development SDK libraries/projects; run after package-alpha --skip-build."""
import json, os, subprocess, sys, tempfile, zipfile
from pathlib import Path
if len(sys.argv) != 2:
 raise SystemExit('usage: python3 tests/web/check_sdk.py DIST_ARCHIVE.zip')
archive=Path(sys.argv[1]).resolve()
with zipfile.ZipFile(archive) as z:
 names=z.namelist()
 assert any(n.endswith('/libraries/sprig-web/src/app.spr') for n in names)
 assert any(n.endswith('/libraries/sprig-sqlite/src/sqlite.spr') for n in names)
 assert not any(n.endswith('sprig.lock') or '.sqlite' in n or '__pycache__' in n for n in names)
 with tempfile.TemporaryDirectory(prefix='sprig web SDK space ') as tmp:
  z.extractall(tmp)
  sdk=Path(tmp)/names[0].split('/')[0]
  cli=sdk/'bin'/('sprig.cmd' if os.name=='nt' else 'sprig')
  if os.name!='nt':cli.chmod(0o755)
  def run(cwd,*args):
   p=subprocess.run([str(cli),*map(str,args)],cwd=cwd,capture_output=True,text=True,timeout=120)
   assert p.returncode==0,(args,p.stdout,p.stderr)
   return p.stdout
  assert json.loads(run(sdk,'capabilities','--json'))['features']['sourceFunctionTypes']
  for name in ('mini_web','sqlite','ledger'):
   project=sdk/'examples'/name
   run(project,'resolve','--offline')
   run(project,'check','--offline')
  sqlite=sdk/'examples/sqlite';db=Path(tmp)/'SDK database.sqlite'
  assert run(sqlite,'run','--offline','--',db)=='persisted notes=1\n'
  assert run(sqlite,'run','--offline','--',db)=='persisted notes=2\n'
  web=sdk/'examples/mini_web';probe=web/'src/sdk_probe.spr'
  probe.write_text('''import "@web/app.spr" as web
let app = web.App(title="SDK")
app.get("/", fn(req: web.Request) => web.text("SDK", 200))
app.run(0)
print(app.port() > 0)
app.stop()
''')
  assert run(web,'run','src/sdk_probe.spr','--offline')=='true\n'
print('Development SDK: includes libraries, excludes local locks/databases; relocated spaced-path offline projects, SQLite persistence and web lifecycle PASS')
