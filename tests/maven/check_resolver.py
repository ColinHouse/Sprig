#!/usr/bin/env python3
"""Independent Maven fixtures; no network, plugins or expected-output self oracle."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[2]
CLI = ROOT / 'bin' / ('sprig.cmd' if os.name == 'nt' else 'sprig')


def main():
    checks = 0
    with tempfile.TemporaryDirectory(prefix='sprig maven fixture ') as directory:
        base = Path(directory)
        repository = base / 'repository'
        cache = base / 'cache'
        project = base / 'project'
        project.mkdir()
        (project / 'src').mkdir()
        env = dict(os.environ, SPRIG_MAVEN_REPOSITORY=repository.as_uri(), SPRIG_MAVEN_CACHE=str(cache))
        def invoke(command, *args, status=0):
            nonlocal checks
            proc = subprocess.run([str(CLI), command, *map(str, args), '--json'], cwd=project,
                                  env=env, text=True, encoding='utf-8', capture_output=True, timeout=120)
            try: data = json.loads(proc.stdout)
            except Exception: raise AssertionError((command, proc.returncode, proc.stdout, proc.stderr))
            assert proc.returncode == status, (command, data, proc.stderr)
            checks += 1
            return data
        def error(command, code, *args):
            data = invoke(command, *args, status=1)
            assert code in [d['code'] for d in data['diagnostics']], data
        def publish(name, version='1', inner='', packaging='jar', parent=''):
            folder = repository / 'fixture' / name / version
            folder.mkdir(parents=True, exist_ok=True)
            pom = ('<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>'
                   + parent + '<groupId>fixture</groupId><artifactId>' + name + '</artifactId><version>'
                   + version + '</version><packaging>' + packaging + '</packaging>' + inner + '</project>')
            p = folder / f'{name}-{version}.pom'
            p.write_text(pom, encoding='utf-8')
            if packaging == 'jar':
                with zipfile.ZipFile(folder / f'{name}-{version}.jar', 'w') as z:
                    z.writestr('marker.txt', name + version)
            for file in folder.iterdir():
                if file.suffix in ('.pom', '.jar'):
                    file.with_name(file.name + '.sha1').write_text(hashlib.sha1(file.read_bytes()).hexdigest())
        def dep(name, version='1', extra=''):
            return '<dependency><groupId>fixture</groupId><artifactId>' + name + '</artifactId>' + (
                '<version>' + version + '</version>' if version else '') + extra + '</dependency>'
        publish('bom', packaging='pom', inner='<dependencyManagement><dependencies>' + dep('leaf') + '</dependencies></dependencyManagement>')
        publish('parent', packaging='pom', inner='<properties><bridge.version>1</bridge.version></properties><dependencyManagement><dependencies>'
                + dep('bom', extra='<type>pom</type><scope>import</scope>') + '</dependencies></dependencyManagement>')
        for name in ('leaf', 'runtime', 'optional', 'test', 'provided', 'excluded'):
            publish(name)
        publish('conflict', '1'); publish('conflict', '2')
        publish('bridge', inner='<dependencies>' + dep('excluded') + dep('conflict', '2') + '</dependencies>')
        parent = '<parent><groupId>fixture</groupId><artifactId>parent</artifactId><version>1</version><relativePath/></parent>'
        publish('client', parent=parent, inner='<dependencies>' + dep('leaf', '') + dep('runtime', extra='<scope>runtime</scope>')
                + dep('optional', extra='<optional>true</optional>') + dep('test', extra='<scope>test</scope>')
                + dep('provided', extra='<scope>provided</scope>') + dep('conflict', '1')
                + dep('bridge', '${bridge.version}', '<exclusions><exclusion><groupId>fixture</groupId><artifactId>excluded</artifactId></exclusion></exclusions>') + '</dependencies>')
        # Class in direct JAR calls a class in the transitively selected JAR.
        source = base / 'java'; source.mkdir()
        (source / 'Leaf.java').write_text('package fixture; public class Leaf { public static String value() { return "transitive"; } }')
        (source / 'Client.java').write_text('package fixture; public class Client { public static String value() { return Leaf.value(); } }')
        classes = base / 'classes'; classes.mkdir()
        subprocess.run(['javac', '--release', '17', '-d', str(classes), str(source/'Leaf.java'), str(source/'Client.java')], check=True)
        for name, clazz in (('leaf', 'Leaf'), ('client', 'Client')):
            jar = repository / 'fixture' / name / '1' / f'{name}-1.jar'
            with zipfile.ZipFile(jar, 'w') as z:
                z.write(classes/'fixture'/f'{clazz}.class', f'fixture/{clazz}.class')
            jar.with_name(jar.name+'.sha1').write_text(hashlib.sha1(jar.read_bytes()).hexdigest())
        manifest = '[project]\nname="fixture"\n[[jvm]]\ngroup="fixture"\nartifact="client"\nversion="1"\n'
        (project/'sprig.toml').write_text(manifest)
        (project/'src/main.spr').write_text('import fixture.Client\nlet result = Client.value()\nif result != null:\n    print(result)\n')
        invoke('resolve')
        lock = (project/'sprig.lock').read_bytes()
        data = invoke('deps')
        jars = [e for e in data['jvmDependencies'] if e['extension'] == 'jar']
        selected = {(e['artifact'], e['version']) for e in jars}
        assert selected == {('client','1'), ('leaf','1'), ('runtime','1'), ('bridge','1'), ('conflict','1')}, selected
        poms = {e['artifact'] for e in data['jvmDependencies'] if e['extension'] == 'pom'}
        assert {'parent','bom','client'} <= poms, poms
        assert data['jvmEdges'], data
        invoke('resolve')
        assert (project/'sprig.lock').read_bytes() == lock, 'non-deterministic lock'
        invoke('check'); invoke('build', '-d', base/'output with spaces')
        assert invoke('run')['programOutput'] == 'transitive\n'
        assert invoke('api', 'fixture.Client')['className'] == 'fixture.Client'
        doctor = invoke('doctor')
        assert len(doctor['classpath']) == len(jars), doctor
        # A JAR without a valid effective POM must fail, never become direct-only.
        publish('missing-model')
        (repository/'fixture/missing-model/1/missing-model-1.pom').unlink()
        publish('invalid-model', inner='<dependencies>'+dep('leaf', '')+'</dependencies>')
        publish('checksum-bad')
        (repository/'fixture/checksum-bad/1/checksum-bad-1.jar.sha1').write_text('0'*40)
        publish('relocated', inner='<distributionManagement><relocation><artifactId>leaf</artifactId></relocation></distributionManagement>')
        for name in ('missing-model', 'invalid-model', 'checksum-bad', 'relocated'):
            (project/'sprig.toml').write_text(manifest.replace('artifact="client"', 'artifact="'+name+'"'))
            error('resolve', 'SPR-DEP-MAVEN')
        (project/'sprig.toml').write_text(manifest)
        assert (project/'sprig.lock').read_bytes() == lock, 'failed resolution replaced valid lock'
        # Two concurrent resolves sharing one cache must publish identical verified content.
        commands = [[str(CLI),'resolve','--json'] for _ in range(2)]
        processes = [subprocess.Popen(c,cwd=project,env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True) for c in commands]
        for proc in processes:
            stdout, stderr = proc.communicate(timeout=120)
            assert proc.returncode == 0, (stdout,stderr)
        assert (project/'sprig.lock').read_bytes() == lock, 'concurrent resolve changed lock'
        # Re-resolve cannot bless changed Aether staging bytes (JAR or valid XML POM).
        staged_jar = next((cache/'repository').rglob('client-1.jar'))
        original_staged = staged_jar.read_bytes()
        staged_jar.write_bytes(b'broken not a jar')
        error('resolve', 'SPR-DEP-CHECKSUM')
        assert (project/'sprig.lock').read_bytes() == lock
        staged_jar.write_bytes(original_staged)
        staged_pom = next((cache/'repository').rglob('client-1.pom'))
        original_pom = staged_pom.read_bytes()
        staged_pom.write_bytes(original_pom.replace(b'</project>', b'<description>tampered</description></project>'))
        error('resolve', 'SPR-DEP-CHECKSUM')
        assert (project/'sprig.lock').read_bytes() == lock
        staged_pom.write_bytes(original_pom)
        # Same coordinate, different repository bytes: never reuse the old origin.
        mirror = base/'mirror two'
        shutil.copytree(repository, mirror)
        changed = mirror/'fixture/client/1/client-1.jar'
        with zipfile.ZipFile(changed, 'a') as z: z.writestr('mirror.txt', 'different valid bytes')
        changed.with_name(changed.name+'.sha1').write_text(hashlib.sha1(changed.read_bytes()).hexdigest())
        env['SPRIG_MAVEN_REPOSITORY'] = mirror.as_uri()
        invoke('resolve')
        mirrored = invoke('deps')['jvmDependencies']
        client_jar = next(e for e in mirrored if e['artifact']=='client' and e['extension']=='jar')
        assert client_jar['sha256'] == hashlib.sha256(changed.read_bytes()).hexdigest()
        assert client_jar['repository'] == mirror.as_uri()+'/'
        env['SPRIG_MAVEN_REPOSITORY'] = repository.as_uri()
        invoke('resolve')
        assert (project/'sprig.lock').read_bytes() == lock
        # Network/repository unavailable: consumers use only the hash-verified lock cache.
        shutil.rmtree(repository)
        invoke('resolve', '--offline')
        invoke('check', '--offline')
        assert invoke('run', '--offline')['programOutput'] == 'transitive\n'
        invoke('api', 'fixture.Client', '--offline'); invoke('doctor', '--offline')
        artifact = cache/'artifacts'/(jars[0]['sha256']+'.jar')
        original = artifact.read_bytes()
        artifact.write_bytes(b'corruption')
        for command, args in [('check',()), ('build',()), ('run',()), ('api',('fixture.Client',)), ('doctor',())]:
            error(command, 'SPR-DEP-CHECKSUM', *args)
        artifact.write_bytes(original)
        artifact.unlink()
        error('check', 'SPR-DEP-OFFLINE', '--offline')
        artifact.write_bytes(original)
        (project/'sprig.toml').write_text(manifest+'# changed\n')
        error('check', 'SPR-PROJECT-LOCK-STALE')
        for bad in ('[1,2)', 'LATEST', '1-SNAPSHOT', '../escape'):
            (project/'sprig.toml').write_text(manifest.replace('version="1"', 'version="'+bad+'"'))
            error('resolve', 'SPR-PROJECT-MANIFEST')
        (project/'sprig.toml').write_text(manifest)
        # Compiler's own resolver libraries must not leak into application imports.
        outside = base/'standalone.spr'
        outside.write_text('import org.eclipse.aether.RepositorySystem\nprint("bad")\n')
        error('check', 'SPR-JVM-CLASS', outside)
        print(f'Maven resolver: {checks} command checks + independent graph/model/cache assertions passed')

if __name__ == '__main__':
    main()
