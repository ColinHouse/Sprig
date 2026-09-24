package sprig.runtime;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Exact decimal values. Arithmetic is exact except explicit rounded division. */
public final class SprigDecimal implements Comparable<SprigDecimal> {
    private final BigDecimal value;

    private SprigDecimal(BigDecimal value) { this.value = value; }

    public static SprigDecimal parse(String text) {
        try { return new SprigDecimal(new BigDecimal(text)); }
        catch (NumberFormatException e) { throw new SprigNumericError("Invalid Decimal: " + text); }
    }
    public static SprigDecimal fromInt(long value) { return new SprigDecimal(BigDecimal.valueOf(value)); }
    public static SprigDecimal fromJava(BigDecimal value) { return new SprigDecimal(value); }
    public BigDecimal toJava() { return value; }
    public SprigDecimal add(SprigDecimal other) { return new SprigDecimal(value.add(other.value)); }
    public SprigDecimal subtract(SprigDecimal other) { return new SprigDecimal(value.subtract(other.value)); }
    public SprigDecimal multiply(SprigDecimal other) { return new SprigDecimal(value.multiply(other.value)); }
    public SprigDecimal negate() { return new SprigDecimal(value.negate()); }
    public SprigDecimal divide(SprigDecimal other, long scale, String mode) {
        if (scale < 0 || scale > Integer.MAX_VALUE) throw new SprigNumericError("Decimal scale outside supported range");
        try { return new SprigDecimal(value.divide(other.value, (int) scale, RoundingMode.valueOf(mode))); }
        catch (IllegalArgumentException | ArithmeticException e) { throw new SprigNumericError("Decimal division failed: " + e.getMessage()); }
    }
    public long toIntExact() {
        try { return value.longValueExact(); }
        catch (ArithmeticException e) { throw new SprigNumericError("Decimal is not an exact Int value"); }
    }
    public double toFloatExact() {
        double result = value.doubleValue();
        if (!Double.isFinite(result) || new BigDecimal(result).compareTo(value) != 0)
            throw new SprigNumericError("Decimal to Float loses precision");
        return result;
    }
    public double toFloatLossy() { return value.doubleValue(); }
    @Override public int compareTo(SprigDecimal other) { return value.compareTo(other.value); }
    @Override public boolean equals(Object other) { return other instanceof SprigDecimal that && compareTo(that) == 0; }
    @Override public int hashCode() { return value.stripTrailingZeros().hashCode(); }
    @Override public String toString() { return value.toPlainString(); }
}
