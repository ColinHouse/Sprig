## Package

- Name:
- Repository and subdirectory:
- Version(s) added, each a tag pinned to a commit (`sprig publish --tag vX.Y.Z` records the commit):
- License (SPDX identifier):
- Owners (GitHub handles allowed to change this entry later):

## Checklist

- [ ] This pull request changes only `registry/packages/NAME.toml`.
- [ ] The entry was written by `sprig publish`, not by hand.
- [ ] The tag exists in the package repository and will not move; a broken release is yanked, never replaced.
- [ ] `sprig resolve` and `sprig check` pass in the package at that tag (the registry workflow repeats this).

Open this template with `?template=registry_submission.md` on the new pull request URL.
