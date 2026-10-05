#!/usr/bin/env python3
"""Contract-derived adversarial cases; independently asserted outputs/rejection stages."""
from pathlib import Path
import json
import subprocess
import sys
ROOT = Path(__file__).resolve().parents[3]
SPRIG = ROOT / 'bin' / ('sprig.cmd' if sys.platform == 'win32' else 'sprig')
CASES = Path(__file__).parent / 'semantics'

def invoke(command, path):
    p = subprocess.run([str(SPRIG), command, str(path), '--json'], capture_output=True, text=True, timeout=40)
    assert not p.stderr, (path.name, p.stderr)
    j = json.loads(p.stdout)
    assert j['exitCode'] == p.returncode, (path.name, j)
    return p.returncode, j

NEGATIVE = {
    'finally_missing_return': 'SPR-FLOW-MISSING-RETURN', 'finally_unreachable': 'SPR-FLOW-UNREACHABLE',
    # `let a = b` before `let b = a` is now rejected at its root cause: top-level
    # code cannot use a binding declared below it, so no inference cycle forms.
    'global_cycle': 'SPR-NAME-FORWARD-REFERENCE', 'global_null_infer': 'SPR-TYPE-INFER',
    'global_lambda_bad': 'SPR-TYPE-MISMATCH', 'global_field_bad': 'SPR-TYPE-MISMATCH',
    'default_earlier_caller': 'SPR-FLOW-THROWS', 'default_lambda_effect': 'SPR-FLOW-THROWS',
    'global_wrong': 'SPR-TYPE-RETURN', 'global_generic': 'SPR-TYPE-RETURN',
    'forward_default_effect': 'SPR-FLOW-THROWS',
    'mutable_loop': 'SPR-TYPE-NULLABLE', 'generic_wrong_nested': 'SPR-TYPE-RETURN',
    'nonnull_java': 'SPR-TYPE-NULLABLE', 'match_scope': 'SPR-NAME-UNRESOLVED',
    'default_self_lambda': 'SPR-NAME-UNRESOLVED',
    'index_assign_map_key': 'SPR-TYPE-MISMATCH',
    'index_assign_map_compound_key': 'SPR-TYPE-MISMATCH',
    'index_assign_map_key_nullable': 'SPR-TYPE-NULLABLE',
    'index_assign_map_key_bool': 'SPR-TYPE-MISMATCH',
    'index_assign_list_index_string': 'SPR-TYPE-MISMATCH',
    'index_assign_list_index_nullable': 'SPR-TYPE-NULLABLE',
    'index_assign_generic_key': 'SPR-TYPE-MISMATCH',
    'index_assign_param_key': 'SPR-TYPE-MISMATCH',
    'short_circuit_null_branch': 'SPR-TYPE-NULLABLE',
}
POSITIVE = {
    'finally_break_override': '42\n', 'finally_continue_override': '42\n',
    'finally_nested': 'inner\nouter\n1\n', 'finally_catch': 'cleanup\n42\n',
    'default_explicit_safe': '42\n', 'default_caught': '42\n',
    'global_valid': '42\n', 'global_null': '1\n',
    'finally_return': 'finally\n1\n', 'finally_exits': 'try\n42\n',
    'generic_nullable': '43\n', 'generic_fn_return': '42\n', 'generic_fn_param': '42\n',
    'fn_match': '42\n', 'compound_index': '1\n3\n', 'null_match': '43\n',
    'map_generic': '43\n', 'context_fn': '42\n', 'float_match': '0.1\n',
    'index_assign_legal': '10\n5\n', 'index_assign_generic_legal': '2\n',
    'index_assign_widening_legal': '4\n',
}

def main():
    failed = []
    for name, code in NEGATIVE.items():
        rc, j = invoke('check', CASES / (name + '.spr'))
        ds = j['diagnostics']
        good = rc == 1 and any(d['code'] == code for d in ds)
        good &= all(d['code'] != 'SPR-JVM-INTERNAL' and d['range'] is not None for d in ds)
        print(('PASS' if good else 'FAIL'), name, [d['code'] for d in ds])
        if not good: failed.append(name)
    for name, expected in POSITIVE.items():
        rc, j = invoke('run', CASES / (name + '.spr'))
        good = rc == 0 and not j['diagnostics'] and j['programOutput'].replace('\r\n', '\n') == expected
        print(('PASS' if good else 'FAIL'), name, j.get('programOutput', j['diagnostics']))
        if not good: failed.append(name)
    print(f'{len(NEGATIVE)+len(POSITIVE)} semantic attacks; {len(failed)} failures')
    return bool(failed)
if __name__ == '__main__':
    raise SystemExit(main())
