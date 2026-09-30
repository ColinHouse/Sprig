# Sprig documentation site

VitePress source for the official Sprig documentation, built as a bilingual
site: **Simplified Chinese is the default locale at `/`**, English lives under
`/en/`.

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

| Path | Content |
|---|---|
| `index.md`, `guide/`, `examples.md`, `reference/`, `project/` | Chinese pages (default locale). |
| `en/` | English pages. |
| `scripts/sync-reference.mjs` | Generates English reference/project pages from the authoritative root documents into `generated/en/` on every build. |
| `snippets/` | Real `.spr` programs shared by both languages, executed by `scripts/internal/verify-doc-snippets.py`. |
| `public/` | Generated brand assets (see `website/assets/brand/`). |

Only the hand-written Chinese pages and the English `en/` pages are committed;
`generated/` is gitignored and rebuilt by `docs:dev`/`docs:build`. Never edit a
generated copy — edit the root document it comes from.

Reference material (language spec, numeric semantics, diagnostics, grammar) is
authoritative in English only; the Chinese site marks it as such in
`reference/index.md` and links to the English pages.

## Brand assets

`public/logo-round.png`, the favicons, `public/apple-touch-icon.png` and
`public/og-image.png` are produced by `website/assets/brand/generate.py` from
`website/assets/brand/icon-source.png`. The source artwork is only cropped, masked and
resized. See `website/assets/brand/README.md` and `THIRD_PARTY_NOTICES.md` for the
provenance note.

## Base path and deployment

The base path defaults to `/Sprig/` for the GitHub Pages project site at
<https://colinhouse.github.io/Sprig/>. Override it for a custom domain:

```bash
DOCS_BASE=/ npm run docs:build
```

`.github/workflows/docs.yml` deploys to GitHub Pages and derives the base path
from `actions/configure-pages`. The repository must have Pages configured with
**Source: GitHub Actions**.
