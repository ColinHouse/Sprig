#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANTLR_JAR="${ANTLR_JAR:-}"
if [[ -z "$ANTLR_JAR" || ! -f "$ANTLR_JAR" ]]; then
  echo 'Set ANTLR_JAR to an existing ANTLR4 complete JAR, e.g. /path/to/antlr-4.13.2-complete.jar' >&2
  exit 2
fi
command -v java >/dev/null && command -v javac >/dev/null || { echo 'JDK required' >&2; exit 2; }
BUILD="$(mktemp -d "${TMPDIR:-/tmp}/sprig-antlr.XXXXXX")"
trap 'rm -rf "$BUILD"' EXIT
(cd "$ROOT/grammar" && java -jar "$ANTLR_JAR" -Dlanguage=Java -visitor -no-listener -o "$BUILD" SprigLexer.g4)
(cd "$ROOT/grammar" && java -jar "$ANTLR_JAR" -Dlanguage=Java -visitor -no-listener -lib "$BUILD" -o "$BUILD" SprigParser.g4)
javac -cp "$ANTLR_JAR" -d "$BUILD" "$BUILD"/*.java \
  "$ROOT/tools/grammar-harness/LayoutTokenSource.java" \
  "$ROOT/tools/grammar-harness/ParseSmoke.java"
count=0
for f in "$ROOT"/tests/syntax/positive/*.spr; do
  java -cp "$BUILD:$ANTLR_JAR" ParseSmoke "$f"
  count=$((count+1))
done
for f in "$ROOT"/tests/syntax/negative/*.spr; do
  if java -cp "$BUILD:$ANTLR_JAR" ParseSmoke "$f" >/dev/null 2>&1; then
    echo "UNEXPECTED ACCEPT: $f" >&2
    exit 1
  fi
  count=$((count+1))
done
echo "GRAMMAR CASES PASS: $count (syntax only; not type/runtime tests)"
