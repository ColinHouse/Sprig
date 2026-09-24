# Sprig documentation site

VitePress source for the official Sprig documentation.

## Commands

Node.js 20 or newer is required.

```bash
npm ci                 # reproducible install from package-lock.json
npm run docs:dev       # local development server
npm run docs:build     # production build to .vitepress/dist
npm run docs:preview   # preview the production build
```

From the repository root, `./scripts/check-docs.sh` runs the documented
snippets through the real compiler and then builds the site.

## Page sources

- Hand-written pages: `index.md`, `guide/`, `examples.md`,
  `project/release-status.md`.
- **Generated pages:** `scripts/sync-reference.mjs` copies the authoritative
  root documents (`docs/`, `spec/docs/`, `grammar/README.md`,
  `CONTRIBUTING.md`, `AI_DISCLOSURE.md`, `LICENSE_STATUS.md`,
  `THIRD_PARTY_NOTICES.md`) into `generated/` on every `docs:dev` and
  `docs:build`. `generated/` is gitignored. Never edit the copies; edit the
  root document.
- `snippets/` contains real `.spr` programs included by the guide and
  executed by `tools/verify-doc-snippets.py`.

## Base path and deployment

The base path defaults to `/Sprig/` for the GitHub Pages project site at
<https://colinhouse.github.io/Sprig/>. Override it for a custom domain:

```bash
DOCS_BASE=/ npm run docs:build
```

`.github/workflows/docs.yml` deploys to GitHub Pages and derives the base path
from `actions/configure-pages`. The repository must have Pages configured with
**Source: GitHub Actions**.

## Icon assets

`public/logo.png`, the favicons and `public/og-image.png` are resized copies of
the project icon at the repository root (`icon.png`). The artwork was not
redesigned or recolored; see `THIRD_PARTY_NOTICES.md` for the provenance and
rights items that still need owner confirmation.
