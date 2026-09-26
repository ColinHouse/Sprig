#!/usr/bin/env bash
# Same canonical gates on Unix and Windows; no second test inventory.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec python3 "$ROOT/scripts/test.py" "$@"
