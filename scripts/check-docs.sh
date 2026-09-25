#!/usr/bin/env bash
# Documentation gate: executes documented Sprig snippets, then builds the
# VitePress site (including the generated reference pages).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

python3 "$ROOT/tools/verify-doc-snippets.py"
python3 "$ROOT/tools/check-tooling-consistency.py"

if [[ ! -x "$ROOT/website/node_modules/.bin/vitepress" || -n "${DOCS_FORCE_INSTALL:-}" ]]; then
  echo "Installing website dependencies..."
  npm --prefix "$ROOT/website" ci
fi

npm --prefix "$ROOT/website" run docs:build
echo "Documentation checks passed."
