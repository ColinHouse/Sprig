# Third-party notices

This file records third-party components and material rights that affect the
Sprig repository, the runtime archives and the documentation site. It is a
notice list, not the project license. Sprig itself is licensed under
Apache-2.0 (see `LICENSE` and `LICENSE_STATUS.md`).

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

## Project icon and mascot (`assets/brand/icon-source.png`)

The artwork was supplied by the project owner, who approved its use for this
project. `assets/brand/generate.py` derives the README image, site logo,
favicons, Apple touch icon and social preview image by **cropping, masking and
resizing only**; the character was not redesigned or recolored. A separate
"S" monogram is generated for 16–32px favicons because the detailed
illustration is not legible at that size.

Recorded caveat: the artwork's origin is not independently documented in this
repository, and the cat silhouette on the laptop and mug resembles GitHub's
Octocat mark. The owner should keep a note of the artwork's source, and if the
resemblance is intentional, confirm that the reference is acceptable; a
third-party mark used to identify this project could be confusing. No claim of
original or unrestricted licensing is made for the artwork beyond the owner's
approval.

## Content and fonts in this repository

All prose, compiler source, tests and examples were written for this project
(or generated with AI assistance as described in `AI_DISCLOSURE.md`). No
third-party fonts, images or datasets are committed beyond
`assets/brand/icon-source.png` and the site assets derived from it. The
documentation references JDK classes by name but does not redistribute JDK
code.
