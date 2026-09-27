# String code-point semantics correction

Focused correction approved by the user: Sprig has no `Char` type; a textual
element is a non-null `String`, and ordinary String position operations use
Unicode code points, not JVM UTF-16 code units and not grapheme clusters.

## Contract

- `"abc".length() == 3`, `"東".length() == 1`, `"😀".length() == 1`,
  `"A😀東".length() == 3`.
- Indexing and `charAt` take a code-point index and return a one-code-point
  `String`; `codeAt` returns the numeric Unicode code point as `Int`.
- Iteration yields one `String` per code point.
- `substring(start[, end])` uses code-point indices and the existing half-open
  convention; `indexOf` returns a code-point index or the existing `-1`.
- `"e" + String.fromCode(769)` has length 2: combining sequences are two code
  points. Grapheme clusters are not modeled; no ICU/Unicode dependency exists.
- Invalid indices are not clamped; they raise the existing
  `StringIndexOutOfBoundsException` model, and `Int` positions keep the
  checked `Int32` conversion (`NumericOps.toInt32Exact`).
- `String.fromCode(128512) == "😀"`; invalid code points keep the existing
  `Character.toChars` rejection (`IllegalArgumentException`).
- Java `char`/`Character` interop remains a UTF-16 code-unit boundary: only a
  one-unit String literal is accepted, and `"😀"` is still rejected.

## Implementation

- New `sprig.runtime.StringOps` boundary: `length`, `elementAt`, `codePointAt`,
  `substring` (1- and 2-argument), `indexOf`, and a code-point `Iterable`.
  Implemented with `String.codePointCount`, `offsetByCodePoints`,
  `codePointAt`, `Character.toChars` and `Character.charCount`.
- `JavaGenerator` now emits `StringOps` calls for `String.length`,
  `charAt`, `codeAt`, `substring`, `indexOf`, String indexing, and String
  `for` iteration (`for (String c : StringOps.codePoints(text))`).
- `SprigRuntime.stringSplit` enumerates code points for the empty separator,
  retaining the final empty segment.
- `std/json.spr` parse errors now say "code point offset"; JSON escape
  processing is unchanged.

## Tests

- `tests/runtime/20_string_codepoints.spr` + `.out`: ASCII, BMP (`東`/`你`),
  supplementary (`😀`), mixed `A😀東`, combining sequence, length, indexing,
  `charAt`, `codeAt`, iteration values, `substring`, `indexOf`, `fromCode`,
  empty string and empty-separator split.
- `tests/runtime/check_strings.py` (wired into `scripts/test.py`): 27 checks
  covering first/last index, negative index, index == length, substring
  boundaries, not-found `indexOf`, supplementary-before-target indices,
  `Int32` conversion, invalid `fromCode`, and the Java `char` interop
  accept/reject rule.

## Verification

- `python3 tests/runtime/check_strings.py` — 27 checks passed.
- `python3 scripts/test.py` — 84 gates/cases passed, 0 failed (includes all
  runtime goldens, stdlib, showcases, installer/upgrade/dogfood suites).
- `./scripts/verify.sh` — contributor verification passed (build,
  compiler/JVM, grammar, executed docs, editor).
