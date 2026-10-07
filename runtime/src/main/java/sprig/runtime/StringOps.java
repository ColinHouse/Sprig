package sprig.runtime;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Ordinary Sprig String position semantics operate on Unicode code points, not
 * JVM UTF-16 code units and not grapheme clusters. A textual element is a
 * one-code-point {@link String}; Sprig has no Char type.
 *
 * <p>A JVM string is indexed by UTF-16 unit, so finding code point {@code i}
 * means counting from the start whenever the text may hold a surrogate pair.
 * When the code-point count equals the UTF-16 length, the text has no
 * surrogate pair: every code point is in the Basic Multilingual Plane (Latin,
 * CJK and most scripts) and is one {@code char}, so code-point index {@code i}
 * is char offset {@code i}. Counting is itself linear for text outside
 * Latin-1, so the count of the last long string indexed is remembered, and a
 * loop over the same text pays for one counting pass instead of one per
 * access. Text with a supplementary code point (an emoji, say) still counts to
 * the requested offset on every access.
 */
public final class StringOps {
    private StringOps() {}

    /**
     * Strings shorter than this many chars are counted directly and never
     * cached: counting them is cheap, and leaving them out keeps a long text's
     * entry in place while a loop also asks for a short separator's or
     * element's length.
     */
    private static final int CACHED_LENGTH = 32;

    /** The one-character strings of U+0000 to U+007F, shared instead of allocated per element. */
    private static final String[] ASCII = new String[128];

    static {
        for (char c = 0; c < ASCII.length; c++) {
            ASCII[c] = String.valueOf(c).intern();
        }
    }

    /** A string with a surrogate pair and its code-point count, which is less than its length. */
    private record Counted(String text, int codePoints) {}

    /**
     * The long string indexed last: the string itself when it has no surrogate
     * pair (its count is its length), otherwise a {@link Counted} record of it.
     * Either is immutable and replaces the previous entry as a whole, so a
     * thread reads one consistent entry even while tasks on other threads
     * index other strings. The entry answers only for the very same string
     * object: identity is the key, never equals, so it cannot answer for a
     * different string (equal contents in another object are just a miss).
     * The entry keeps that one string reachable until another long string is
     * indexed.
     */
    private static volatile Object last;

    /** The code-point count of {@code value}; long strings go through the cache. */
    private static int count(String value) {
        int length = value.length();
        if (length < CACHED_LENGTH) {
            return value.codePointCount(0, length);
        }
        Object seen = last;
        if (seen == value) {
            return length;
        }
        if (seen instanceof Counted counted && counted.text == value) {
            return counted.codePoints;
        }
        int count = value.codePointCount(0, length);
        last = count == length ? value : new Counted(value, count);
        return count;
    }

    public static long length(String value) {
        return count(value);
    }

    public static String elementAt(String value, int index) {
        int count = count(value);
        checkIndex(index, count);
        if (count == value.length()) {
            return element(value.charAt(index));
        }
        return element(value.codePointAt(value.offsetByCodePoints(0, index)));
    }

    public static long codePointAt(String value, int index) {
        int count = count(value);
        checkIndex(index, count);
        if (count == value.length()) {
            return value.charAt(index);
        }
        return value.codePointAt(value.offsetByCodePoints(0, index));
    }

    public static String substring(String value, int start) {
        int count = count(value);
        if (start < 0 || start > count) {
            throw new StringIndexOutOfBoundsException(
                    "index " + start + ", code point length " + count);
        }
        return value.substring(offset(value, count, start));
    }

    public static String substring(String value, int start, int end) {
        int count = count(value);
        if (start < 0 || end > count || start > end) {
            throw new StringIndexOutOfBoundsException(
                    "begin " + start + ", end " + end + ", code point length " + count);
        }
        return value.substring(offset(value, count, start), offset(value, count, end));
    }

    public static long indexOf(String value, String needle) {
        int offset = value.indexOf(needle);
        return offset < 0 ? -1L : codePointIndex(value, offset);
    }

    /** Code-point index of the last occurrence of {@code needle}, or -1. */
    public static long lastIndexOf(String value, String needle) {
        int offset = value.lastIndexOf(needle);
        return offset < 0 ? -1L : codePointIndex(value, offset);
    }

    /** One {@link String} per Unicode code point, in order. */
    public static Iterable<String> codePoints(String value) {
        return () -> new CodePointIterator(value);
    }

    /** A one-code-point string; ASCII ones come from the shared table. */
    private static String element(int codePoint) {
        return codePoint < ASCII.length ? ASCII[codePoint] : Character.toString(codePoint);
    }

    private static void checkIndex(int index, int count) {
        if (index < 0 || index >= count) {
            throw new StringIndexOutOfBoundsException(
                    "index " + index + ", code point length " + count);
        }
    }

    /** The char offset of code point {@code index}, already checked against {@code count}. */
    private static int offset(String value, int count, int index) {
        return count == value.length() ? index : value.offsetByCodePoints(0, index);
    }

    /**
     * The code-point index of a char offset found by a search. A search
     * records nothing: counting the whole string would cost more than counting
     * up to the match, so only an entry already there is used.
     */
    private static long codePointIndex(String value, int offset) {
        if (last == value) {
            return offset;
        }
        return value.codePointCount(0, offset);
    }

    /** Walks the string once: one code point per step, no stream and no int[] per element. */
    private static final class CodePointIterator implements Iterator<String> {
        private final String text;
        private int offset;

        CodePointIterator(String text) {
            this.text = text;
        }

        @Override
        public boolean hasNext() {
            return offset < text.length();
        }

        @Override
        public String next() {
            int at = offset;
            if (at >= text.length()) {
                throw new NoSuchElementException();
            }
            int codePoint = text.codePointAt(at);
            offset = at + Character.charCount(codePoint);
            return element(codePoint);
        }
    }
}
