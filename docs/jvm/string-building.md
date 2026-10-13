# Building large strings without copying every prefix

A loop that repeatedly appends to an immutable String with `+=` constructs a
new string from the old prefix and the new fragment. For similarly sized
fragments, total copied text can grow quadratically with the row count.
Use `java.lang.StringBuilder` for incremental construction, or `text.join`
from `@std/text.spr` when the String parts already exist. Convert non-string
Sprig values to their Sprig text explicitly when Java append formatting would
differ. Call the builder's `toString()` once at the end.

The [English recipe](https://colinhouse.github.io/Sprig/en/guide/java-interop-cookbook#string-building)
and [中文配方](https://colinhouse.github.io/Sprig/guide/java-interop-cookbook#string-building)
share an executed snippet. `sprig help strings` also exposes this advice.
This resolves the documentation option in #231; no automatic compiler rewrite
or acceptance rule changes are introduced.

## Reproduce the comparison

After building the compiler:

```sh
python3 scripts/internal/benchmark-string-building.py --rows 20000 --warmups 3 --samples 7
```

The script builds a Sprig program and runs both constructions in the same JVM,
with a fixed 256 MiB heap, three warmups **per variant**, and seven measured
samples per variant. Order alternates. Only construction is timed with
`System.nanoTime()`; compilation, startup and correctness assertions are excluded.
After every sample, the program checks the complete text against an independently
specified repeated-row string. The script prints all samples, the medians,
environment and expected UTF-8 byte count/hash. There is no CI timing threshold.

## Recorded run, 2026-10-13

macOS 26.5.2 arm64, OpenJDK 26.0.1; 20,000 copies of `row,oak,12` plus newline.
Both produce exactly 220,000 UTF-8 bytes, SHA-256
`130fe455fba1141124499bc199eb4486f5931dda4f7b291cf4448000c3d8bba0`.

| Construction | Median milliseconds | Seven measured samples (milliseconds) |
|---|---:|---|
| Repeated String `+=` | 76.581834 | 75.429583, 75.090750, 76.581834, 75.201083, 78.110959, 77.598750, 77.067291 |
| StringBuilder append | 0.129208 | 0.287083, 0.137250, 0.129208, 0.170708, 0.113916, 0.078208, 0.084250 |

This is a small constant-fragment construction benchmark, not the original CSV
importer, a compiler-before/after comparison, or a general Sprig/Java throughput
claim. JIT compilation, GC, heap size, other processes and actual fragment work
change timings. The builder samples still vary as the JVM warms further; the
numbers demonstrate the cost hazard rather than a promised application speedup.
The two implementations have identical checked text in this workload; not every
arbitrary String concatenation can be mechanically replaced by Java append.
