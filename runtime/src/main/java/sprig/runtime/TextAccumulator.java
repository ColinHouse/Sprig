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
