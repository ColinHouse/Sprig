#!/usr/bin/env python3
"""Bootstrap pinned Resolver libraries; never invokes Maven or executes ZIP code."""
import hashlib
import io
import json
import os
from pathlib import Path
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]

def main():
    spec = json.loads((ROOT / 'scripts/internal/resolver-libraries.json').read_text(encoding='utf-8'))
    target = ROOT / 'build/deps/resolver'
    target.mkdir(parents=True, exist_ok=True)
    def valid(name, digest):
        path = target / name
        return path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest() == digest
    if {p.name for p in target.glob('*.jar')} - set(spec['jars']):
        raise RuntimeError('Unexpected resolver JARs; remove unlisted files before building')
    if all(valid(n, d) for n, d in spec['jars'].items()) and (target / 'legal/LICENSE').is_file():
        return
    with urllib.request.urlopen(spec['url'], timeout=60) as response:
        data = response.read()
    if hashlib.sha512(data).hexdigest() != spec['sha512']:
        raise RuntimeError('Apache distribution SHA-512 mismatch')
    archive = zipfile.ZipFile(io.BytesIO(data))
    prefix = spec['distribution'] + '/'
    def write(path, data):
        path.parent.mkdir(parents=True, exist_ok=True)
        fd, temp = tempfile.mkstemp(dir=path.parent)
        try:
            with os.fdopen(fd, 'wb') as stream:
                stream.write(data)
            os.replace(temp, path)
        finally:
            if os.path.exists(temp):
                os.unlink(temp)
    for name, digest in spec['jars'].items():
        if name in spec.get('external', {}):
            with urllib.request.urlopen(spec['external'][name], timeout=60) as response:
                content = response.read()
        else:
            content = archive.read(prefix + 'lib/' + name)
        if hashlib.sha256(content).hexdigest() != digest:
            raise RuntimeError('Pinned library SHA-256 mismatch: ' + name)
        write(target / name, content)
    for entry in archive.namelist():
        if entry in (prefix + 'LICENSE', prefix + 'NOTICE') or entry.endswith('.license'):
            write(target / 'legal' / Path(entry).name, archive.read(entry))
    actual = {p.name for p in target.glob('*.jar')}
    if actual != set(spec['jars']):
        raise RuntimeError('Unexpected resolver JARs; remove unlisted files before building')
    print('Verified Apache Maven Resolver 1.9.24 / model provider 3.9.11 libraries')

if __name__ == '__main__':
    main()
