# Sprig brand assets

This directory is the single authoritative source for the project artwork and
the script that derives the web assets.

| File | Purpose |
|---|---|
| `icon-source.png` | Owner-supplied original artwork (1254×1254, opaque RGB). Do not edit. |
| `generate.py` | Derives every website asset from the source. Requires Pillow. |

Run from the repository root:

```bash
python3 assets/brand/generate.py
```

It writes into `website/public/`:

| Output | Derivation |
|---|---|
| `logo-round.png` | 512px circular crop with transparent corners; used by the homepage hero and the README. |
| `logo-mono.png` | 96px "S" monogram; used in the navigation bar, where the detailed artwork is not legible. |
| `favicon-32.png`, `favicon-16.png` | The same monogram at 32/16px in the artwork's slate/cream/rose palette. |
| `apple-touch-icon.png` | 180px rounded-square version of the full artwork. |
| `og-image.png` | 1200×630 social card: circular artwork plus a short wordmark on the project's paper background. |

Design rules followed:

- The artwork is only cropped, masked, resized and composited; the character is
  never redrawn or recolored.
- White background is **not** removed pixel-by-pixel (that would damage the
  light parts of the illustration). The round mark presents it inside a
  deliberate circle instead.
- The monogram is an additional, clearly secondary mark for tiny sizes; the
  illustration remains the project image.

Provenance and the unresolved third-party-mark question are recorded in
`THIRD_PARTY_NOTICES.md`. Rasterizing the social card uses a system font; no
font file is redistributed.
