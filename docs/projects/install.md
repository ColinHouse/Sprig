# Sprig SDK install and upgrade

The managed SDK installer supports Linux and macOS with JDK 17+ (`java` and
`javac`), `curl`, `unzip`, and `shasum` or `sha256sum`. Windows remains an
experimental preview. The installer does not require Maven.

```sh
curl -fsSL https://raw.githubusercontent.com/ColinHouse/Sprig/main/scripts/install-sprig.sh | sh
```

The installer selects the newest published GitHub release. To select a
specific release, download the script and pass a tag from the project's
[release page](https://github.com/ColinHouse/Sprig/releases):

```sh
curl -fsSLO https://raw.githubusercontent.com/ColinHouse/Sprig/main/scripts/install-sprig.sh
sh install-sprig.sh --version vX.Y.Z
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

## Windows

Windows is an experimental preview without a managed installer. The release
ZIP contains `bin\sprig.cmd`; verify and unpack it, then run that launcher or
put its `bin` directory on `PATH`:

```powershell
$Zip = "sprig-vX.Y.Z-jdk.zip"   # the release asset you downloaded
(Get-FileHash $Zip -Algorithm SHA256).Hash.ToLower() -eq (Get-Content "$Zip.sha256").Split()[0]
Expand-Archive $Zip -DestinationPath .
& ".\$($Zip -replace '\.zip$')\bin\sprig.cmd" version
```

The comparison must print `True`. `sprig upgrade` refuses on Windows; replace
the extracted SDK with a newer verified ZIP instead. Building from source works
too: `py -3 scripts/build.py` creates `bin\sprig.cmd` in the checkout.

`sprig.cmd` forwards arguments exactly as `cmd.exe` passes them. Programs that
start it themselves must quote for `cmd.exe`; a C runtime argument list (for
example Python's `subprocess` list form) leaves `&`, `|` and `^` unquoted. See
[known limitations](../language/known-limitations.md) for the JDK's
command-line code page.
