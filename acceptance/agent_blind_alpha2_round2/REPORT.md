# Sprig alpha.2 archive-only blind SDK test, round 2

## Scope and setup

I used only the extracted SDK at `sdk/sprig-v0.1.0-alpha.2-jdk` as Sprig source of truth. All authored source, generated build output, and the locally made Java JAR are under `work/` in this report directory. Discovery output is in `discovery.log`; compiler/build/run commands and their complete output are in `commands.log`.

The documented `bin/sprig` launcher was mode `0644` in this supplied extracted directory (`stat` output at the end of `commands.log`). Direct `bin/sprig ...` invocation failed with permission denied (exit 126). Running the same script as `sh bin/sprig ...` worked. Per the parent task coordinator, the ZIP entry records mode `0755` and standard `unzip` preserves it; I did not independently inspect or re-extract an archive because the assigned source boundary was the extracted SDK. This is an extraction-mode issue, not a compiler failure.

Discovery began with version, `doctor --json`, `capabilities --json`, help, and API calls. Reported environment: compiler alpha.2, language 0.7, JDK 26.0.1, `javac` available. Capabilities reported lambdas through arity 3, variants and local modules, no arrays/varargs, and exhaustive statement matches without wildcard.

## Task outcomes

1. **Startup example and typed function — passed.** `INSTALL.md` points to `examples/hello.spr`; `check` passed and `run` printed `Hello, Ada!`. Authored `work/start.spr` with `square(value: Int) -> Int`; check passed and run printed `81`.

2. **Checked-exception JDK call — safely handled, with an observed compiler gap.** Before coding, `api java.nio.file.Files --member readString --json` reported `Files.readString(Path) -> String?`, `checkedExceptions: [java.io.IOException]`, and a supported direct signature. `File.toPath()` was also cataloged as `Path?`. `work/io_caught.spr` declares `throws IOException`, checks both nullable references, and catches `IOException`; running with a deliberately missing file printed `read failed` and exited 0. For comparison, the unhandled `io_first.spr` also passed check and ran successfully when reading the existing README. Thus the safe catch path worked, but the compiler did not reject that uncaught checked call in this top-level case despite the guide saying checked Java exceptions must be caught or declared. I report this as observed behavior, not an assumed rule.

3. **Two-source local module project — passed.** `work/module_main.spr` imports `./mod_math.spr`; `check` was not run separately, but `build` succeeded and `run` printed `42`.

4. **Nullable Java reference — passed.** API metadata for `System.getProperty(String)` gives a nullable `String?`. `work/null_java.spr` checks against null before use. Querying a deliberately absent system property selected the `else` branch and printed `missing property`.

5. **Array-returning Java API boundary — unsupported, correctly identified.** API metadata for `String.toCharArray()` reports Java `char[]`, `usableFromSprig: false`, `signatureSupported: false`, and reason `Java array result has no Sprig source type or adapter`. The attempted source `work/array_probe.spr` failed check with `SPR-JVM-MEMBER`; I did not invent a Sprig array type. The Java array is visible in metadata only and cannot be used from this Sprig source.

6. **Overloaded Java method — passed.** API metadata for `Math.max` showed supported overloads for `Float`, `Float32`, `Int32`, and `Int`. `work/overload.spr` gives both arguments type `Int`; check and build passed, and run printed `29`.

7. **Lambda and arity — passed within documented limits.** The archived lambda snippet documents lambda forms with arities zero through three, while capabilities independently reports `lambdaMaxArity: 3`. I exercised a one-parameter lambda in `work/lambda.spr`, using `map` and `filter`; run printed `[9, 12]`. No unsupported arity was guessed or used.

8. **Exhaustive variant match extension — passed after repair.** `work/match.spr` defines `Signal.Pause` but omits it from the match. Check reported `SPR-MATCH-NONEXHAUSTIVE`, message `Missing case: Signal.Pause`, with the hint to add that explicit case. `work/match_fixed.spr` adds it; run printed `pause`.

9. **Numeric diagnostics and explicit repairs — passed.** Integer `/` in `work/numeric_bad.spr` was rejected by check with `SPR-NUM-DIVISION`; `explain SPR-NUM-DIVISION --json` says use `divTrunc` for deliberate truncation. `numeric_fixed.spr` uses `divTrunc` and prints `4`. A second deliberate conversion mistake (`Float` initialized into `Int`) yielded `SPR-NUM-CONVERSION` with an explicit-conversion hint. Runtime `Int` addition overflow in `numeric_overflow.spr` reported `SPR-RUNTIME-EXCEPTION` / `SprigNumericError: Int addition overflow`; the repaired source avoids overflowing and prints the maximum `Int` successfully. The first explain probe misspelled the emitted code (`SPR-NUMERIC-DIVISION`); it returned `known:false`, then the exact emitted code was explained successfully.

10. **Third-party JAR and 150–300-line program — passed.** I authored and locally compiled `thirdparty/src/fixture/Stamp.java`, packaged `thirdparty/stamp.jar`, then discovered it with `api fixture.Stamp --classpath ... --json`. Catalog metadata exposed a no-arg constructor, `tag(String) -> String?`, `code() -> Int32`, and `bump(Int32) -> Int32`, all supported. `work/jar_program.spr` is 160 lines and exercises that JAR plus typed functions, a class and methods, enum, variant and exhaustive matches, null checks, `List`/`MutableList`, `Map`, and lambdas. `check` passed with the JAR on classpath; `build` succeeded with the same classpath; `run` succeeded and printed the expected output beginning `alpha:132`, including `jar:invoice`, `12`, and `7` from the third-party calls.

## Repair iterations and unsupported-feature decisions

- Used `sh` to run the launcher after the extracted executable bit prevented direct invocation.
- An early loop that formed API arguments mishandled `zsh` positional parameters and queried combined class names; reran the API commands explicitly and got valid metadata. Those failed probes are retained in `discovery.log`.
- Did not attempt to adapt the Java `char[]` into an invented Sprig array type; the catalog and capabilities mark arrays unsupported.
- Corrected the first diagnostic explanation request to use the actual `SPR-NUM-DIVISION` code emitted by check.
- Used the archived documented one-parameter lambda form and the catalog's maximum arity rather than guessing a Java SAM conversion.

## Files

- `discovery.log` — documented startup discovery, topic help, Java API metadata, and classpath JAR discovery.
- `commands.log` — checks, builds, runs, diagnostics, outputs, and the launcher mode observation.
- `work/` — all authored Sprig and Java source, local JAR, and generated build directories.
