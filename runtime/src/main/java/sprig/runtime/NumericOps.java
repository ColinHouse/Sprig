package sprig.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Numeric operations whose Sprig semantics differ from Java's defaults. */
public final class NumericOps {
    private NumericOps() {}

    public static long add(long a, long b) { try { return Math.addExact(a, b); }
        catch (ArithmeticException e) { throw new SprigNumericError("Int addition overflow"); } }
    public static long sub(long a, long b) { try { return Math.subtractExact(a, b); }
        catch (ArithmeticException e) { throw new SprigNumericError("Int subtraction overflow"); } }
    public static long mul(long a, long b) { try { return Math.multiplyExact(a, b); }
        catch (ArithmeticException e) { throw new SprigNumericError("Int multiplication overflow"); } }
    public static long neg(long a) { try { return Math.negateExact(a); }
        catch (ArithmeticException e) { throw new SprigNumericError("Int negation overflow"); } }
    public static long abs(long a) { return a < 0 ? neg(a) : a; }
    public static long divTrunc(long a, long b) {
        if (b == 0) throw new SprigNumericError("Int division by zero");
        if (a == Long.MIN_VALUE && b == -1) throw new SprigNumericError("Int division overflow");
        return a / b;
    }
    public static long rem(long a, long b) {
        if (b == 0) throw new SprigNumericError("Int remainder by zero");
        return a % b;
    }

    public static int add32(int a, int b) { try { return Math.addExact(a, b); }
        catch (ArithmeticException e) { throw new SprigNumericError("Int32 addition overflow"); } }
    public static int sub32(int a, int b) { try { return Math.subtractExact(a, b); }
        catch (ArithmeticException e) { throw new SprigNumericError("Int32 subtraction overflow"); } }
    public static int mul32(int a, int b) { try { return Math.multiplyExact(a, b); }
        catch (ArithmeticException e) { throw new SprigNumericError("Int32 multiplication overflow"); } }
    public static int neg32(int a) { try { return Math.negateExact(a); }
        catch (ArithmeticException e) { throw new SprigNumericError("Int32 negation overflow"); } }
    public static int divTrunc32(int a, int b) {
        if (b == 0) throw new SprigNumericError("Int32 division by zero");
        if (a == Integer.MIN_VALUE && b == -1) throw new SprigNumericError("Int32 division overflow");
        return a / b;
    }
    public static int rem32(int a, int b) {
        if (b == 0) throw new SprigNumericError("Int32 remainder by zero");
        return a % b;
    }
    public static int toInt32Exact(long a) {
        try { return Math.toIntExact(a); }
        catch (ArithmeticException e) { throw new SprigNumericError("Int value outside Int32 range"); }
    }
    public static double toFloatExact(long a) {
        double value = (double) a;
        if (new BigDecimal(value).toBigInteger().compareTo(BigInteger.valueOf(a)) != 0) {
            throw new SprigNumericError("Int to Float loses precision; use toFloatLossy() explicitly");
        }
        return value;
    }
    public static float toFloat32Exact(double a) {
        float value = (float) a;
        if (Double.isNaN(a) && Float.isNaN(value)) return value;
        if ((double) value != a) throw new SprigNumericError("Float to Float32 loses precision or range");
        return value;
    }
    public static long floatToIntExact(double a) {
        if (!Double.isFinite(a) || a < -0x1.0p63 || a >= 0x1.0p63 || a != Math.rint(a)) {
            throw new SprigNumericError("Float is not an exact Int value");
        }
        return (long) a;
    }
    public static long floatToIntTrunc(double a) {
        if (!Double.isFinite(a) || a < -0x1.0p63 || a >= 0x1.0p63) {
            throw new SprigNumericError("Float outside Int range");
        }
        return (long) a;
    }
    public static boolean approxEqual(double a, double b, double absoluteTolerance) {
        if (Double.isNaN(absoluteTolerance) || absoluteTolerance < 0)
            throw new SprigNumericError("approxEqual tolerance must be nonnegative and not NaN");
        if (a == b) return true;
        if (!Double.isFinite(a) || !Double.isFinite(b)) return false;
        return Math.abs(a - b) <= absoluteTolerance;
    }
}
