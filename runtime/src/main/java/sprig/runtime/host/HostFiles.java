package sprig.runtime.host;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Explicit platform services for a future Sprig-written compiler frontend.
 * No lexer, parser, symbol, type or code-generation semantics live here. */
public final class HostFiles {
    private HostFiles() {}

    public static String readUtf8(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    public static void writeUtf8(String path, String text) throws IOException {
        Files.writeString(Path.of(path), text, StandardCharsets.UTF_8);
    }

    public static boolean fileExists(String path) {
        return Files.isRegularFile(Path.of(path));
    }

    public static String canonicalPath(String path) throws IOException {
        return Path.of(path).toRealPath().toString();
    }

    /** Sorted snapshot; exposed as an opaque Java list until explicit adapters exist. */
    public static List<String> listFiles(String directory) throws IOException {
        try (var stream = Files.list(Path.of(directory))) {
            return stream.map(path -> path.toAbsolutePath().normalize().toString())
                    .sorted().toList();
        }
    }
}
