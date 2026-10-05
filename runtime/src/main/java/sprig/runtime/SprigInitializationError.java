package sprig.runtime;

/** Unchecked failure: a top-level binding was used before its initializer ran. */
public final class SprigInitializationError extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public SprigInitializationError(String message) {
        super(message);
    }
}
