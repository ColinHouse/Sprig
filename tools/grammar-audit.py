#!/usr/bin/env python3
"""Conservative ANTLR grammar token/rule reference audit; cannot replace antlr4."""
import re
from pathlib import Path
root=Path(__file__).resolve().parents[1]
l=(root/'grammar/SprigLexer.g4').read_text()
p=(root/'grammar/SprigParser.g4').read_text()
lexer_tokens=set(re.findall(r'(?m)^([A-Z][A-Z_0-9]*)\s*:', l))|{'EOF','INDENT','DEDENT'}
parser_rules=set(re.findall(r'(?m)^([a-z][A-Za-z_0-9]*)\s*:',p))
# Strip comments and quoted strings so only grammar references remain.
p_clean=re.sub(r'//[^\n]*|/\*.*?\*/|\'[^\']*\'|"[^"]*"','',p,flags=re.S)
words=set(re.findall(r'\b[A-Za-z][A-Za-z_0-9]*\b',p_clean))
known={'parser','grammar','options','tokenVocab','SprigLexer','SprigParser','EOF'}
missing_upper=sorted(x for x in words if x[0].isupper() and x not in lexer_tokens|known)
missing_lower=sorted(x for x in words if x[0].islower() and x not in parser_rules|known)
assert not missing_upper,('unknown lexer refs:',missing_upper)
assert not missing_lower,('unknown parser rules:',missing_lower)
print(f'GRAMMAR REFERENCE AUDIT PASS: {len(lexer_tokens)} tokens, {len(parser_rules)} parser rules')
print('This is a static reference audit, not ANTLR generation or parse verification.')
