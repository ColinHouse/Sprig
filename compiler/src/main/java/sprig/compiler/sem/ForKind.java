package sprig.compiler.sem;

/** Iteration category for a {@code for} statement. */
public enum ForKind {
    LIST, MAP_KEYS, STRING,
    /** {@code for x in range(...)}: a counted loop; no list is built. */
    RANGE
}
