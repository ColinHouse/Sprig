#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="v0.1.0-alpha.1"
NAME="sprig-${VERSION}-jdk"
DIST="$ROOT/dist"
ANTLR="$ROOT/tools/antlr-4.13.2-complete.jar"

"$ROOT/scripts/build.sh"
[[ -f "$ANTLR" ]] || { echo "Missing ANTLR dependency: $ANTLR" >&2; exit 2; }
"$ROOT/bin/sprig" version | grep -F "0.1.0-alpha.1" >/dev/null
mkdir -p "$DIST"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/sprig-package.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT
PKG="$WORK/$NAME"
mkdir -p "$PKG/bin" "$PKG/lib" "$PKG/examples" "$PKG/docs" "$PKG/runtime/src/main/java"

cp "$ROOT/build/sprig-compiler.jar" "$PKG/lib/"
cp "$ANTLR" "$PKG/lib/antlr-4.13.2-complete.jar"
cp -R "$ROOT/runtime/src/main/java/sprig" "$PKG/runtime/src/main/java/"
cp "$ROOT/examples/hello.spr" "$PKG/examples/"
cp "$ROOT/docs/releases/RELEASE_NOTES-v0.1.0-alpha.1.md" "$PKG/"
cp "$ROOT/THIRD_PARTY_NOTICES.md" "$ROOT/LICENSE_STATUS.md" "$PKG/"
cp "$ROOT/docs/NUMERIC_SEMANTICS.md" "$ROOT/docs/KNOWN_LIMITATIONS.md" "$PKG/docs/"
cat > "$PKG/README.md" <<'EOF'
# Sprig v0.1.0-alpha.1 candidate package

This local candidate contains the Java stage-0 compiler/runtime and ANTLR
4.13.2. It compiles Sprig source to Java, invokes `javac`, then runs on the JVM.
It is not self-hosted. The project license has not been selected; do not
redistribute this candidate publicly until the owner records a license.

See `INSTALL.md` for the quick start, `docs/NUMERIC_SEMANTICS.md` for numeric
rules, and `docs/KNOWN_LIMITATIONS.md` for supported boundaries.
EOF
{
  echo "Candidate: $VERSION"
  echo "Source revision: no Git metadata; local candidate only"
  echo "Build Java:"
  java -version 2>&1
  echo "Compiler JAR SHA-256:"
  (cd "$PKG/lib" && shasum -a 256 sprig-compiler.jar)
  echo "ANTLR JAR SHA-256:"
  (cd "$PKG/lib" && shasum -a 256 antlr-4.13.2-complete.jar)
} > "$PKG/BUILD_INFO.txt"
cat > "$PKG/LEGAL_STATUS.txt" <<'EOF'
Sprig project license: NOT YET SELECTED.
This package is a local release candidate for owner review. Do not redistribute
it publicly until the project owner selects and records a license.
The bundled ANTLR dependency's license is reproduced in THIRD_PARTY_NOTICES.md.
EOF
cat > "$PKG/INSTALL.md" <<'EOF'
# Install and run

Requires JDK 26 or newer on PATH for this alpha. It has been run on OpenJDK
26.0.1; Java 17 runtime validation is pending.

1. Extract this archive.
2. Run `bin/sprig version`.
3. Run `bin/sprig check examples/hello.spr`.
4. Run `bin/sprig run examples/hello.spr`.

To emit Java and class files, run:
`bin/sprig build examples/hello.spr -d build/hello`.

The archive contains the compiler/runtime and ANTLR dependency; it does not
contain a JDK. Read `docs/KNOWN_LIMITATIONS.md` before relying on JVM interop.
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
