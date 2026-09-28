#!/usr/bin/env python3
"""Optional mutation gate; never part of concurrent verification. Restores sources."""
from pathlib import Path
import subprocess
import sys
ROOT=Path(__file__).resolve().parents[2]
MUTATIONS=[
    ('comment-loss','compiler/src/main/java/sprig/compiler/front/SourceFormatter.java',
     'out.append(token.getText().stripTrailing());','/* deliberately omit comment */','tests/formatter/check_formatter.py'),
    ('export-collision','compiler/src/main/java/sprig/compiler/sem/NameResolver.java',
     'if (clash != null) {\n                String origin','if (false) {\n                String origin','tests/reexports/check_reexports.py'),
    ('scrutinee-twice','compiler/src/main/java/sprig/compiler/gen/JavaGenerator.java',
     'boolean concrete = match.matchedType instanceof VariantCaseType;',
     'code.append(temp).append(" = ").append(emitExpr(match.scrutinee)).append("; ");\n        boolean concrete = match.matchedType instanceof VariantCaseType;',
     'tests/match_expression/check_match_expression.py'),
    ('branch-result-check','compiler/src/main/java/sprig/compiler/sem/TypeChecker.java',
     'requireAssignable(candidate[0],actual,value.span,Codes.MATCH_RESULT,"match branch result");',
     '/* deliberately accept incompatible branch */','tests/match_expression/check_match_expression.py'),
]
def build():
    result=subprocess.run([sys.executable,'scripts/build.py'],cwd=ROOT,capture_output=True,text=True)
    if result.returncode: raise AssertionError('Mutation must build successfully:\n'+result.stdout+result.stderr)
def main():
    selected=sys.argv[1:] or [m[0] for m in MUTATIONS]
    for name,relative,before,after,test in MUTATIONS:
        if name not in selected:continue
        path=ROOT/relative; original=path.read_text()
        assert original.count(before)==1,(name,'nonunique mutation site')
        try:
            path.write_text(original.replace(before,after))
            build()
            result=subprocess.run([sys.executable,test],cwd=ROOT,capture_output=True,text=True,timeout=180)
            assert result.returncode and 'AssertionError' in result.stderr,(name,result.stdout,result.stderr)
            print(f'DETECTED {name}: {test}',flush=True)
            print(result.stderr[-1000:],flush=True)
        finally:
            path.write_text(original)
            build()
    print('Mutations restored and compiler rebuilt.',flush=True)
if __name__=='__main__':main()
