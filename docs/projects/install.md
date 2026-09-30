# Sprig SDK install and upgrade

The managed SDK installer supports Linux and macOS with JDK 17+ (`java` and
`javac`), `curl`, `unzip`, and `shasum` or `sha256sum`. Windows remains an
experimental preview. The installer does not require Maven.

```sh
curl -fsSL https://raw.githubusercontent.com/ColinHouse/Sprig/main/scripts/install-sprig.sh | sh
```

The installer selects the newest published GitHub release. To select a
specific release, download the script and pass its tag:

```sh
curl -fsSLO https://raw.githubusercontent.com/ColinHouse/Sprig/main/scripts/install-sprig.sh
sh install-sprig.sh --version v0.4.0-alpha.1
```

It downloads the release ZIP and its `.sha256` asset from the fixed Sprig
GitHub release host, checks the digest, extracts to a staging directory, runs
`sprig version`, checks that version against the requested tag, then switches
the active symlink. A failed download, digest or smoke check leaves the
previous active version in place. The SDK files are installed under
`~/.sprig/versions/<tag>/`; `~/.sprig/current` points to the active version;
`~/.local/bin/sprig` launches it. Older SDK versions are retained. The installer
does not edit shell startup files. If needed, add its launcher directory to
your current shell:

```sh
export PATH="$HOME/.local/bin:$PATH"
```

The launcher is marked as installer-owned and is not overwritten if an
unrelated file already occupies that path. Installation metadata records the
install kind, release tag and verified ZIP digest. Release asset integrity is
checked against the checksum published alongside the asset; this is
transport/release consistency, not a signed provenance guarantee.

Upgrade a managed install with:

```sh
sprig upgrade --check
sprig upgrade
```

`--check` contacts the GitHub release API and reports whether a release is
available, without downloading the archive. Upgrade validates the managed
installation, verifies the new ZIP checksum, smoke-tests it and atomically
switches the `current` link. It retains old versions and does not modify the
working directory, a project's `sprig.toml`, or `sprig.lock`. Source checkouts
must be updated through Git and rebuilt; an unmanaged extracted SDK ZIP must
be reinstalled through the script before `sprig upgrade` can manage it.

For offline installations, fetch both official release assets yourself and
use a trusted local release mirror only through your own distribution process;
the shipped installer intentionally targets the official project release.
First Maven dependency resolution is a separate project action and may need
network access. See [dependency behavior](../projects/dependencies.md) and the
[Agent guide](../tooling/agent-guide.md).
