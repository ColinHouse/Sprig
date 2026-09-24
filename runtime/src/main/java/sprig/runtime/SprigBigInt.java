package sprig.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Arbitrary precision integer with explicit conversions at fixed-width boundaries. */
public final class SprigBigInt implements Comparable<SprigBigInt> {
    private final BigInteger value;

    private SprigBigInt(BigInteger value) { this.value = value; }

    public static SprigBigInt parse(String text) {
        try { return new SprigBigInt(new BigInteger(text)); }
        catch (NumberFormatException e) { throw new SprigNumericError("Invalid BigInt: " + text); }
    }
    public static SprigBigInt fromInt(long value) { return new SprigBigInt(BigInteger.valueOf(value)); }
    public static SprigBigInt fromJava(BigInteger value) { return new SprigBigInt(value); }
    public BigInteger toJava() { return value; }
    public SprigBigInt add(SprigBigInt other) { return new SprigBigInt(value.add(other.value)); }
    public SprigBigInt subtract(SprigBigInt other) { return new SprigBigInt(value.subtract(other.value)); }
    public SprigBigInt multiply(SprigBigInt other) { return new SprigBigInt(value.multiply(other.value)); }
    public SprigBigInt negate() { return new SprigBigInt(value.negate()); }
    public SprigBigInt divTrunc(SprigBigInt other) {
        if (other.value.signum() == 0) throw new SprigNumericError("BigInt division by zero");
        return new SprigBigInt(value.divide(other.value));
    }
    public SprigBigInt remainder(SprigBigInt other) {
        if (other.value.signum() == 0) throw new SprigNumericError("BigInt remainder by zero");
        return new SprigBigInt(value.remainder(other.value));
    }
    public long toIntExact() {
        try { return value.longValueExact(); }
        catch (ArithmeticException e) { throw new SprigNumericError("BigInt outside Int range"); }
    }
    public double toFloatExact() {
        double result = value.doubleValue();
        if (!Double.isFinite(result) || !new BigDecimal(result).toBigInteger().equals(value))
            throw new SprigNumericError("BigInt to Float loses precision or range");
        return result;
    }
    public double toFloatLossy() { return value.doubleValue(); }
    public SprigDecimal toDecimal() { return SprigDecimal.fromJava(new BigDecimal(value)); }
    @Override public int compareTo(SprigBigInt other) { return value.compareTo(other.value); }
    @Override public boolean equals(Object other) { return other instanceof SprigBigInt that && value.equals(that.value); }
    @Override public int hashCode() { return value.hashCode(); }
    @Override public String toString() { return value.toString(); }
}
