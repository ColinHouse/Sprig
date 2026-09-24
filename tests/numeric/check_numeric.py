#!/usr/bin/env python3
"""Independent numeric acceptance cases; each snippet is compiled from source."""
from __future__ import annotations

import decimal
import pathlib
import random
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
SPRIG = ROOT / "bin/sprig"
HERE = pathlib.Path(__file__).resolve().parent
passed = 0
failed = []


def call(*args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run([str(SPRIG), *map(str, args)], text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                          timeout=30)


def verify(name: str, okay: bool, detail: str = "") -> None:
    global passed
    if okay:
        passed += 1
    else:
        failed.append(name)
        print(f"FAIL {name}: {detail[:500]}")


FIXTURES = {
    "core": "\n".join([
        "-9223372036854775808", "9223372036854775807", "-2147483648",
        "2147483647", "2", "3.5", "3", "0.10000000149011612",
        "0.3", "0.02", "0.3333", "true", "true",
    ]),
    "bigint": "\n".join([
        "9223372036854775810", "18446744073709551616", "4611686018427387904",
        "2", "-2", "true", "42", "true", "3",
    ]),
    "science": "0.0\n1.0\n1\n7.450580596923828E-9\n1.0E-8",
    "float_edges": "\n".join([
        "0.30000000000000004", "false", "true", "true", "true", "true",
        "false", "true", "true", "true", "0.0",
        "0.10000000149011612", "1.0", "-9223372036854775808", "-2147483648",
    ]),
    "interop": "42\n7\n42\n0.5\n1.20\ntrue",
}

for name, expected in FIXTURES.items():
    result = call("run", HERE / f"{name}.spr")
    verify(f"fixture {name}", result.returncode == 0 and result.stdout.strip() == expected,
           f"exit={result.returncode} stdout={result.stdout!r} stderr={result.stderr!r}")

# The Decimal oracle uses Python's independent arbitrary precision decimal package.
decimal.getcontext().prec = 80
verify("oracle decimal add", str(decimal.Decimal("0.1") + decimal.Decimal("0.2")) == "0.3")
verify("oracle decimal cancellation", str(decimal.Decimal("1e16") + 1 - decimal.Decimal("1e16")) == "1")
small_root = (decimal.Decimal("1e8") - (decimal.Decimal("1e16") - 4).sqrt()) / 2
verify("oracle quadratic instability", abs(decimal.Decimal("7.450580596923828e-9") - small_root)
       / small_root > decimal.Decimal("0.2")
       and abs(decimal.Decimal("1e-8") - small_root) / small_root < decimal.Decimal("1e-12"))

NEGATIVE = {
    "int_max_plus_one": ("let x: Int = 9223372036854775808\n", "SPR-NUM-RANGE"),
    "int_min_minus_one": ("let x: Int = -9223372036854775809\n", "SPR-NUM-RANGE"),
    "int32_max_plus_one": ("let x: Int32 = 2147483648\n", "SPR-NUM-RANGE"),
    "int32_min_minus_one": ("let x: Int32 = -2147483649\n", "SPR-NUM-RANGE"),
    "float_literal_overflow": ("let x: Float = 1e309\n", "SPR-NUM-RANGE"),
    "float_literal_underflow": ("let x: Float = 1e-400\n", "SPR-NUM-RANGE"),
    "float32_literal_overflow": ("let x: Float32 = 1e40\n", "SPR-NUM-RANGE"),
    "float32_literal_underflow": ("let x: Float32 = 1e-50\n", "SPR-NUM-RANGE"),
    "int_to_float": ("let x: Int = 9007199254740993\nlet y: Float = x\n", "SPR-NUM-CONVERSION"),
    "int32_to_float32": ("let x: Int32 = 16777217\nlet y: Float32 = x\n", "SPR-NUM-CONVERSION"),
    "float_to_float32": ("let x: Float = 0.1\nlet y: Float32 = x\n", "SPR-NUM-CONVERSION"),
    "float_to_int": ("let x: Float = 3.5\nlet y: Int = x\n", "SPR-NUM-CONVERSION"),
    "integer_division": ("let x = 1 / 2\n", "SPR-NUM-DIVISION"),
    "mixed_int_float": ("let x: Int = 10\nlet y: Float = 3.0\nlet z = x / y\n", "SPR-NUM-MIXED"),
    "mixed_decimal_float": ("let x = Decimal.parse(\"0.1\")\nlet y: Float = 0.2\nlet z = x + y\n", "SPR-NUM-MIXED"),
    "decimal_division": ("let x = Decimal.parse(\"1\") / Decimal.parse(\"3\")\n", "SPR-NUM-DIVISION"),
    "bigint_division": ("let x = BigInt.fromInt(1) / BigInt.fromInt(3)\n", "SPR-NUM-DIVISION"),
    "bigint_to_int": ("let x: Int = BigInt.fromInt(1)\n", "SPR-NUM-CONVERSION"),
    "inexact_int_literal_to_float": ("let x: Float = 9007199254740993\n", "SPR-NUM-RANGE"),
    "inexact_int_literal_to_float32": ("let x: Float32 = 16777217\n", "SPR-NUM-RANGE"),
    "jvm_long_to_int": ("import java.lang.Integer as JInteger\nlet x: Int = 4294967296\nprint(JInteger.valueOf(x))\n", "SPR-JVM-MEMBER"),
    "jvm_double_to_float": ("import java.lang.Float as JFloat\nlet x: Float = 0.1\nprint(JFloat.valueOf(x))\n", "SPR-JVM-MEMBER"),
    "float_map_key": ("let m: Map[Float, Int] = {0.0: 1}\n", "SPR-NUM-CONVERSION"),
    "nullable_float_map_key": ("let m: Map[Float?, Int] = {0.0: 1}\n", "SPR-NUM-CONVERSION"),
    "float_map_key_inferred": ("let m = {0.0: 1}\n", "SPR-NUM-CONVERSION"),
    "list_element_inexact": ("let xs: List[Float] = [9007199254740993]\n", "SPR-NUM-RANGE"),
}

RUNTIME_ERRORS = {
    "add_overflow": ("print(9223372036854775807 + 1)\n", "Int addition overflow"),
    "sub_overflow": ("print(-9223372036854775808 - 1)\n", "Int subtraction overflow"),
    "mul_overflow": ("print(3037000500 * 3037000500)\n", "Int multiplication overflow"),
    "neg_overflow": ("let x: Int = -9223372036854775808\nprint(-x)\n", "Int negation overflow"),
    "rem_zero": ("print(1 % 0)\n", "Int remainder by zero"),
    "div_zero": ("print((1).divTrunc(0))\n", "Int division by zero"),
    "div_min": ("let x: Int = -9223372036854775808\nprint(x.divTrunc(-1))\n", "Int division overflow"),
    "int32_add": ("let x: Int32 = 2147483647\nprint(x + 1)\n", "Int32 addition overflow"),
    "int32_sub": ("let x: Int32 = -2147483648\nprint(x - 1)\n", "Int32 subtraction overflow"),
    "int32_mul": ("let x: Int32 = 50000\nprint(x * x)\n", "Int32 multiplication overflow"),
    "int32_neg": ("let x: Int32 = -2147483648\nprint(-x)\n", "Int32 negation overflow"),
    "int32_div_min": ("let x: Int32 = -2147483648\nprint(x.divTrunc(-1))\n", "Int32 division overflow"),
    "compound_overflow": ("var x: Int = 9223372036854775807\nx += 1\n", "Int addition overflow"),
    "inexact_int_to_float": ("print((9007199254740993).toFloatExact())\n", "loses precision"),
    "inexact_bigint_to_float": ("print(BigInt.parse(\"9007199254740993\").toFloatExact())\n", "loses precision"),
    "inexact_decimal_to_float": ("print(Decimal.parse(\"0.1\").toFloatExact())\n", "loses precision"),
    "inexact_float_to_float32": ("print((0.1).toFloat32Exact())\n", "loses precision"),
    "inexact_float_to_int": ("print((3.9).toIntExact())\n", "not an exact Int"),
    "out_of_range_float_to_int": ("print((1e20).toIntTrunc())\n", "outside Int range"),
    "bigint_to_int_range": ("print(BigInt.parse(\"9223372036854775808\").toIntExact())\n", "outside Int range"),
    "decimal_div_zero": ("print(Decimal.parse(\"1\").divide(Decimal.parse(\"0\"), 2, \"HALF_EVEN\"))\n", "zero"),
}

POSITIVE = {
    "zero_exponent": ("let x: Float32 = 0e-100\nprint(x)\n", "0.0"),
    "int32_widen": ("let a: Int32 = 17\nlet b: Int = a\nprint(b)\n", "17"),
    "int_literal_float_context": ("let a: Float = 1\nprint(a)\n", "1.0"),
    "float32_widen": ("let a: Float32 = 0.5\nlet b: Float = a\nprint(b)\n", "0.5"),
    "explicit_lossy": ("print((9007199254740993).toFloatLossy())\n", "9.007199254740992E15"),
    "decimal_round": ("print(Decimal.parse(\"1\").divide(Decimal.parse(\"3\"), 4, \"HALF_EVEN\"))\n", "0.3333"),
    "mixed_integer_width": ("let a: Int32 = 2147483647\nlet b: Int = 1\nprint(a + b)\n", "2147483648"),
    "mixed_float_width": ("let a: Float32 = 0.5\nlet b: Float = 0.25\nprint(a + b)\n", "0.75"),
    "signed_zero": ("print(-0.0 == 0.0)\nprint(1.0 / -0.0)\n", "true\n-Infinity"),
    "list_literal_context": ("let xs: List[Float] = [1, 2]\nprint(xs[0] + xs[1])\n", "3.0"),
}

with tempfile.TemporaryDirectory(prefix="sprig-numeric-") as work:
    directory = pathlib.Path(work)
    for name, (source, expected) in NEGATIVE.items():
        path = directory / f"{name}.spr"
        path.write_text(source)
        result = call("check", path)
        verify(f"check {name}", result.returncode != 0 and expected in result.stdout + result.stderr,
               f"exit={result.returncode} {result.stdout}{result.stderr}")
    for name, (source, expected) in RUNTIME_ERRORS.items():
        path = directory / f"{name}.spr"
        path.write_text(source)
        result = call("run", path)
        verify(f"runtime {name}", result.returncode != 0 and expected in result.stdout + result.stderr,
               f"exit={result.returncode} {result.stdout}{result.stderr}")
    for name, (source, expected) in POSITIVE.items():
        path = directory / f"{name}.spr"
        path.write_text(source)
        result = call("run", path)
        verify(f"positive {name}", result.returncode == 0 and result.stdout.strip() == expected,
               f"exit={result.returncode} {result.stdout}{result.stderr}")

    # Seeded independent Python integer oracle. One Sprig program amortizes
    # javac startup while checking runtime arithmetic on nonconstant locals.
    rng = random.Random(0x5A17)
    lines = ["func emit(a: Int, b: Int) -> Unit:",
             "    print(a + b)", "    print(a - b)", "    print(a * b)"]
    expected_lines = []
    for _ in range(24):
        a = rng.randint(-1_000_000, 1_000_000)
        b = rng.randint(-1_000_000, 1_000_000)
        lines.append(f"emit({a}, {b})")
        expected_lines.extend(map(str, (a + b, a - b, a * b)))
    path = directory / "seeded_oracle.spr"
    path.write_text("\n".join(lines) + "\n")
    result = call("run", path)
    verify("seeded integer oracle 0x5A17", result.returncode == 0
           and result.stdout.splitlines() == expected_lines,
           f"exit={result.returncode} {result.stdout}{result.stderr}")

print(f"numeric: {passed} passed, {len(failed)} failed")
sys.exit(bool(failed))
