package sprig.runtime;

import java.util.ArrayList;
import java.util.List;

/** Formatted output and value helpers used by generated Java code. */
public final class SprigRuntime {
    private SprigRuntime() {
    }

    /** Sprig {@code print}: one line, Sprig value formatting. */
    public static void print(Object value) {
        System.out.println(format(value));
    }

    /** Canonical Sprig value formatting used by print, toString and string +. */
    public static String str(Object value) {
        return format(value);
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
