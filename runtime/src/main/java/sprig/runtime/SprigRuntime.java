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

    /** Sprig {@code print}: one line, Sprig value formatting. */
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

    /** Deep structural equality used by Sprig {@code ==} on reference values. */
    /**
     * {@code in} helpers. Arguments are evaluated left-to-right, so the element
     * expression runs before the container expression exactly as written.
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

    /** Hash consistent with Sprig's numeric equality, including signed zero. */
    public static int hashValue(Object value) {
        if (value instanceof Double d && d == 0.0d) return Double.hashCode(0.0d);
        if (value instanceof Float f && f == 0.0f) return Float.hashCode(0.0f);
        return value == null ? 0 : value.hashCode();
    }
}
