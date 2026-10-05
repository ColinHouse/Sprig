#!/usr/bin/env python3
"""Real JDBC persistence + actual HTTP ledger; external networking only via resolve."""
import argparse
import json
import os
from pathlib import Path
import queue
import signal
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
CLI = ROOT / 'bin' / ('sprig.cmd' if os.name == 'nt' else 'sprig')
# java.exe reads its command line in the Windows ANSI code page, so a
# non-ASCII path argument must be one that code page can carry.
NON_ASCII = '世界'
if os.name == 'nt':
    import ctypes
    _code_page = ctypes.windll.kernel32.GetACP()
    for _sample in ('世界', 'café', 'Ωμέγα', 'Привет'):
        try:
            _sample.encode(f'cp{_code_page}')
            NON_ASCII = _sample
            break
        except (LookupError, UnicodeEncodeError):
            pass


def command(project, *args):
    result = subprocess.run([str(CLI), *map(str,args)], cwd=project, text=True,
                            encoding='utf-8', capture_output=True, timeout=120)
    assert result.returncode == 0, (args, result.stdout, result.stderr)
    return result.stdout


class Server:
    def __init__(self, project, database):
        self.proc = subprocess.Popen([str(CLI),'run','--offline','--',str(database),'0'],cwd=project,
                                     stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',
                                     start_new_session=os.name!='nt')
        self.lines = queue.Queue()
        self.transcript = []
        def pump():
            for line in self.proc.stdout:
                self.transcript.append(line)
                self.lines.put(line)
            self.lines.put(None)
        threading.Thread(target=pump,daemon=True).start()
        deadline=time.monotonic()+90
        while time.monotonic()<deadline:
            try:line=self.lines.get(timeout=min(1,max(.01,deadline-time.monotonic())))
            except queue.Empty:continue
            if line is None:break
            if line.startswith('LEDGER_READY '):
                self.url='http://127.0.0.1:'+line.split()[1]
                return
        self.close()
        raise AssertionError('Server did not become ready:\n'+''.join(self.transcript))
    def close(self):
        if self.proc.poll() is None:
            if os.name == 'nt':
                subprocess.run(['taskkill','/PID',str(self.proc.pid),'/T','/F'],capture_output=True)
            else:
                os.killpg(self.proc.pid,signal.SIGTERM)
            try:self.proc.wait(timeout=10)
            except subprocess.TimeoutExpired:
                if os.name == 'nt':
                    subprocess.run(['taskkill','/PID',str(self.proc.pid),'/T','/F'],capture_output=True)
                else:
                    try:os.killpg(self.proc.pid,signal.SIGKILL)
                    except ProcessLookupError:pass
                self.proc.kill();self.proc.wait(timeout=10)
        self.proc.stdout.close()
    def request(self,method,path,value=None,raw=None):
        data=json.dumps(value,ensure_ascii=False).encode('utf-8') if value is not None else raw
        req=urllib.request.Request(self.url+path,data=data,method=method,headers={'Content-Type':'application/json','Origin':'http://localhost:5173'})
        try:response=urllib.request.urlopen(req,timeout=10)
        except urllib.error.HTTPError as error:response=error
        with response:
            body=response.read().decode('utf-8')
            value=json.loads(body) if response.headers.get('Content-Type','').startswith('application/json') else body
            return response.status,value,response.headers


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--offline',action='store_true');args=parser.parse_args()
    with tempfile.TemporaryDirectory(prefix='sprig SQLite integration space ') as temp:
        work=Path(temp)
        for name in ('sprig-sqlite','sprig-web'):
            shutil.copytree(ROOT/'libraries'/name,work/'libraries'/name)
        for name in ('sqlite','ledger'):
            shutil.copytree(ROOT/'examples'/name,work/'examples'/name,ignore=shutil.ignore_patterns('sprig.lock','*.sqlite','*.sqlite-*','sprig-build'))
        sqlite=work/'examples/sqlite'; ledger=work/'examples/ledger'
        for project in (sqlite,ledger):
            resolved=json.loads(command(project,'resolve',*(['--offline'] if args.offline else []),'--json'))
            assert resolved['exitCode']==0
            lock=(project/'sprig.lock').read_bytes()
            command(project,'resolve','--offline')
            assert (project/'sprig.lock').read_bytes()==lock,'offline lock changed'
            checked=json.loads(command(project,'check','--offline','--json'))
            assert checked['diagnostics']==[]
        deps=json.loads(command(sqlite,'deps','--json'))['jvmDependencies']
        assert any(d['group']=='org.xerial' and d['version']=='3.46.1.0' and d['resolved'] for d in deps)
        assert 'lock-version = 5' in (sqlite/'sprig.lock').read_text()
        checked=json.loads(command(sqlite,'check','--offline','--json'))
        classpath=os.pathsep.join([str(ROOT/'build/sprig-compiler.jar'),*checked['environment']['classpath']])
        classes=work/'probe classes';classes.mkdir()
        subprocess.run(['javac','--release','17','-cp',classpath,'-d',str(classes),str(ROOT/'runtime/src/main/java/sprig/runtime/SprigError.java'),*map(str,(ROOT/'runtime/src/main/java/sprig/runtime/sqlite').glob('*.java')),str(ROOT/'tests/sqlite/AdapterProbe.java')],check=True)
        subprocess.run(['java','-Dfile.encoding=UTF-8','-cp',os.pathsep.join([str(classes),classpath]),'AdapterProbe',str(work/f'adapter {NON_ASCII}.sqlite')],check=True)
        path=work/'demo notes.sqlite'
        assert command(sqlite,'run','--offline','--',path)=='persisted notes=1\n'
        assert command(sqlite,'run','--offline','--',path)=='persisted notes=2\n'
        database=work/f'ledger {NON_ASCII}.sqlite';server=None
        try:
            server=Server(ledger,database)
            def request(method,path,value=None,expected=200,raw=None):
                status,body,headers=server.request(method,path,value,raw)
                assert status==expected,(method,path,status,body)
                assert headers.get('Access-Control-Allow-Origin')=='http://localhost:5173'
                return body
            assert request('GET','/api/accounts')==[]
            account=request('POST','/api/accounts',{'name':"Savings'); DROP TABLE accounts;-- 世界"},201)
            category=request('POST','/api/categories',{'name':'Salary'},201)
            assert request('GET','/api/accounts')==[account]
            assert request('GET','/api/categories')==[category]
            payload={'account_id':account['id'],'category_id':category['id'],'amount_minor':125099,'occurred_on':'2026-09-15','memo':'income 世界'}
            first=request('POST','/api/transactions',payload,201)
            second=request('POST','/api/transactions',dict(payload,amount_minor=-2599,memo='expense'),201)
            assert request('GET','/api/transactions')==[first,second]
            totals=request('GET','/api/statistics/monthly?month=2026-09')
            assert totals=={'month':'2026-09','count':2,'net_minor':122500,'income_minor':125099,'expense_minor':-2599}
            assert request('GET','/api/statistics/monthly?month=2026-10')['count']==0
            request('GET','/api/statistics/monthly?month=invalid',expected=400)
            request('POST','/api/accounts',{},400)
            request('POST','/api/accounts',{'name':None},400)
            request('POST','/api/accounts',{'name':False},400)
            request('POST','/api/accounts',{'name':''},400)
            request('POST','/api/accounts',raw=b'{broken',expected=400)
            for changes in ({'account_id':999},{'category_id':999},{'amount_minor':1.5},{'amount_minor':0},{'amount_minor':9223372036854775808},{'occurred_on':'2026-02-30'}):
                request('POST','/api/transactions',dict(payload,**changes),400)
            request('DELETE','/api/transactions/not-an-int',expected=400)
            request('DELETE','/api/transactions/9999',expected=404)
            document=request('GET','/openapi.json')
            assert document['openapi']=='3.0.3'
            assert document['info']['title']=='Sprig SQLite Ledger'
            assert len(document['paths'])==5
            post=document['paths']['/api/transactions']['post']
            assert post['requestBody']['content']['application/json']['schema']['properties']['amount_minor']['type']=='integer'
            assert '201' in post['responses']
            assert 'delete' in document['paths']['/api/transactions/{id}']
            monthly_parameters=document['paths']['/api/statistics/monthly']['get']['parameters']
            assert any(p['name']=='month' and p['in']=='query' and p['required'] for p in monthly_parameters)
            assert 'SwaggerUIBundle' in request('GET','/docs')
            request('GET','/missing',expected=404)
            request('DELETE','/api/transactions/'+str(second['id']),expected=204)
            assert request('GET','/api/transactions')==[first]
            server.close();server=None
            # A second JVM opens the same file: persistence is independent of process memory.
            server=Server(ledger,database)
            assert request('GET','/api/accounts')==[account]
            assert request('GET','/api/transactions')==[first]
            assert request('GET','/api/statistics/monthly?month=2026-09')['net_minor']==125099
        finally:
            if server:server.close()
    print('SQLite Sprig/locked Maven/offline persistence + real ledger HTTP/OpenAPI/restart PASS')


if __name__=='__main__':main()
