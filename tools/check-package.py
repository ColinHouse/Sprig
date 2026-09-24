#!/usr/bin/env python3
"""Offline structure/syntax-reference smoke checks; not an ANTLR compiler."""
from pathlib import Path
import json,re,sys
root=Path(__file__).resolve().parents[1]
lexer=(root/'grammar/SprigLexer.g4').read_text()
parser=(root/'grammar/SprigParser.g4').read_text()
needed=['variantDefinition','matchStatement','namedArguments','positionalArguments']
for rule in needed:
    assert re.search(r'(?m)^'+re.escape(rule)+r'\s*:',parser), f'missing rule {rule}'
assert "PIPE:" not in lexer and 'pipeExpression:' not in parser
for dir in ('positive','negative'):
    files=sorted((root/'tests/syntax'/dir).glob('*.spr'))
    assert files,dir
    for f in files:
        f.read_text(encoding='utf-8')
manifest=json.loads((root/'tests/semantics/cases.json').read_text())
for item in manifest:
    assert (root/'tests/semantics'/item['file']).exists()
print('PACKAGE STRUCTURE PASS; syntax + runtime NOT verified by this script')
print('syntax positive:',len(list((root/'tests/syntax/positive').glob('*.spr'))))
print('syntax negative:',len(list((root/'tests/syntax/negative').glob('*.spr'))))
print('future semantic cases:',len(manifest))
