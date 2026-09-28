#!/usr/bin/env python3
"""Package, install and dogfood only through the managed SDK PATH launcher."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import threading
import time
import urllib.error
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]


class Releases(BaseHTTPRequestHandler):
    version = ''
    archive = b''

    def do_GET(self):
        if self.path == '/releases':
            body = json.dumps([{'tag_name': self.version, 'draft': False}]).encode()
        elif self.path == f'/download/{self.version}/sprig-{self.version}-jdk.zip':
            body = self.archive
        elif self.path == f'/download/{self.version}/sprig-{self.version}-jdk.zip.sha256':
            name = f'sprig-{self.version}-jdk.zip'
            body = f'{hashlib.sha256(self.archive).hexdigest()}  {name}\n'.encode()
        else:
            self.send_error(404)
            return
        self.send_response(200)
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *_args):
        pass


def installed(*args, cwd, env, ok=True, timeout=120):
    result = subprocess.run(['sprig', *map(str, args)], cwd=cwd, env=env, text=True,
                            encoding='utf-8', capture_output=True, timeout=timeout)
    if ok and result.returncode:
        raise AssertionError((args, result.returncode, result.stdout, result.stderr))
    if not ok and result.returncode == 0:
        raise AssertionError((args, 'expected failure', result.stdout, result.stderr))
    return result


class WebServer:
    def __init__(self, command, project, db, env):
        self.proc = subprocess.Popen(command, cwd=project, env=env, stdout=subprocess.PIPE,
                                     stderr=subprocess.STDOUT, text=True, encoding='utf-8')
        self.output = []
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            line = self.proc.stdout.readline()
            if line:
                self.output.append(line)
                if line.startswith('LEDGER_READY '):
                    self.url = 'http://127.0.0.1:' + line.split()[1]
                    return
            elif self.proc.poll() is not None:
                break
        self.close()
        raise AssertionError('installed SDK ledger did not start: ' + ''.join(self.output))

    def close(self):
        if self.proc.poll() is None:
            self.proc.terminate()
            try:
                self.proc.wait(timeout=10)
            except subprocess.TimeoutExpired:
                self.proc.kill()
                self.proc.wait(timeout=10)
        if self.proc.stdout:
            self.proc.stdout.close()

    def request(self, method, path, value=None):
        data = json.dumps(value).encode() if value is not None else None
        req = urllib.request.Request(self.url + path, data=data, method=method,
                                     headers={'Content-Type': 'application/json'})
        try:
            response = urllib.request.urlopen(req, timeout=10)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            raw = response.read()
            body = json.loads(raw) if raw else None
            return response.status, body


def main():
    build = subprocess.run([os.sys.executable, str(ROOT / 'scripts/package-alpha.py'), '--skip-build'],
                           cwd=ROOT, text=True, capture_output=True, timeout=180)
    if build.returncode:
        raise AssertionError(('package-alpha', build.stdout, build.stderr))
    archive = ROOT / 'dist' / 'sprig-v0.4.0-alpha.1-jdk.zip'
    if not archive.is_file():
        raise AssertionError('package-alpha did not create the SDK ZIP')

    with tempfile.TemporaryDirectory(prefix='sprig installed SDK dogfood ') as temp:
        work = Path(temp)
        Releases.version = 'v0.4.0-alpha.1'
        Releases.archive = archive.read_bytes()
        server = ThreadingHTTPServer(('127.0.0.1', 0), Releases)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        home = work / 'SDK home with spaces'
        fixture_env = dict(os.environ)
        fixture_env.update({'HOME': str(home),
                            'SPRIG_TEST_RELEASES_API_URL': f'http://127.0.0.1:{server.server_port}/releases',
                            'SPRIG_TEST_RELEASE_BASE_URL': f'http://127.0.0.1:{server.server_port}/download'})
        try:
            install = subprocess.run(['sh', str(ROOT / 'scripts/install-sprig.sh'), '--version', Releases.version],
                                     env=fixture_env, text=True, capture_output=True, timeout=120)
            if install.returncode:
                raise AssertionError(('managed install', install.stdout, install.stderr))
            launcher_dir = home / '.local/bin'
            installed_path = str(launcher_dir) + os.pathsep + str(Path(shutil.which('java')).parent) + os.pathsep + '/usr/bin:/bin'
            sdk_home = home / '.sprig'
            shared_cache = Path.home() / '.sprig/maven'
            cache = sdk_home / 'maven'
            if shared_cache.is_dir():
                shutil.copytree(shared_cache, cache)
            env = {**fixture_env, 'PATH': installed_path, 'SPRIG_MAVEN_CACHE': str(cache)}
            version = installed('version', cwd=work, env=env)
            assert '0.4.0-alpha.1' in version.stdout
            capabilities = json.loads(installed('capabilities', '--json', cwd=work, env=env).stdout)
            assert capabilities.get('compilerVersion') or capabilities.get('version')
            assert capabilities['stringSemantics']['positionUnit'] == 'unicode-code-point'
            assert capabilities['stringSemantics']['hasCharType'] is False
            assert capabilities['featureGuidance']['inheritance']['supported'] is False
            check = installed('check', 'website/snippets/tutorial/hello.spr',
                              cwd=sdk_home / 'current', env=env)
            assert check.returncode == 0

            explained = json.loads(installed('explain', 'SPR-TYPE-NULLABLE', '--json', cwd=work, env=env).stdout)
            assert explained['relatedHelp'] == 'nullability'
            assert explained['repair'] == {'kind': 'narrow-before-use', 'machineApplicable': False}
            agents_help = json.loads(installed('help', 'agents', '--json', cwd=work, env=env).stdout)
            assert any('api' in line for line in agents_help['syntax'])

            ledger = sdk_home / 'current/examples/ledger'
            installed('resolve', '--offline', cwd=ledger, env=env)
            installed('check', '--offline', '--json', cwd=ledger, env=env)
            ledger_db = work / 'installed ledger.sqlite'
            web = WebServer(['sprig', 'run', '--offline', '--', str(ledger_db), '0'], ledger, ledger_db, env)
            try:
                status, accounts = web.request('GET', '/api/accounts')
                assert status == 200 and accounts == []
                status, created = web.request('POST', '/api/accounts', {'name': 'installed sdk'})
                assert status == 201 and created['name'] == 'installed sdk'
                assert web.request('GET', '/api/accounts') == (200, [created])
            finally:
                web.close()

            migrations = sdk_home / 'current/examples/sqlite_migrations'
            installed('resolve', '--offline', cwd=migrations, env=env)
            installed('check', '--offline', '--json', cwd=migrations, env=env)
            migration_db = work / 'installed migrations.sqlite'
            first = installed('run', '--offline', '--', migration_db, 'migrations', cwd=migrations, env=env)
            second = installed('run', '--offline', '--', migration_db, 'migrations', cwd=migrations, env=env)
            assert 'applied=2' in first.stdout and 'applied=0' in second.stdout
            assert 'created by migration 001' in first.stdout and 'created by migration 002' in first.stdout

            json_select = sdk_home / 'current/examples/json_select'
            installed('resolve', '--offline', cwd=json_select, env=env)
            installed('check', '--offline', '--json', cwd=json_select, env=env)
            source = work / 'source.json'
            source.write_text('{"account":{"name":"Sprig"},"nothing":null}', encoding='utf-8')
            selected = installed('run', '--offline', '--', '--input', source, '--key=account',
                                 cwd=json_select, env=env)
            assert json.loads(selected.stdout) == {'name': 'Sprig'}
            output = work / 'selected.json'
            installed('run', '--offline', '--', '-i', source, '-k', 'nothing', '-o', output,
                      cwd=json_select, env=env)
            assert json.loads(output.read_text(encoding='utf-8')) is None

            # Module and project introspection must work through the installed SDK.
            module = json.loads(installed('api', '@web/app.spr', '--json', cwd=ledger, env=env).stdout)
            assert module['kind'] == 'sprig-module' and any(d['name'] == 'App' for d in module['declarations'])
            member = json.loads(installed('api', '@web/app.spr', '--member', 'App.get', '--json',
                                          cwd=ledger, env=env).stdout)
            assert member['memberCount'] == 1
            inventory = json.loads(installed('api', '.', '--json', cwd=ledger, env=env).stdout)
            assert inventory['kind'] == 'sprig-project'
            labels = {item['module'] for item in inventory['modules']}
            assert 'main.spr' in labels and '@web/app.spr' in labels
            assert '@web/openapi_helpers.spr' not in labels

            # Sprig-written tools consume installed-SDK metadata end to end.
            tools = sdk_home / 'current/examples/agent_tools'
            installed('resolve', '--offline', cwd=tools, env=env)
            snapshot = work / 'installed_api.json'
            snapshot.write_text(installed('api', '@web/app.spr', '--json', cwd=ledger, env=env).stdout,
                                encoding='utf-8')
            report = installed('run', '--offline', '--bin', 'api-report', '--', snapshot, cwd=tools, env=env)
            assert '# API report:' in report.stdout and 'method get(' in report.stdout
            bad = work / 'installed_bad.spr'
            bad.write_text('let value: String? = null\nprint(value.length())\n', encoding='utf-8')
            diagnostics = installed('check', bad, '--json', cwd=work, env=env, ok=False)
            diagnostics_file = work / 'installed_diagnostics.json'
            diagnostics_file.write_text(diagnostics.stdout, encoding='utf-8')
            summary = installed('run', '--offline', '--bin', 'diag-summary', '--', diagnostics_file,
                                cwd=tools, env=env)
            assert 'SPR-TYPE-NULLABLE: 1' in summary.stdout

            installed('upgrade', '--check', cwd=work, env=env)
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)
    print('installed SDK PATH-only acceptance: install, capabilities, check, api module/project, agent tools, '
          'ledger HTTP, SQLite migrations, json-select and upgrade --check passed')


if __name__ == '__main__':
    main()
