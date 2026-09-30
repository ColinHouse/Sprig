package sprig.runtime.host;

import java.time.Instant;
import java.time.DateTimeException;
import sprig.runtime.SprigError;

/** Process/time boundary. The generated JVM entry point installs a copied argument vector. */
public final class HostSystem {
    private static volatile String[] arguments = new String[0];
    private HostSystem() {}
    public static void setArguments(String[] values) { arguments = values.clone(); }
    public static long argumentCount() { return arguments.length; }
    public static String argument(long index) { return arguments[Math.toIntExact(index)]; }
    public static String environment(String name) { return System.getenv(name); }
    public static long epochMillis() { return System.currentTimeMillis(); }
    public static String utcNow() { return Instant.now().toString(); }
    public static String formatUtc(long epochMillis) {
        try {
            return Instant.ofEpochMilli(epochMillis).toString();
        } catch (DateTimeException | ArithmeticException failure) {
            throw new SprigError("Invalid epoch-millisecond value: " + epochMillis, failure);
        }
    }
    public static long parseUtc(String value) {
        try {
            return Instant.parse(value).toEpochMilli();
        } catch (DateTimeException | ArithmeticException failure) {
            throw new SprigError("Invalid ISO-8601 timestamp or epoch-millisecond range: " + value, failure);
        }
    }
}
