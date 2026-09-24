# Sprig stage-0 numeric semantics

This is the normative contract for the numeric features implemented in the
root-level stage-0 compiler.
The original v0.7 design kit specifies `Int` as signed 64-bit and `Float` as
binary64, but leaves overflow and integer division open. The rules below close
those gaps without changing the original grammar. `Float` is the canonical
binary64 name; `Float64` is **not** an alias.

## Types, literals, ranges

| Sprig type | Representation | Construction |
|---|---|---|
| `Int` | signed 64-bit, −2^63 through 2^63−1 | unsuffixed integer literal, `Int.parse`, checked conversion |
| `Int32` | signed 32-bit, −2^31 through 2^31−1 | contextual integer literal, `Int.toInt32Exact` |
| `BigInt` | arbitrary-precision integer (`BigInteger` wrapper) | `BigInt.parse(text)`, `BigInt.fromInt(value)`, `BigInt.fromJava(value)` |
| `Float` | IEEE 754 binary64 (`double`) | floating literal, contextual integer literal, conversion |
| `Float32` | IEEE 754 binary32 (`float`) | contextual floating/integer literal, checked conversion |
| `Decimal` | arbitrary-precision base-10 (`BigDecimal` wrapper) | `Decimal.parse(text)`, `Decimal.fromInt(value)`, `Decimal.fromJava(value)` |

The parser preserves original literal text and source span. Integer literals
are parsed as arbitrary precision before checking the target range; the
unary-minus form permits the most negative `Int` and `Int32` literal. A literal
whose value exceeds its fixed-width target is a compile error (`SPR-NUM-RANGE`).
An integer literal may acquire a `Float` or `Float32` target type **only if its
integer value is exactly representable**. Thus `let x: Float = 1` is legal;
`let x: Float = 9007199254740993` is rejected. A floating literal defaults
to `Float`; a `Float32` target rounds the decimal token directly to binary32.
Floating literal overflow and nonzero underflow to zero are compile errors.
Representable subnormal values are allowed. Runtime parsing via `String.toFloat`
uses Java's parser and is a separate, potentially overflowing API.

## Conversion contract

| Source → target | Implicit variable conversion | Explicit API |
|---|---|---|
| `Int32` → `Int` | yes, exact | `.toInt()` |
| `Float32` → `Float` | yes, exact | `.toFloat()` |
| `Int` → `Int32` | no | `.toInt32Exact()`; range error |
| `Int` → `Float` | no | `.toFloat()` / `.toFloatExact()`; precision error, or `.toFloatLossy()` |
| `Float` → `Float32` | no | `.toFloat32Exact()`; precision/range error, or `.toFloat32Lossy()` |
| `Float` → `Int` | no | `.toInt()` / `.toIntExact()`; fractional/range error, or `.toIntTrunc()`; range error |
| `Int`/`Int32` → `Decimal` | no | `.toDecimal()` or `Decimal.fromInt` |
| `BigInt` → `Int` | no | `.toIntExact()`; range error |
| `BigInt` → `Float` | no | `.toFloatExact()` or `.toFloatLossy()` |
| `Decimal` → `Int`/`Float` | no | `.toIntExact()`, `.toFloatExact()`, `.toFloatLossy()` |
| `Decimal` ↔ binary float | no | explicit conversion; construct Decimal from decimal text or Java BigDecimal |

`toFloatLossy` and `toFloat32Lossy` may round, overflow to Infinity, or
underflow to zero. They require an explicit call. There is no implicit
`Number` supertype that silently mixes decimal and binary arithmetic.

## Operators and result types

Both operands must belong to the same numeric family. `Int32`/`Int` can mix
with result `Int`; `Float32`/`Float` can mix with result `Float`. Integer ↔
binary float, integer ↔ decimal, BigInt ↔ fixed-width integer, and Decimal ↔
binary float require explicit conversion. No operand is silently narrowed.

| Operands | `+`, `-`, `*` | `/` | `%` | unary `-` | ordering and `==` |
|---|---|---|---|---|---|
| `Int32`, `Int32` | `Int32`, checked | compile error | `Int32`, checked zero | checked | exact integer comparison |
| `Int32`/`Int` pair | `Int`, checked | compile error | `Int`, checked zero | own width, checked | exact integer comparison |
| `Float32`, `Float32` | `Float32`, IEEE | `Float32`, IEEE | `Float32`, IEEE | `Float32` | IEEE comparison |
| `Float32`/`Float` pair | `Float`, IEEE | `Float`, IEEE | `Float`, IEEE | own width | IEEE comparison |
| `BigInt`, `BigInt` | `BigInt`, exact | compile error | `BigInt`, checked zero | exact | exact integer comparison |
| `Decimal`, `Decimal` | `Decimal`, exact | compile error | compile error | exact | numeric decimal comparison |

For integer quotient use `a.divTrunc(b)` (toward zero). Division or remainder
by zero raises `SprigNumericError`. `MIN_VALUE.divTrunc(-1)` and negating
`MIN_VALUE` raise `SprigNumericError`; they never wrap. `+`, `-`, `*`, unary
minus and compound assignments use the same checked runtime helpers. Integer
`/` is rejected because returning an integer would silently truncate, while
returning `Float` would silently lose precision near 2^53. This applies to
`BigInt` as well. `Decimal.divide(divisor, scale, roundingMode)` requires
explicit base-10 digits after the point and a Java `RoundingMode` name such as
`"HALF_EVEN"`; invalid mode, zero divisor or unsupported scale is a numeric
runtime error. Decimal addition, subtraction and multiplication are exact.

## Binary floating behavior

`Float`/`Float32` arithmetic uses JVM IEEE 754 `double`/`float`: round to
nearest, ties to even; signed zero; NaN; positive/negative Infinity; gradual
underflow where supported. Floating `%` follows Java's truncating-quotient
remainder operation, not the IEEE `remainder` library function. Runtime overflow may
yield zero, and floating division by zero yields Infinity or NaN. They are not
traps. `NaN == anything` is false, including itself; ordering with NaN is
false; `-0.0 == +0.0` is true. Equality never uses a hidden tolerance.
`a.approxEqual(b, absoluteTolerance)` is an explicit **absolute** tolerance
comparison, not a general numerical error proof. `Float.isNaN/isInfinite/
isFinite` expose special-value classification. Float printing uses Java's
round-trip decimal formatting; it does not round away `0.1 + 0.2` error.

`Map`/`MutableMap` reject `Float` and `Float32` keys because Java hashing and
IEEE equality disagree for NaN and signed zero. Distinct lists and variant
values compare their floating elements/fields with Sprig equality; structural
equality currently short-circuits on object identity, so a list containing
NaN compares equal to itself. Do not use a container containing floats as a
map key until a dedicated key-equality rule is specified. JVM collection
objects imported directly are outside this guarantee.

Floating expressions are emitted in source order. No constant folding or
fast-math optimizer is present. Exact bitwise reproducibility of every
transcendental across JVM versions is **not** guaranteed by this stage-0
contract; even correct binary arithmetic cannot ensure numerically stable
algorithms, physical-unit consistency, or correct conditioning.

## Java boundary

Java `long`/`Long` maps to `Int`, `int`/`Integer` (also short/byte) to
`Int32`, `double`/`Double` to `Float`, and `float`/`Float` to `Float32`.
`BigInteger` and `BigDecimal` remain Java types until explicitly wrapped with
`BigInt.fromJava` / `Decimal.fromJava`; `.toJava()` unwraps. A literal may
match an `int`/`float` Java formal only when in range and exactly representable
under the literal rule. A *variable* cannot silently narrow into a Java
method formal. Primitive Java results are non-null; reference and boxed
primitive fields/results are nullable in Sprig and require a null check before
dereference. Java parameter annotations are not yet interpreted. JVM calls
are still direct Java calls: Java library arithmetic, unchecked exceptions,
null returns, boxed generic collections, and arrays do not inherit Sprig's
checked-arithmetic guarantee. Validate or adapt their contracts explicitly at
the boundary.

## Agent diagnostic contract and tests

| Code | Trigger | Suggested action |
|---|---|---|
| `SPR-NUM-RANGE` | literal outside target range or nonzero float literal rounded to zero | choose a wider type, a decimal text constructor, or correct the literal |
| `SPR-NUM-CONVERSION` | unsafe implicit conversion or native Float map key | call an exact/explicitly lossy conversion, or choose an appropriate key type |
| `SPR-NUM-DIVISION` | integer/BigInt `/` or Decimal `/` without policy | call `divTrunc`, or explicitly select Float/Decimal and Decimal scale/mode |
| `SPR-NUM-MIXED` | arithmetic across incompatible numeric families | convert each operand deliberately, considering precision/range |

`sprig check --json` emits schema version 1 with `code`, `phase`, `severity`,
`uri`, zero-based `range`, `message`, `expectedType`, `actualType`, `hint`,
`related`, and `suggestedEdits`. An Agent should use the code and actual type
to propose an explicit fix; it must not silently insert `Lossy` or `Trunc`.
Example: assigning an `Int` variable to `Float` reports
`SPR-NUM-CONVERSION`, `expectedType="Float"`, `actualType="Int"`, and a hint
to choose exact or explicitly lossy conversion. Runtime numeric failures use
`SprigNumericError` and are reported as `SPR-RUNTIME-EXCEPTION` when uncaught;
the arithmetic expression's precise Sprig span is not yet attached. Run
`python3 tests/numeric/check_numeric.py` or `./scripts/test.sh` from the
repository root.
