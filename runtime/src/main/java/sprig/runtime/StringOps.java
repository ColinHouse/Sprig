package sprig.runtime;

/**
 * Ordinary Sprig String position semantics operate on Unicode code points, not
 * JVM UTF-16 code units and not grapheme clusters. A textual element is a
 * one-code-point {@link String}; Sprig has no Char type.
 */
public final class StringOps {
    private StringOps() {}

    public static long length(String value) {
        return value.codePointCount(0, value.length());
    }

    public static String elementAt(String value, int index) {
        return new String(Character.toChars(value.codePointAt(codePointOffset(value, index))));
    }

    public static long codePointAt(String value, int index) {
        return value.codePointAt(codePointOffset(value, index));
    }

    public static String substring(String value, int start) {
        int count = value.codePointCount(0, value.length());
        if (start < 0 || start > count) {
            throw new StringIndexOutOfBoundsException(
                    "index " + start + ", code point length " + count);
        }
        return value.substring(value.offsetByCodePoints(0, start));
    }

    public static String substring(String value, int start, int end) {
        int count = value.codePointCount(0, value.length());
        if (start < 0 || end > count || start > end) {
            throw new StringIndexOutOfBoundsException(
                    "begin " + start + ", end " + end + ", code point length " + count);
        }
        return value.substring(value.offsetByCodePoints(0, start), value.offsetByCodePoints(0, end));
    }

    public static long indexOf(String value, String needle) {
        int offset = value.indexOf(needle);
        return offset < 0 ? -1L : value.codePointCount(0, offset);
    }

    /** One {@link String} per Unicode code point, in order. */
    public static Iterable<String> codePoints(String value) {
        return () -> value.codePoints()
                .mapToObj(codePoint -> new String(Character.toChars(codePoint)))
                .iterator();
    }

    private static int codePointOffset(String value, int index) {
        int count = value.codePointCount(0, value.length());
        if (index < 0 || index >= count) {
            throw new StringIndexOutOfBoundsException(
                    "index " + index + ", code point length " + count);
        }
        return value.offsetByCodePoints(0, index);
    }
}
