# Numeric design decisions and open work

The historical `spec/docs/LANGUAGE_SPEC.md` fixed `Int` at 64 bits and `Float` at
binary64 but explicitly left overflow and integer division unspecified. This
stage-0 implementation supplies those missing rules. See
`NUMERIC_SEMANTICS.md` for the binding contract and runnable examples under
`tests/numeric/`.

| Decision | Alternatives considered | Reason and Agent impact |
|---|---|---|
| Checked fixed-width arithmetic | Java wraparound; saturating arithmetic | Wraparound silently corrupts computations. Saturation also changes the answer silently. A runtime numeric error makes overflow visible. |
| Reject integer `/`; explicit `divTrunc` | Java truncation; implicit Float quotient; rational type | Truncation is easy to overlook. Float quotient loses integer precision beyond 2^53. A rational type adds substantial complexity. The compiler suggests a deliberate operation. |
| `Float` remains binary64, `Float32` is additional | Rename to `Float64`; add both `Float` and `Float64` | The v0.7 name is familiar and already in examples. One canonical binary64 name avoids synonym confusion. |
| Contextual numeric literals, strict variable conversions | No literal context; implicit numeric promotion | Literals such as `let x: Float = 1` remain concise when exact. A variable's full range must be respected, so `Int`→`Float` is explicit. |
| Exact and explicitly lossy conversion methods | A single cast; all lossy conversion forbidden | The method name reveals whether precision loss is accepted. Runtime exact checks handle values whose exactness cannot be proved statically. |
| Decimal/BigInt wrappers around JDK classes | Built-in literal suffixes; generic `Number`; raw Java class | Library wrappers avoid new syntax and keep boundary semantics explicit. BigDecimal equality is numeric (`1.0 == 1.00`), unlike Java `BigDecimal.equals`. |
| Decimal division requires scale and mode | Implicit context or arbitrary default precision | `1/3` has no finite exact decimal representation. The call site must state rounding policy; `UNNECESSARY` can demand exactness. |
| No implicit cross-family arithmetic | Java numeric promotion | Explicit conversion prevents `Int + Float`, `Decimal + Float`, and `BigInt + Int` from silently changing range or precision. |
| Reject native Float map keys | Java boxed `Double` hashing | IEEE equality gives `NaN != NaN` and signed zeros equal, conflicting with standard hash-key behavior. A future explicit total-order/key wrapper could allow them safely. |
| Preserve IEEE float nonfinite values | Trap every infinity/NaN | Trapping would depart from ubiquitous JVM scientific libraries. Users can call `isFinite` and use stable algorithms. The type system cannot certify numerical stability. |

## Grammar and compiler boundary

No new token or parser rule was needed. The existing grammar already accepts
type names, integer and decimal-exponent literals, unary minus, arithmetic,
and method calls. Numeric ranges and conversions belong in the type checker;
overflow checks and Decimal/BigInt operations belong in the runtime and Java
emitter. Source text and span are retained on AST literal nodes. The current
compiler lowers a typed AST directly into Java; it has **no separate typed IR**.
Adding one could simplify future optimizations but is not required to enforce
these rules. Any future constant folding must reuse the checked integer and
IEEE/decimal semantics; none is currently performed.

## Remaining limitations, ordered by impact

1. **JVM boundary audit.** Java calls may return null or perform unchecked
   arithmetic, and generic/array element types are not fully represented in
   Sprig. A Java `long` return maps to `Int`, but a *boxed* `Long` result may
   still be null. Future work needs explicit nullable adapters and array/list
   conversion APIs. Java `short`/`byte` returns are mapped to `Int32`, but
   passing Sprig variables to `short`/`byte` formals requires an explicit
   checked adapter not yet offered.
2. **Runtime diagnostics.** Static `SPR-NUM-*` errors have source ranges and
   JSON. An uncaught `SprigNumericError` is surfaced as a runtime exception;
   a precise source span for the arithmetic expression is not yet attached.
3. **Math library.** `Float.sqrt`, `abs`, `floor`, `ceil` and explicit absolute
   tolerance comparison exist. Stable summation, relative/ULP comparison,
   compensated dot product, finite-value validation and statistics utilities
   should be library additions with algorithm-specific tests. They should not
   be asserted as a type-system guarantee.
4. **High-precision policy.** Decimal has unlimited exact `+`, `-`, `*`, but
   no first-class `MathContext`, advanced functions, or configurable resource
   limits. BigInt likewise uses JVM allocation; very large computations may
   exhaust memory. A string rounding-mode name is checked at runtime rather
   than being a Sprig enum.
5. **Float reproducibility.** Basic operations use JVM binary floats and
   source order. Bit-identical results for all math functions across JVM
   versions, architectures and external libraries need separate contracts and
   tests. Algorithm conditioning and physical units remain user concerns.
   Direct Float map keys are rejected, but composite keys containing Float and
   Java boxed Double keys are not yet prohibited; these need a total key
   equality policy before being advertised as safe.
6. **Compiler architecture.** A shared numeric semantics module for both
   compile-time evaluation and runtime will become necessary if constant
   folding is added. Today there is no folding path to disagree with runtime.

## Compatibility and migration

Old Sprig code using integer `/` must choose `divTrunc` or convert to a
numeric family with explicit precision policy. `(3.9).toInt()` now requires an
integral value; use `toIntTrunc()` to request truncation. Calls from `Int`
variables to Java `int` methods and from `Float` variables to Java `float`
methods now fail at type checking; declare an appropriately sized Sprig value
or call an explicit checked conversion. Existing standard arithmetic literals,
recursion, AST visitors, and JVM overloads taking matching widths continue to
work. Original root design files and grammars were not modified.
