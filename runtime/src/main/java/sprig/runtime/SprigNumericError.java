package sprig.runtime;

/** Unchecked failure of a checked numeric operation. */
public final class SprigNumericError extends ArithmeticException {
    private static final long serialVersionUID = 1L;

    public SprigNumericError(String message) {
        super(message);
    }
}
