package sprig.runtime;

import java.util.ArrayList;
import java.util.List;

/** Formatted output and value helpers used by generated Java code. */
public final class SprigRuntime {
    private SprigRuntime() {
    }

    /** Single-line transport markers for an uncaught failure; consumed by the CLI. */
    public static final String FAILURE_PREFIX = "sprig-runtime-failure:";

    /** Generated-Java location of the first user frame, when one exists. */
    public static final String FRAME_PREFIX = "sprig-runtime-frame:";

    /**
     * Reports an uncaught program failure without exposing JVM frames by
     * default. {@code SPRIG_STACKTRACE=1} (set by {@code sprig run
     * --stacktrace}) restores the full JVM stack trace for debugging.
     */
    public static void reportRuntimeFailure(Throwable failure) {
        if (System.getenv("SPRIG_STACKTRACE") != null) {
            failure.printStackTrace();
            return;
        }
        StringBuilder out = new StringBuilder(FAILURE_PREFIX).append(' ')
                .append(failure.getClass().getName());
        String message = failure.getMessage();
        if (message != null && !message.isEmpty()) {
            out.append(": ").append(message.replace('\n', ' ').replace('\r', ' '));
        }
        System.err.println(out);
        for (StackTraceElement frame : failure.getStackTrace()) {
            if (frame.getClassName().startsWith("sprig.user.")) {
                System.err.println(FRAME_PREFIX + " " + frame.getFileName() + ":" + frame.getLineNumber());
                break;
            }
        }
    }

    /**
     * Initialization-order guard for a top-level binding that code may reach
     * before its initializer ran. It throws here, inside the runtime, so the
     * reported Sprig location is the use site rather than a generated helper.
     */
    public static void requireInitialized(boolean ready, String name) {
        if (!ready) {
            throw new SprigInitializationError("Top-level '" + name + "' was used before its initializer ran");
        }
    }

    public static long initialized(boolean ready, String name, long value) {
        requireInitialized(ready, name);
        return value;
    }

    public static int initialized(boolean ready, String name, int value) {
        requireInitialized(ready, name);
        return value;
    }

    public static double initialized(boolean ready, String name, double value) {
        requireInitialized(ready, name);
        return value;
    }

    public static float initialized(boolean ready, String name, float value) {
        requireInitialized(ready, name);
        return value;
    }

    public static boolean initialized(boolean ready, String name, boolean value) {
        requireInitialized(ready, name);
        return value;
    }

    public static <T> T initialized(boolean ready, String name, T value) {
        requireInitialized(ready, name);
        return value;
    }

    /** Sprig {@code print}: one line, Sprig value formatting. */
    /** Evaluates {@code value} once and hands it to {@code body}: the receiver of a method reference. */
    public static <T, R> R bind(T value, java.util.function.Function<T, R> body) {
        return body.apply(value);
    }

    public static void print(Object value) {
        System.out.println(format(value));
    }

    /** Canonical Sprig value formatting used by print, toString and string +. */
    public static String str(Object value) {
        return format(value);
    }

    /** Preserve nullability while adapting boxed JVM scalar values. */
    public static String fromJavaCharacter(Character value) {
        return value == null ? null : String.valueOf(value);
    }

    public static Integer fromJavaShort(Short value) {
        return value == null ? null : Integer.valueOf(value.intValue());
    }

    public static Integer fromJavaByte(Byte value) {
        return value == null ? null : Integer.valueOf(value.intValue());
    }

    public static String format(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof SprigList<?> list) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append(format(item));
            }
            return sb.append("]").toString();
        }
        if (value instanceof SprigMap<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (var entry : map.entries.entrySet()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append(format(entry.getKey())).append(": ").append(format(entry.getValue()));
            }
            return sb.append("}").toString();
        }
        if (value instanceof SprigError error) {
            return String.valueOf(error.getMessage());
        }
        return String.valueOf(value);
    }

    /** {@code String.split} helper returning a Sprig list. */
    public static SprigList<String> stringSplit(String text, String separator) {
        if (separator.isEmpty()) {
            // An empty separator enumerates Unicode code points, then the final
            // empty segment, matching the existing trailing-empty contract.
            List<String> out = new ArrayList<>();
            for (String element : StringOps.codePoints(text)) {
                out.add(element);
            }
            out.add("");
            return new SprigList<>(out);
        }
        String[] parts = text.split(java.util.regex.Pattern.quote(separator), -1);
        List<String> out = new ArrayList<>(parts.length);
        java.util.Collections.addAll(out, parts);
        return new SprigList<>(out);
    }

    /** {@code String.join} helper. */
    public static String stringJoin(SprigList<String> parts, String separator) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String part : parts) {
            if (!first) {
                sb.append(separator);
            }
            first = false;
            sb.append(part);
        }
        return sb.toString();
    }

    public static SprigMutableList<Long> range(long end) {
        return range(0L, end, 1L);
    }

    public static SprigMutableList<Long> range(long start, long end) {
        return range(start, end, 1L);
    }

    public static SprigMutableList<Long> range(long start, long end, long step) {
        if (step == 0) {
            throw new SprigError("range step must not be zero");
        }
        SprigMutableList<Long> out = new SprigMutableList<>();
        if (step > 0) {
            for (long i = start; i < end; ) {
                out.append(i);
                if (i > Long.MAX_VALUE - step) break;
                i = NumericOps.add(i, step);
            }
        } else {
            for (long i = start; i > end; ) {
                out.append(i);
                if (i < Long.MIN_VALUE - step) break;
                i = NumericOps.add(i, step);
            }
        }
        return out;
    }

    /**
     * How many values range(start, end, step) holds, as an unsigned count, without
     * building the list: the bound a counted {@code for x in range(...)} loop runs to.
     * A zero step fails exactly as range does.
     */
    public static long rangeCount(long start, long end, long step) {
        if (step == 0) {
            throw new SprigError("range step must not be zero");
        }
        if (step > 0) {
            return start < end ? Long.divideUnsigned(end - start - 1, step) + 1 : 0L;
        }
        // -step is the magnitude as an unsigned value, Long.MIN_VALUE included.
        return start > end ? Long.divideUnsigned(start - end - 1, -step) + 1 : 0L;
    }

    public static long parseInt(String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            throw new SprigError("not an integer: \"" + text + "\"");
        }
    }

    public static Long parseIntOrNull(String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static double parseFloat(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            throw new SprigError("not a float: \"" + text + "\"");
        }
    }

    public static void assertTrue(boolean condition) {
        if (!condition) {
            throw new SprigError("assertion failed");
        }
    }

    public static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new SprigError("assertion failed: " + message);
        }
    }

    /**
     * {@code in} helpers. Arguments are evaluated left-to-right, so the element
     * expression runs before the container expression exactly as written. A
     * list is searched with {@code ==}, as {@link SprigList#indexOf} searches.
     */
    public static boolean listContains(Object element, SprigList<?> list) {
        return list.contains(element);
    }

    public static boolean mapContainsKey(Object key, SprigMap<?, ?> map) {
        return map.containsKeyObject(key);
    }

    public static boolean stringContains(String needle, String haystack) {
        return haystack.contains(needle);
    }

    /**
     * Deep structural equality used by Sprig {@code ==} on reference values:
     * IEEE equality for two Floats or two Float32s (NaN equals nothing, -0.0
     * equals 0.0), identity and then equals for everything else.
     */
    public static boolean equalsValue(Object a, Object b) {
        if (a instanceof Double x && b instanceof Double y) return x.doubleValue() == y.doubleValue();
        if (a instanceof Float x && b instanceof Float y) return x.floatValue() == y.floatValue();
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b);
    }

    // Ordering on a Comparable type parameter, with the same meaning as the
    // operator on the concrete type: IEEE comparison for Float/Float32 (false
    // with NaN, -0.0 equal to 0.0), natural order for Int, Int32, Decimal,
    // BigInt and String. The checker admits no other argument types.

    public static boolean lessThan(Object a, Object b) {
        if (a instanceof Double x && b instanceof Double y) return x.doubleValue() < y.doubleValue();
        if (a instanceof Float x && b instanceof Float y) return x.floatValue() < y.floatValue();
        return compareOrdered(a, b) < 0;
    }

    public static boolean lessOrEqual(Object a, Object b) {
        if (a instanceof Double x && b instanceof Double y) return x.doubleValue() <= y.doubleValue();
        if (a instanceof Float x && b instanceof Float y) return x.floatValue() <= y.floatValue();
        return compareOrdered(a, b) <= 0;
    }

    public static boolean greaterThan(Object a, Object b) {
        if (a instanceof Double x && b instanceof Double y) return x.doubleValue() > y.doubleValue();
        if (a instanceof Float x && b instanceof Float y) return x.floatValue() > y.floatValue();
        return compareOrdered(a, b) > 0;
    }

    public static boolean greaterOrEqual(Object a, Object b) {
        if (a instanceof Double x && b instanceof Double y) return x.doubleValue() >= y.doubleValue();
        if (a instanceof Float x && b instanceof Float y) return x.floatValue() >= y.floatValue();
        return compareOrdered(a, b) >= 0;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compareOrdered(Object a, Object b) {
        return ((Comparable) a).compareTo(b);
    }

    /** Hash consistent with Sprig's numeric equality, including signed zero. */
    public static int hashValue(Object value) {
        if (value instanceof Double d && d == 0.0d) return Double.hashCode(0.0d);
        if (value instanceof Float f && f == 0.0f) return Float.hashCode(0.0f);
        return value == null ? 0 : value.hashCode();
    }
}
