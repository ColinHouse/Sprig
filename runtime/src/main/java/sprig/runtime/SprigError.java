package sprig.runtime;

/**
 * Value of the built-in Sprig {@code Error} type. Declared recoverable errors
 * (`-> T throws Error`) become this exception; `catch error: Error` catches it.
 * Java runtime defects (ArithmeticException, NullPointerException, ...) are NOT
 * SprigError and stay unchecked unless an imported Java exception type is caught.
 */
public class SprigError extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SprigError(String message) {
        super(message);
    }

    public SprigError(String message, Throwable cause) {
        super(message, cause);
    }
}
