# Third-party notices

This file records third-party components and material rights that affect the
Sprig repository, the runtime archives and the documentation site. It is a
notice list, not the project license. The project license itself is still
unselected; see `LICENSE_STATUS.md`.

## ANTLR 4.13.2 (build and runtime archive)

Sprig's build uses the official ANTLR 4.13.2 complete JAR from Maven Central.
The build script pins its SHA-256 digest. ANTLR is distributed under the BSD
license shown on the [official ANTLR license page](https://www.antlr.org/license.html).
The dependency is fetched during build and is **not** committed to this
repository; the local candidate package (`scripts/package-alpha.sh`) copies it
and includes this notice.

Copyright (c) 2012 Terence Parr and Sam Harwell. All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.
3. Neither the name of the author nor the names of its contributors may be used
   to endorse or promote products derived from this software without specific
   prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

## Documentation site toolchain (`website/`)

The site is built with VitePress and its default theme. The production build
redistributes JavaScript bundles and the Inter webfont. These components keep
their own licenses; they are development/build dependencies, not part of the
compiler or runtime:

| Component | License |
|---|---|
| VitePress | MIT |
| Vue.js | MIT |
| Shiki | MIT |
| MiniSearch (local search) | MIT |
| Inter font (shipped by the theme) | SIL Open Font License 1.1 |

No dependency listed here is modified by this repository.

## Project icon and mascot (`icon.png`)

The icon at the repository root was supplied by the project owner and is used
to derive the site logo, favicons and social preview image by **resizing and
format changes only**. The artwork itself was not redesigned or recolored.

Before a public release, the owner must confirm:

- the origin of the artwork and the rights to redistribute it under the
  eventual project license;
- whether the cat silhouette on the laptop and mug is intended to reference a
  third-party mark (it resembles GitHub's Octocat mark), because that may
  require permission or a design change.

Until those confirmations are recorded, the icon is treated as project-owner
material with unresolved provenance, and no claim of original or unrestricted
licensing is made for it.

## Content and fonts in this repository

All prose, compiler source, tests and examples were written for this project
(or generated with AI assistance as described in `AI_DISCLOSURE.md`). No
third-party fonts, images or datasets are committed beyond `icon.png` and the
site assets derived from it. The documentation references JDK classes by name
but does not redistribute JDK code.
