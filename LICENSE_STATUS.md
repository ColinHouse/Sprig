# Project license status

**No license has been selected.** The Sprig project owner has not chosen a
license for the compiler, runtime, documentation, examples, website or
generated artifacts. This repository does not grant public redistribution
rights for those project materials, and no release will be published until the
owner records a license.

## Decision guide for the owner

The choice affects different parts of the project, so it is worth deciding
deliberately rather than copying another project:

| Component | What a license must cover |
|---|---|
| Compiler source (`compiler/`, `grammar/`) | Use, modification and redistribution of the tool. |
| Runtime (`runtime/`) | Redistribution **inside generated/compiled user programs**; this is the most permissive requirement in practice. |
| Examples, tests, docs, website | Reuse of prose, code snippets and images. |
| The icon and mascot (`icon.png` and assets derived from it) | Copyright/rights confirmed by the owner; see `THIRD_PARTY_NOTICES.md`. |

Common candidate directions, for discussion only — none is chosen here:

- **Permissive** (MIT / Apache-2.0 / BSD): simplest adoption, including
  commercial use. Apache-2.0 additionally grants patent terms and requires
  notices; MIT/BSD are shorter.
- **Weak copyleft** (MPL-2.0 / LGPL): modifications to covered files stay
  open; linking/generated output typically remains unrestricted. LGPL is more
  complex for a runtime embedded in user programs.
- **Strong copyleft** (GPL-3.0): derivative works must be distributed under
  the same terms. This can deter commercial users of a compiler/runtime.
- **Dual licensing or no license**: reserving all rights keeps the project
  closed until a decision is made; it is the current state.

A common split is a permissive license for the runtime and examples so user
programs are unencumbered, with the same or a different license for the
compiler. That split is a decision for the owner, not something this
repository assumes.

## Current blockers

1. The owner selects a license and adds its text (for example `LICENSE`) and
   updates this file and `README.md`.
2. The icon/mascot rights and any third-party marks in the artwork are
   confirmed (see `THIRD_PARTY_NOTICES.md`).
3. Only then can the repository be pushed publicly and a release be created.

ANTLR 4.13.2 keeps its own BSD license, reproduced in
`THIRD_PARTY_NOTICES.md`. It does not determine Sprig's own license.
