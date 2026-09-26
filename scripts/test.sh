#!/usr/bin/env bash
# Full Sprig test suite: syntax, semantics, runtime end-to-end, visitor, JSON.
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SPRIG="$ROOT/bin/sprig"
PASS=0
FAIL=0
FAILED_NAMES=()

pass() { PASS=$((PASS + 1)); }
fail() { FAIL=$((FAIL + 1)); FAILED_NAMES+=("$1"); echo "FAIL: $1"; }

if [[ ! -x "$SPRIG" ]]; then
  echo "Build the compiler first: scripts/build.sh" >&2
  exit 2
fi
if ! "$SPRIG" version >/dev/null 2>&1; then
  echo "The launcher exists but cannot start; rerun scripts/build.sh" >&2
  echo "(on cloud-synced folders, wait for sync to settle first)" >&2
  exit 2
fi

echo "== 1. syntax positives (lexer/layout/parser must accept) =="
for f in "$ROOT"/tests/syntax/positive/*.spr; do
  if "$SPRIG" check --syntax-only "$f" >/dev/null 2>&1; then
    pass
  else
    fail "syntax-positive $(basename "$f")"
  fi
done

echo "== 2. syntax negatives (must be rejected with LEX/SYNTAX diagnostics) =="
for f in "$ROOT"/tests/syntax/negative/*.spr; do
  output=$("$SPRIG" check --syntax-only "$f" 2>&1)
  status=$?
  if [[ $status -ne 0 ]] && grep -qE "SPR-(LEX|SYNTAX)-" <<<"$output"; then
    pass
  else
    fail "syntax-negative $(basename "$f") (status=$status)"
  fi
done

echo "== 3. static semantics (expected diagnostic codes, JSON schema) =="
if python3 "$ROOT/scripts/check_cases.py" "$ROOT"; then
  PASS=$((PASS + $(python3 -c 'import json;print(len(json.load(open("'"$ROOT"'/tests/semantics/cases.json"))))')))
else
  fail "semantic cases"
fi

echo "== 4. runtime end-to-end (compile -> javac -> JVM, golden stdout) =="
for f in "$ROOT"/tests/runtime/*.spr; do
  base="$(basename "$f" .spr)"
  expected="$ROOT/tests/runtime/$base.out"
  [[ -f "$expected" ]] || continue
  actual=$("$SPRIG" run "$f" 2>&1)
  status=$?
  if [[ $status -eq 0 ]] && [[ "$actual" == "$(cat "$expected")" ]]; then
    pass
  else
    fail "runtime $base (status=$status)"
    diff <(echo "$actual") "$expected" | head -8
  fi
done

echo "== 5. AST visitor + bootstrap-slice experiments (written in Sprig) =="
for f in "$ROOT"/tests/visitor/*.spr; do
  base="$(basename "$f" .spr)"
  expected="$ROOT/tests/visitor/$base.out"
  if [[ ! -f "$expected" ]]; then
    continue
  fi
  actual=$("$SPRIG" run "$f" 2>&1)
  status=$?
  if [[ $status -eq 0 ]] && [[ "$actual" == "$(cat "$expected")" ]]; then
    pass
  else
    fail "visitor $base (status=$status)"
    diff <(echo "$actual") "$expected" | head -10
  fi
done
# The exhaustiveness regression must keep failing with the right code.
nonexhaustive_output=$("$SPRIG" check "$ROOT/tests/visitor/nonexhaustive.spr" 2>&1 || true)
if grep -q "SPR-MATCH-NONEXHAUSTIVE" <<<"$nonexhaustive_output"; then
  pass
else
  fail "visitor nonexhaustive.spr should report SPR-MATCH-NONEXHAUSTIVE"
fi

echo "== 6. examples (must compile and run) =="
for f in "$ROOT"/examples/*.spr; do
  if "$SPRIG" run "$f" >/dev/null 2>&1; then
    pass
  else
    fail "example $(basename "$f")"
  fi
done

echo "== 7. compiler diagnostics UX =="
if "$SPRIG" explain SPR-MATCH-NONEXHAUSTIVE >/dev/null 2>&1; then pass; else fail "explain"; fi
json=$("$SPRIG" check --json "$ROOT/tests/semantics/missing_case.spr" 2>/dev/null)
if python3 -c 'import json,sys; json.loads(sys.argv[1])' "$json" >/dev/null 2>&1; then
  pass
else
  fail "check --json output"
fi

echo "== 8. numeric semantics and scientific computing =="
if python3 "$ROOT/tests/numeric/check_numeric.py"; then
  pass
else
  fail "numeric acceptance cases"
fi

echo "== 9. correctness repair regressions =="
if python3 "$ROOT/tests/correctness/check_correctness.py"; then
  PASS=$((PASS + 1))
else
  fail "correctness repair regressions"
fi

echo "== 10. parser recovery and truncation fuzz =="
if python3 "$ROOT/tests/recovery/check_recovery.py"; then
  PASS=$((PASS + 1))
else
  fail "parser recovery regressions"
fi

echo "== 11. independent acceptance regressions =="
if python3 "$ROOT/acceptance/scripts/run_acceptance.py"; then
  PASS=$((PASS + 1))
else
  fail "independent acceptance regressions"
fi
if python3 "$ROOT/acceptance/scripts/json_matrix.py"; then
  PASS=$((PASS + 1))
else
  fail "independent JSON matrix"
fi
if python3 "$ROOT/acceptance/scripts/consistency_matrix.py"; then
  PASS=$((PASS + 1))
else
  fail "independent check/build/run consistency matrix"
fi

echo "== 12. agent tooling and third-party classpath =="
if python3 "$ROOT/tests/agent_tooling/check_tooling.py"; then
  PASS=$((PASS + 1))
else
  fail "agent tooling regressions"
fi

echo "== 13. strict CLI option ownership and process exit contract =="
if python3 "$ROOT/tests/cli_contract/check_cli_contract.py"; then
  PASS=$((PASS + 1))
else
  fail "CLI contract regressions"
fi

echo "== 14. Sprig-written stage-1 frontend probe =="
if python3 "$ROOT/tests/bootstrap/check_probe.py"; then
  PASS=$((PASS + 1))
else
  fail "stage-1 frontend probe"
fi

echo "== 15. project model (sprig.toml, init, discovery, explicit-file priority) =="
if python3 "$ROOT/tests/project/check_project.py"; then
  PASS=$((PASS + 1))
else
  fail "project model regressions"
fi

echo "== 16. dependency resolver (local, exports, lockfile, Git SHA lock, offline) =="
if python3 "$ROOT/tests/project_deps/check_deps.py"; then
  PASS=$((PASS + 1))
else
  fail "dependency resolver regressions"
fi

echo "== 17. independent v0.8 adversarial generics, numerics and manifests =="
if python3 "$ROOT/tests/adversarial/v08/check_generics.py"; then pass; else fail "v0.8 adversarial generics"; fi
if python3 "$ROOT/tests/adversarial/v08/check_projects.py"; then pass; else fail "v0.8 adversarial projects"; fi

echo
echo "== summary: $PASS passed, $FAIL failed =="
if [[ $FAIL -gt 0 ]]; then
  printf 'failed: %s\n' "${FAILED_NAMES[@]}"
  exit 1
fi
