package sprig.runtime;

/**
 * Value of the built-in Sprig {@code Error} type. Declared recoverable errors
 * (`-> T throws Error`) become this exception; `catch error: Error` catches it.
 * Java runtime defects (ArithmeticException, NullPointerException, ...) are NOT
 * SprigError and stay unchecked unless an imported Java exception type is caught.
 *
 * <p>In Sprig, {@code toString()} on an Error is its message, as print shows it:
 * the compiler lowers it to {@link SprigRuntime#str}. This class keeps Java's
 * {@code toString()} on purpose: stack traces, host logs and the CLI's
 * {@code run --stacktrace} classification read its "sprig.runtime.SprigError:
 * message" header.
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
