package sprig.runtime;

/**
 * A function's local String variable that a loop appends to.
 *
 * <p>A String is immutable, so {@code text += part} written as a concatenation
 * copies everything accumulated so far on every pass: a loop that appends n
 * parts copies on the order of n squared characters. The generated code keeps
 * such a variable here instead. An append goes to a builder, which copies only
 * the new part; the first read after an append takes the builder's text once
 * and keeps it until the next append, so repeated reads cost nothing.
 *
 * <p>Its length and whether it is empty are answered from the builder, so a
 * loop that asks "is this the first part?" before each append takes no text
 * out either.
 *
 * <p>Only a local variable is kept this way: no other code can reach it, and a
 * lambda cannot capture a local {@code var}, so one thread uses the object.
 * The text a read returns is the text the concatenation would have produced,
 * including Java's {@code "null"} for a null reference.
 */
public final class TextAccumulator {
    /** The variable's value while {@link #ahead} is false. */
    private String text;
    /** Holds the value from the first append on; null before it and after {@link #set}. */
    private StringBuilder builder;
    /** True while the builder holds appends that {@link #text} does not. */
    private boolean ahead;
    /** How many chars at the start of the builder {@link #codePoints} covers. */
    private int countedChars;
    /** The code points in the builder's first {@link #countedChars} chars. */
    private int codePoints;

    public TextAccumulator(String initial) {
        this.text = initial;
    }

    /** The variable's current value. */
    public String get() {
        if (ahead) {
            text = builder.toString();
            ahead = false;
        }
        return text;
    }

    /** A plain assignment: the variable starts over from this value. */
    public void set(String value) {
        text = value;
        builder = null;
        ahead = false;
        countedChars = 0;
        codePoints = 0;
    }

    /**
     * The variable's length in code points, as {@link StringOps#length}
     * gives it. Only what was appended since the last call is counted, so
     * asking on every pass of a loop costs the length of each part once.
     */
    public long length() {
        StringBuilder current = builder;
        if (current == null) {
            return StringOps.length(text);
        }
        int from = countedChars;
        int end = current.length();
        if (from < end) {
            // A pair that the previous count split was counted as a lone high
            // surrogate; the low half that now follows it adds no code point.
            if (from > 0 && Character.isHighSurrogate(current.charAt(from - 1))
                    && Character.isLowSurrogate(current.charAt(from))) {
                codePoints--;
            }
            codePoints += Character.codePointCount(current, from, end);
            countedChars = end;
        }
        return codePoints;
    }

    /** Whether the variable is the empty text; a null reference is not. */
    public boolean isEmpty() {
        StringBuilder current = builder;
        return current != null ? current.length() == 0 : text != null && text.isEmpty();
    }

    private StringBuilder appending() {
        StringBuilder current = builder;
        if (current == null) {
            current = new StringBuilder().append(text);
            builder = current;
        }
        ahead = true;
        return current;
    }

    public void append(String part) {
        appending().append(part);
    }

    public void append(long part) {
        appending().append(part);
    }

    public void append(int part) {
        appending().append(part);
    }

    public void append(double part) {
        appending().append(part);
    }

    public void append(float part) {
        appending().append(part);
    }

    public void append(boolean part) {
        appending().append(part);
    }

    /** Any other reference joins as Java's string concatenation joins it. */
    public void append(Object part) {
        appending().append(part);
    }
}
