#!/usr/bin/env python3
"""Offline local Markdown/image/include paths. VitePress checks route anchors.

Repository mode checks tracked docs plus generated site pages. --root checks
all Markdown in an extracted SDK; network links are intentionally not fetched.
"""
import argparse
from pathlib import Path
import re
import shutil
import subprocess
import sys
from urllib.parse import unquote, urlsplit

parser = argparse.ArgumentParser()
parser.add_argument('--root', type=Path)
args = parser.parse_args()
root = (args.root or Path(__file__).resolve().parents[2]).resolve()
package = args.root is not None
if package:
    files = list(root.rglob('*.md'))
else:
    # Site pages link into /en/reference/..., which website/scripts/sync-reference.mjs
    # generates from docs/ before every docs build. Generate them here as well, so
    # the links are checked against the current docs/ when this script runs on its
    # own (test-affected.py selects it for documentation changes).
    node = shutil.which('node')
    if node is None:
        print('Node.js 20+ is required to generate the reference pages that site links point to.',
              file=sys.stderr)
        raise SystemExit(2)
    subprocess.run([node, str(root / 'website/scripts/sync-reference.mjs')], cwd=root, check=True,
                   stdout=subprocess.DEVNULL)
    files = [root / p for p in subprocess.check_output(
        ['git', 'ls-files', '*.md'], cwd=root, text=True).splitlines() if (root / p).is_file()]
    files += list((root / 'website/generated').rglob('*.md'))
errors = []
count = 0
for path in files:
    text = re.sub(r'^\s*(```|~~~)[^\n]*\n.*?^\s*\1\s*$', '', path.read_text(), flags=re.M | re.S)
    text = re.sub(r"(`+).*?\1", "", text)
    refs = re.findall(r'!?\[[^\]\n]*\]\(\s*<?([^\s)>]+)>?(?:\s+["\'][^\n]*["\'])?\s*\)', text)
    refs += re.findall(r'^\s*\[[^\]]+\]:\s*<?([^\s>]+)', text, flags=re.M)
    refs += re.findall(r'<(?:img|a)\b[^>]*(?:src|href)=["\']([^"\']+)', text)
    refs += re.findall(r'^<<<\s+([^\s#]+)', text, flags=re.M)
    for href in refs:
        url = urlsplit(href)
        if url.scheme or url.netloc or not url.path:
            continue
        target = unquote(url.path)
        count += 1
        if not package and 'website' in path.relative_to(root).parts:
            site = root / 'website'
            if target.startswith('@/'):
                candidates = [site / target[2:]]
            elif target.startswith('/'):
                rel = target.lstrip('/')
                candidates = [site / 'public' / rel, site / rel, site / 'generated' / rel]
                candidates += [p.with_name(p.name + '.md') for p in candidates]
                candidates += [p.with_suffix('.md') for p in candidates]
                candidates += [p / 'index.md' for p in candidates[:3]]
            else:
                candidates = [path.parent / target]
                if not Path(target).suffix:
                    candidates += [(path.parent / target).with_suffix('.md'), path.parent / target / 'index.md']
        else:
            candidates = [root / target.lstrip('/')] if target.startswith('/') else [path.parent / target]
        if not any(p.exists() for p in candidates):
            errors.append(f'{path.relative_to(root)}: missing local target {href}')
if errors:
    raise SystemExit('\n'.join(errors))
print(f'Markdown local links: {len(files)} files, {count} targets passed')
