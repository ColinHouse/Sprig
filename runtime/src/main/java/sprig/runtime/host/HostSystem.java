package sprig.runtime.host;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.DateTimeException;
import sprig.runtime.SprigError;

/** Process/time boundary. The generated JVM entry point installs a copied argument vector. */
public final class HostSystem {
    private static volatile String[] arguments = new String[0];
    private static BufferedReader input;
    private static Charset inputCharset = StandardCharsets.UTF_8;
    private HostSystem() {}
    public static void setArguments(String[] values) { arguments = values.clone(); }
    public static long argumentCount() { return arguments.length; }
    public static String argument(long index) { return arguments[Math.toIntExact(index)]; }
    public static String environment(String name) { return System.getenv(name); }

    /** Ends the process. Statuses outside 0..255 are not portable, so they are a caller bug. */
    public static void exit(long status) {
        if (status < 0 || status > 255) {
            throw new IllegalArgumentException("Exit status must be between 0 and 255: " + status);
        }
        System.out.flush();
        System.err.flush();
        System.exit((int) status);
    }

    public static void printError(String text) { System.err.println(text); }

    /** The next line without its line ending, or null at the end of input. */
    public static synchronized String readLine() {
        try {
            return input().readLine();
        } catch (IOException failure) {
            throw inputFailure(failure);
        }
    }

    /** Everything left on standard input, unchanged. */
    public static synchronized String readAll() {
        try {
            StringBuilder text = new StringBuilder();
            char[] buffer = new char[8192];
            BufferedReader reader = input();
            for (int count = reader.read(buffer); count >= 0; count = reader.read(buffer)) {
                text.append(buffer, 0, count);
            }
            return text.toString();
        } catch (IOException failure) {
            throw inputFailure(failure);
        }
    }

    // One reader for the whole process, so read_line and read_all share its buffer.
    // Bytes that are not text in the input encoding are an error, not a replacement character.
    private static BufferedReader input() {
        if (input == null) {
            inputCharset = inputCharset();
            input = new BufferedReader(new InputStreamReader(System.in, inputCharset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)));
        }
        return input;
    }

    /**
     * Piped or redirected input is UTF-8, like every file Sprig reads. A
     * terminal delivers its own code page (GBK on a Chinese Windows console),
     * which the JDK reports through the console.
     */
    private static Charset inputCharset() {
        Console console = System.console();
        if (console == null) return StandardCharsets.UTF_8;
        try {
            // JDK 22+ can return a console for redirected streams.
            if (Boolean.FALSE.equals(Console.class.getMethod("isTerminal").invoke(console))) {
                return StandardCharsets.UTF_8;
            }
        } catch (ReflectiveOperationException beforeJdk22) {
            // An older JDK only returns a console for a terminal.
        }
        return console.charset();
    }

    private static SprigError inputFailure(IOException failure) {
        String reason = failure instanceof CharacterCodingException ? "it is not valid " + inputCharset.name()
                : failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        return new SprigError("Cannot read standard input: " + reason, failure);
    }

    public static long epochMillis() { return System.currentTimeMillis(); }
    public static String utcNow() { return Instant.now().toString(); }
    public static String formatUtc(long epochMillis) {
        try {
            return Instant.ofEpochMilli(epochMillis).toString();
        } catch (DateTimeException | ArithmeticException failure) {
            throw new SprigError("Invalid epoch-millisecond value: " + epochMillis, failure);
        }
    }
    public static long parseUtc(String value) {
        try {
            return Instant.parse(value).toEpochMilli();
        } catch (DateTimeException | ArithmeticException failure) {
            throw new SprigError("Invalid ISO-8601 timestamp or epoch-millisecond range: " + value, failure);
        }
    }
}
