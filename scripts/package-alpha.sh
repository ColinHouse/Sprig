#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
"$ROOT/scripts/build.sh"
VERSION="v$("$ROOT/bin/sprig" version | awk '{print $2}')"
NAME="sprig-${VERSION}-jdk"
DIST="$ROOT/dist"
ANTLR="$ROOT/tools/antlr-4.13.2-complete.jar"
[[ -f "$ANTLR" ]] || { echo "Missing ANTLR dependency: $ANTLR" >&2; exit 2; }
if [[ -n "${SPRIG_PACKAGE_VERSION:-}" && "$SPRIG_PACKAGE_VERSION" != "$VERSION" ]]; then
  echo "Tag $SPRIG_PACKAGE_VERSION does not match compiler $VERSION" >&2
  exit 2
fi
mkdir -p "$DIST"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/sprig-package.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT
PKG="$WORK/$NAME"
mkdir -p "$PKG/bin" "$PKG/lib" "$PKG/examples" "$PKG/docs" \
  "$PKG/runtime/src/main/java" "$PKG/website/snippets" "$PKG/tests/visitor"

cp "$ROOT/build/sprig-compiler.jar" "$PKG/lib/"
cp "$ANTLR" "$PKG/lib/antlr-4.13.2-complete.jar"
cp -R "$ROOT/runtime/src/main/java/sprig" "$PKG/runtime/src/main/java/"
cp -R "$ROOT/examples/." "$PKG/examples/"
cp -R "$ROOT/website/snippets/." "$PKG/website/snippets/"
cp "$ROOT/tests/visitor/ast_visitor.spr" "$PKG/tests/visitor/"
cp "$ROOT/docs/releases/RELEASE_NOTES-${VERSION}.md" "$PKG/"
cp "$ROOT/AGENT_GUIDE.md" "$PKG/"
cp "$ROOT/LICENSE" "$ROOT/NOTICE" "$ROOT/LICENSE_STATUS.md" "$PKG/"
cp "$ROOT/THIRD_PARTY_NOTICES.md" "$PKG/"
cp "$ROOT/docs/QUICK_REFERENCE.md" "$ROOT/docs/FEATURE_STATUS_IMPLEMENTED.md" \
  "$ROOT/docs/JVM_INTEROP.md" "$ROOT/docs/NUMERIC_SEMANTICS.md" \
  "$ROOT/docs/DIAGNOSTIC_CODES.md" "$ROOT/docs/KNOWN_LIMITATIONS.md" \
  "$ROOT/docs/HOST_SERVICES.md" "$PKG/docs/"
cat > "$PKG/README.md" <<'EOF'
# Sprig alpha.2 Agent SDK

This package contains the Java stage-0 compiler/runtime and ANTLR 4.13.2. It
compiles Sprig source to Java, invokes `javac`, then runs on the JVM. It is
not self-hosted. Sprig is licensed under Apache-2.0 (`LICENSE`, `NOTICE`);
ANTLR keeps its own BSD license (`THIRD_PARTY_NOTICES.md`).

Start with `AGENT_GUIDE.md` or run `bin/sprig help --json` and
`bin/sprig capabilities --json`. `INSTALL.md` gives the quick start;
`docs/` records numeric rules, JVM boundaries, and limitations.
EOF
{
  echo "Package version: $VERSION"
  echo "Source revision: $(git -C "$ROOT" rev-parse --short HEAD 2>/dev/null || echo 'no Git metadata')"
  if [[ -z "$(git -C "$ROOT" status --porcelain 2>/dev/null)" ]]; then
    echo "Working tree clean: yes"
  else
    echo "Working tree clean: no (development archive; revision alone does not identify all contents)"
  fi
  echo "Build Java:"
  java -version 2>&1
  echo "Compiler JAR SHA-256:"
  (cd "$PKG/lib" && shasum -a 256 sprig-compiler.jar)
  echo "ANTLR JAR SHA-256:"
  (cd "$PKG/lib" && shasum -a 256 antlr-4.13.2-complete.jar)
} > "$PKG/BUILD_INFO.txt"
cat > "$PKG/LEGAL_STATUS.txt" <<'EOF'
Sprig project license: Apache License 2.0 (see LICENSE and NOTICE).
The bundled ANTLR dependency's BSD license is reproduced in
THIRD_PARTY_NOTICES.md. The project icon is owner-supplied; see the notice
file for the recorded provenance caveat.
EOF
cat > "$PKG/INSTALL.md" <<'EOF'
# Install and run

Requires JDK 17 or newer on PATH. It has been run end-to-end on OpenJDK
17.0.19 and 26.0.1.

1. Extract this archive.
2. Run `bin/sprig version`.
3. Run `bin/sprig check examples/hello.spr`.
4. Run `bin/sprig run examples/hello.spr`.
5. Run `bin/sprig capabilities --json` and `bin/sprig help language --json`.

To emit Java and class files, run:
`bin/sprig build examples/hello.spr -d build/hello`.

The archive contains the compiler/runtime and ANTLR dependency; it does not
contain a JDK. `bin/sprig api java.time.LocalDate --json` inspects JVM APIs.
Read `docs/JVM_INTEROP.md` and `docs/KNOWN_LIMITATIONS.md` before relying on
third-party calls.
EOF
cat > "$PKG/bin/sprig" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec java -cp "$HERE/lib/sprig-compiler.jar:$HERE/lib/antlr-4.13.2-complete.jar" \
  -Dsprig.home="$HERE" sprig.compiler.cli.Main "$@"
EOF
chmod +x "$PKG/bin/sprig"

ARCHIVE="$DIST/$NAME.zip"
rm -f "$ARCHIVE" "$ARCHIVE.sha256"
(cd "$WORK" && zip -qr "$ARCHIVE" "$NAME")
(cd "$DIST" && shasum -a 256 "$NAME.zip" > "$NAME.zip.sha256")
echo "Created $ARCHIVE"
cat "$ARCHIVE.sha256"
