#!/usr/bin/env python3
"""Fresh managed SDK, PATH-only workflow, independently checked SQLite/JSON result."""
from pathlib import Path
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import hashlib
import json
import os
import shutil
import sqlite3
import subprocess
import sys
import tempfile
import threading
ROOT=Path(__file__).resolve().parents[3]
class Releases(BaseHTTPRequestHandler):
    payload=b'';tag=''
    def do_GET(self):
        name=f'sprig-{self.tag}-jdk.zip'
        if self.path.endswith('.sha256'): body=(hashlib.sha256(self.payload).hexdigest()+'  '+name+'\n').encode()
        elif self.path=='/releases': body=json.dumps([{'tag_name':self.tag,'draft':False}],indent=2).encode()
        else: body=self.payload
        self.send_response(200);self.end_headers();self.wfile.write(body)
    def log_message(self,*_):pass

def main():
    if os.name=='nt': print('SKIP managed SDK: documented Linux/macOS only');return
    subprocess.run([sys.executable,str(ROOT/'scripts/package-alpha.py'),'--skip-build'],cwd=ROOT,check=True,stdout=subprocess.DEVNULL)
    version=subprocess.check_output([str(ROOT/'bin/sprig'),'version'],text=True).split()[-1]
    Releases.tag='v'+version;Releases.payload=(ROOT/f'dist/sprig-v{version}-jdk.zip').read_bytes()
    server=ThreadingHTTPServer(('127.0.0.1',0),Releases);thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
    try:
        with tempfile.TemporaryDirectory(prefix='sprig-new-user-') as temporary:
            temp=Path(temporary);home=temp/'home';home.mkdir();project=temp/'application';project.mkdir()
            env=dict(os.environ,HOME=str(home),PATH=str(home/'.local/bin')+os.pathsep+os.environ['PATH'],SPRIG_TEST_RELEASE_BASE_URL=f'http://127.0.0.1:{server.server_port}',SPRIG_TEST_RELEASES_API_URL=f'http://127.0.0.1:{server.server_port}/releases')
            subprocess.run(['sh',str(ROOT/'scripts/install-sprig.sh'),'--version',Releases.tag],env=env,cwd=temp,check=True,stdout=subprocess.DEVNULL,timeout=90)
            def run(*args,cwd=project,ok=True):
                result=subprocess.run(['sprig',*map(str,args)],cwd=cwd,env=env,text=True,encoding='utf-8',capture_output=True,timeout=120)
                assert (result.returncode==0)==ok,(args,result.stdout,result.stderr)
                return result
            run('help'); caps=json.loads(run('capabilities','--json').stdout)
            assert caps, caps
            run('init')
            sdk=home/'.sprig/current'
            manifest=project/'sprig.toml'
            manifest.write_text('[project]\nname="audit"\nsource="src"\nentry="src/main.spr"\n'+''.join(f'\n[[dependency]]\nname="{name}"\npath="{(sdk/"libraries"/folder).as_posix()}"\n' for name,folder in [('cli','sprig-cli'),('sqlite','sprig-sqlite'),('web','sprig-web')]))
            shutil.copy2(Path(__file__).parent/'sdk/main.spr',project/'src/main.spr')
            (project/'numbers.json').write_text('[9007199254740993, -2, 4]\n')
            run('resolve'); lock=(project/'sprig.lock').read_bytes()
            run('project','--json');run('deps','--json');run('api','@sqlite/sqlite.spr','--json')
            snapshot=run('api','@web/web.spr','--json').stdout;(temp/'api.json').write_text(snapshot)
            run('check','--json');run('fmt','.');run('fmt','.','--check');run('build','-d',str(temp/'generated'))
            result=run('run','--json','--','numbers.json');data=json.loads(result.stdout)
            assert data['programOutput'].replace('\r\n','\n')=='200\n9007199254740995\n',data
            assert json.loads((project/'result.json').read_text())==9007199254740995
            with sqlite3.connect(project/'audit.db') as connection:
                assert connection.execute('select value from totals').fetchall()==[(9007199254740995,)]
            run('explain','SPR-NUM-CONVERSION','--json')
            run('upgrade','--check');assert lock==(project/'sprig.lock').read_bytes()
            (project/'numbers.json').write_text('[1,"bad"]')
            bad=json.loads(run('run','--json','--','numbers.json',ok=False).stdout)
            assert any(d['code']=='SPR-RUNTIME-ERROR' for d in bad['diagnostics']),bad
            with sqlite3.connect(project/'audit.db') as connection: assert connection.execute('select count(*) from totals').fetchone()==(1,)
            tools=sdk/'examples/agent_tools';run('resolve',cwd=tools)
            report=run('run','--bin','api-report','--',str(temp/'api.json'),cwd=tools).stdout
            assert 'Response' in report and 'Request' in report,report
            print('Fresh managed SDK: discovery/init/resolve/check/fmt/build/run/project/deps/api/explain/upgrade; CLI+files+JSON+time+web+SQLite and agent report passed')
    finally:server.shutdown();server.server_close();thread.join()
if __name__=='__main__':main()
