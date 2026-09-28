#!/usr/bin/env python3
"""Hostile local ZIP inputs must not replace a previously usable installation."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import hashlib
import io
import os
import stat
import subprocess
import tempfile
import threading
import warnings
import zipfile

ROOT = Path(__file__).resolve().parents[3]
TAG = 'v9.8.7-audit'
PREFIX = f'sprig-{TAG}-jdk/'
SCRIPT = b'#!/bin/sh\n: > "$SPRIG_AUDIT_SMOKE_MARKER"\necho sprig-compiler 9.8.7-audit\n'

def archive(entries):
    out = io.BytesIO()
    with warnings.catch_warnings():
        warnings.simplefilter('ignore', UserWarning)
        with zipfile.ZipFile(out, 'w') as z:
            for name, content, mode in entries:
                entry = zipfile.ZipInfo(name); entry.external_attr = mode << 16
                z.writestr(entry, content)
    return out.getvalue()

class Handler(BaseHTTPRequestHandler):
    payload = b''
    digest = ''
    def do_GET(self):
        data = (self.digest + '  sprig-' + TAG + '-jdk.zip\n').encode() if self.path.endswith('.sha256') else self.payload
        self.send_response(200); self.end_headers(); self.wfile.write(data)
    def log_message(self, *_): pass

def main():
    if os.name == 'nt':
        print('SKIP managed installer: documented Linux/macOS only'); return
    regular = stat.S_IFREG | 0o755
    safe = (PREFIX+'bin/sprig', SCRIPT, regular)
    cases = {
        'duplicate': archive([safe, safe]),
        'traversal': archive([safe, (PREFIX+'../outside', b'bad', regular)]),
        'symlink': archive([safe, (PREFIX+'link', b'../../outside', stat.S_IFLNK|0o777)]),
        'partial': archive([safe])[:35],
        'wrong-root': archive([('other/bin/sprig', SCRIPT, regular)]),
        'smoke-fails': archive([(PREFIX+'bin/sprig', b'#!/bin/sh\nexit 1\n', regular)]),
        'wrong-version': archive([(PREFIX+'bin/sprig', b'#!/bin/sh\necho sprig-compiler 0.0.0\n', regular)]),
        'checksum': archive([safe]),
    }
    with tempfile.TemporaryDirectory(prefix='sprig-install-attacks-') as temporary:
        temp=Path(temporary); home=temp/'home'; versions=home/'.sprig/versions'; old=versions/'old'
        (old/'bin').mkdir(parents=True); (old/'bin/sprig').write_text('#!/bin/sh\necho previous-sdk\n'); (old/'bin/sprig').chmod(0o755)
        current=home/'.sprig/current'; current.symlink_to('versions/old')
        outside=temp/'outside-sentinel'; outside.write_text('keep me')
        cases = {
            'metadata-symlink': archive([safe, (PREFIX+'sprig-install.json', str(outside).encode(), stat.S_IFLNK|0o777)]),
            'directory-symlink': archive([safe, (PREFIX+'linked', str(temp).encode(), stat.S_IFLNK|0o777),
                                          (PREFIX+'linked/outside-sentinel', b'bad', regular)]),
            'absolute': archive([safe, (str(outside), b'bad', regular)]),
            'dot-alias': archive([safe, (PREFIX+'bin/./sprig', SCRIPT, regular)]),
            **cases,
        }
        marker=temp/'smoke-marker'
        server=ThreadingHTTPServer(('127.0.0.1',0),Handler)
        thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
        try:
            env=dict(os.environ,HOME=str(home),SPRIG_TEST_RELEASE_BASE_URL=f'http://127.0.0.1:{server.server_port}',
                     SPRIG_AUDIT_SMOKE_MARKER=str(marker))
            for name,payload in cases.items():
                Handler.payload=payload;Handler.digest=hashlib.sha256(payload).hexdigest() if name!='checksum' else '0'*64
                before=sorted(str(p.relative_to(old)) for p in old.rglob('*'))
                r=subprocess.run(['sh',str(ROOT/'scripts/install-sprig.sh'),'--version',TAG],env=env,cwd=temp,capture_output=True,text=True,timeout=35)
                assert outside.read_text() == 'keep me', (name, 'archive symlink overwrote external sentinel', outside.read_text())
                assert not marker.exists(), (name, 'unsafe archive reached SDK smoke execution')
                assert r.returncode != 0, (name,r.stdout,r.stderr)
                assert os.readlink(current)=='versions/old' and not (versions/TAG).exists(), (name,r.stderr)
                assert not list(versions.glob('.install.*')), (name, 'staging directory was retained')
                assert not (home/'.local/bin/sprig').exists(), (name, 'failed install created a launcher')
                assert before==sorted(str(p.relative_to(old)) for p in old.rglob('*'))
                assert subprocess.check_output([str(current/'bin/sprig')],text=True)=='previous-sdk\n'
                assert not (versions/'outside').exists(), name
                print('PASS',name,flush=True)
        finally:
            server.shutdown();server.server_close();thread.join()
    print(f'{len(cases)} hostile install inputs rejected; previous SDK preserved and executable')
if __name__=='__main__': main()
