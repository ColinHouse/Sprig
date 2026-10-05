package sprig.compiler.jvm;

import java.io.BufferedOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Text encoding of the CLI's and the program's standard streams.
 *
 * <p>A Windows console keeps the JVM's console code page, so text the console
 * can show displays correctly. Redirected output (pipes and files read by
 * editors, scripts, agents and {@code --json} consumers) is UTF-8, as it is on
 * Linux and macOS. JDK 17 already did this through {@code file.encoding};
 * JDK 19+ falls back to the ANSI code page ({@code native.encoding}) instead,
 * which replaces most non-Latin text with {@code ?}.
 */
public final class StandardStreams {
    private static final boolean WINDOWS = System.getProperty("os.name", "").startsWith("Windows");
    private static boolean utf8Stdout;

    private StandardStreams() {
    }

    /** Called once at CLI startup, before anything is written. */
    public static void configure() {
        if (!WINDOWS) return;
        if (!console("stdout")) {
            System.setOut(utf8(FileDescriptor.out));
            utf8Stdout = true;
        }
        if (!console("stderr")) System.setErr(utf8(FileDescriptor.err));
    }

    /** Whether a child JVM that inherits this process's stdout must be told to write UTF-8. */
    public static boolean utf8Stdout() {
        return utf8Stdout;
    }

    /**
     * The JDK records the console code page only for a console handle: JDK 17
     * as {@code sun.<stream>.encoding}, JDK 19+ as {@code <stream>.encoding}
     * spelled {@code ms936}/{@code cp437}/{@code UTF-8}, never equal to the
     * {@code native.encoding} spelling ({@code GBK}, {@code Cp1252}) it uses
     * for anything else. An explicit {@code -D<stream>.encoding} is kept too.
     */
    private static boolean console(String stream) {
        if (System.getProperty("sun." + stream + ".encoding") != null) return true;
        String encoding = System.getProperty(stream + ".encoding");
        return encoding != null && !encoding.equals(System.getProperty("native.encoding"));
    }

    private static PrintStream utf8(FileDescriptor descriptor) {
        return new PrintStream(new BufferedOutputStream(new FileOutputStream(descriptor), 128), true,
                StandardCharsets.UTF_8);
    }
}
