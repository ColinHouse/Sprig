#!/bin/sh
# Install one official Sprig SDK into a retained, user-owned version directory.
set -eu

RELEASE_API=https://api.github.com/repos/ColinHouse/Sprig/releases?per_page=100
RELEASE_BASE=https://github.com/ColinHouse/Sprig/releases/download
tag=

fail() { printf 'sprig installer: %s\n' "$1" >&2; exit 1; }
usage() {
    cat <<'EOF'
Usage: install-sprig.sh [--version vX.Y.Z[-prerelease]]

Install an official Linux/macOS Sprig SDK under ~/.sprig and retain every
installed version. Requires JDK 21+, curl, unzip, and a SHA-256 utility.
Without --version, installs the newest published release. If release lookup
fails, pass an exact --version tag to retry without release discovery.
EOF
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --version)
            [ "$#" -ge 2 ] || fail '--version needs a release tag'
            tag=$2
            shift 2
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *) fail "unknown option: $1" ;;
    esac
done

if ! command -v java >/dev/null 2>&1 || ! command -v javac >/dev/null 2>&1; then
    fail 'JDK 21 or newer is required (both java and javac must be on PATH). Install a JDK, then retry.'
fi
javac_version=$(javac -version 2>&1 | sed -E 's/^javac[[:space:]]+//')
javac_major=${javac_version%%.*}
case "$javac_major" in *[!0-9]*|'') fail "cannot determine javac version: $javac_version" ;; esac
if [ "$javac_major" -lt 21 ]; then
    fail "JDK 21 or newer is required; javac reports $javac_version"
fi
java_version=$(java -version 2>&1 | sed -nE '1s/.*version "([0-9]+)(\.[0-9]+)?.*/\1/p; 1s/^openjdk ([0-9]+).*/\1/p')
case "$java_version" in *[!0-9]*|'') fail 'cannot determine java runtime version' ;; esac
if [ "$java_version" -lt 21 ]; then
    fail "JDK 21 or newer is required; java reports version $java_version"
fi

if [ -z "$tag" ]; then
    if [ -n "${SPRIG_TEST_RELEASES_API_URL:-}" ]; then
        case "$SPRIG_TEST_RELEASES_API_URL" in
            http://127.0.0.1:*|http://localhost:*) RELEASE_API=$SPRIG_TEST_RELEASES_API_URL ;;
            *) fail 'test release API override must use a localhost HTTP URL' ;;
        esac
    fi
    release_json=$(curl -fsSL --connect-timeout 10 --max-time 30 "$RELEASE_API") \
        || fail 'could not query official releases; retry later or pass an exact --version tag'
    tag=$(printf '%s\n' "$release_json" | sed -nE 's/^[[:space:]]*"tag_name"[[:space:]]*:[[:space:]]*"(v[^"]+)"[,]?$/\1/p' | sed -n '1p')
    [ -n "$tag" ] || fail 'official release list did not contain a Sprig version tag'
fi
printf '%s\n' "$tag" | grep -Eq '^v[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9][A-Za-z0-9.-]*)?$' \
    || fail "invalid release tag: $tag"

if [ -n "${SPRIG_TEST_RELEASE_BASE_URL:-}" ]; then
    case "$SPRIG_TEST_RELEASE_BASE_URL" in
        http://127.0.0.1:*|http://localhost:*) RELEASE_BASE=$SPRIG_TEST_RELEASE_BASE_URL ;;
        *) fail 'test release override must use a localhost HTTP URL' ;;
    esac
fi

for tool in curl unzip; do
    command -v "$tool" >/dev/null 2>&1 || fail "required installer tool not found: $tool"
done
if command -v shasum >/dev/null 2>&1; then
    hash_file() { shasum -a 256 "$1" | awk '{print $1}'; }
elif command -v sha256sum >/dev/null 2>&1; then
    hash_file() { sha256sum "$1" | awk '{print $1}'; }
else
    fail 'SHA-256 utility not found (install shasum or sha256sum)'
fi

home=${HOME:?HOME must name the installing user home directory}
sdk_root=$home/.sprig
versions=$sdk_root/versions
current=$sdk_root/current
launcher_dir=$home/.local/bin
launcher=$launcher_dir/sprig
mkdir -p "$versions" "$launcher_dir"

if [ -e "$current" ] && [ ! -L "$current" ]; then
    fail "$current exists and is not the managed current symlink; leaving it untouched"
fi
if [ -e "$launcher" ] && ! grep -Fq '# Sprig managed SDK launcher' "$launcher"; then
    fail "$launcher already exists and is not owned by the Sprig installer; leaving it untouched"
fi

work=$(mktemp -d "$versions/.install.XXXXXX") || fail "cannot create staging directory under $versions"
cleanup() {
    rm -rf "$work"
    if [ -n "${current_tmp:-}" ]; then rm -f "$current_tmp"; fi
    if [ -n "${launcher_tmp:-}" ]; then rm -f "$launcher_tmp"; fi
}
trap cleanup EXIT HUP INT TERM

archive=sprig-$tag-jdk.zip
url=$RELEASE_BASE/$tag/$archive
curl -fsSL --connect-timeout 10 --max-time 180 --max-filesize 268435456 "$url" -o "$work/$archive" \
    || fail "could not download official SDK release $tag from $url"
curl -fsSL --connect-timeout 10 --max-time 60 "$url.sha256" -o "$work/$archive.sha256" \
    || fail "could not download the official SHA-256 file for $archive"

expected=$(awk 'NR == 1 {print $1}' "$work/$archive.sha256")
case "$expected" in *[!0-9a-fA-F]*|'') fail 'invalid official SHA-256 file' ;; esac
[ "${#expected}" -eq 64 ] || fail 'invalid official SHA-256 length'
actual=$(hash_file "$work/$archive")
[ "$actual" = "$expected" ] || fail "SHA-256 mismatch for $archive"

entries=$(unzip -Z1 "$work/$archive") || fail 'cannot inspect SDK ZIP entries'
# Reject links before extraction: even a correctly checksummed ZIP could make
# a later entry or our installation metadata write follow a link outside staging.
entry_modes=$(unzip -Z -l "$work/$archive") || fail 'cannot inspect SDK ZIP entry types'
if printf '%s\n' "$entry_modes" | grep -Eq '^l[^[:space:]]{9}[[:space:]]'; then
    fail 'SDK ZIP contains a symbolic link'
fi
prefix=sprig-$tag-jdk/
root_entry=sprig-$tag-jdk
expanded=$(unzip -l "$work/$archive" | awk '$1 ~ /^[0-9]+$/ { total += $1 } END { print total + 0 }')
[ "$expanded" -le 805306368 ] || fail 'SDK ZIP expands beyond the 768 MiB safety limit'
while IFS= read -r entry; do
    case "$entry" in */) entry=${entry%/} ;; esac
    case "$entry" in
        "$root_entry"|"$prefix"*) ;;
        *) fail "SDK ZIP contains an unexpected top-level path: $entry" ;;
    esac
    case "/$entry/" in
        *"/../"*|*"/./"*|*"//"*|*'\'*) fail "SDK ZIP contains an unsafe path: $entry" ;;
    esac
done <<EOF
$entries
EOF
printf '%s\n' "$entries" | awk '{ sub(/\/$/, ""); if (seen[$0]++) exit 1 }' \
    || fail 'SDK ZIP contains a duplicate path'

mkdir "$work/extracted"
unzip -q "$work/$archive" -d "$work/extracted" || fail 'SDK ZIP extraction failed'
# Defense in depth before running the SDK or writing any metadata. Do not follow
# links while walking the extracted tree, including a linked root directory.
extracted_links=$(find "$work/extracted" -type l -print) || fail 'cannot inspect extracted SDK'
[ -z "$extracted_links" ] || fail 'extracted SDK contains a symbolic link'
candidate=$work/extracted/sprig-$tag-jdk
[ -x "$candidate/bin/sprig" ] || fail 'release archive does not contain bin/sprig'
reported=$("$candidate/bin/sprig" version 2>&1) || fail "new SDK smoke test failed: $reported"
reported=$(printf '%s\n' "$reported" | awk 'NR == 1 { print $NF }')
case "$reported" in v*) ;; *) reported=v$reported ;; esac
[ "$reported" = "$tag" ] || fail "new SDK smoke test reported '$reported', expected '$tag'"

target=$versions/$tag
if [ -e "$target" ]; then
    [ -f "$target/sprig-install.json" ] || fail "version directory already exists but is not a managed installation: $target"
    grep -Fq "\"sourceReleaseTag\": \"$tag\"" "$target/sprig-install.json" \
        || fail "existing version directory has conflicting metadata: $target"
    grep -Fq "\"sdkArchiveSha256\": \"$actual\"" "$target/sprig-install.json" \
        || fail "existing version directory has different or unverified SDK contents: $target"
    existing=$("$target/bin/sprig" version 2>&1) || fail "existing version smoke test failed: $existing"
else
    cat > "$candidate/sprig-install.json" <<EOF
{
  "installationKind": "managed",
  "version": "$tag",
  "sourceReleaseTag": "$tag",
  "sdkArchiveSha256": "$actual"
}
EOF
    mv "$candidate" "$target" || fail "cannot publish staged SDK to $target"
fi

if [ ! -e "$launcher" ]; then
    launcher_tmp=$launcher_dir/.sprig-launcher.$$
    cat > "$launcher_tmp" <<'EOF'
#!/bin/sh
# Sprig managed SDK launcher
exec "$HOME/.sprig/current/bin/sprig" "$@"
EOF
    chmod 755 "$launcher_tmp"
    mv "$launcher_tmp" "$launcher" || fail 'could not install the user PATH launcher'
    launcher_tmp=
fi

current_tmp=$sdk_root/.current.$$
ln -s "versions/$tag" "$current_tmp" || fail 'could not prepare the managed current symlink'
case "$(uname -s)" in
    Darwin) mv -fh "$current_tmp" "$current" || fail 'could not atomically switch the managed current symlink' ;;
    Linux) mv -fT "$current_tmp" "$current" || fail 'could not atomically switch the managed current symlink' ;;
    *) fail 'managed install currently supports Linux and macOS only' ;;
esac
current_tmp=

printf 'Installed Sprig %s at %s\n' "$tag" "$target"
case ":${PATH:-}:" in
    *:"$launcher_dir":*) ;;
    *) printf '%s\n' 'Add Sprig to PATH for this shell: export PATH="$HOME/.local/bin:$PATH"' ;;
esac
