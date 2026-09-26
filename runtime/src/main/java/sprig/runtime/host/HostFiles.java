package sprig.runtime.host;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
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

    public static boolean exists(String path) { return Files.exists(Path.of(path), LinkOption.NOFOLLOW_LINKS); }
    public static boolean isDirectory(String path) { return Files.isDirectory(Path.of(path), LinkOption.NOFOLLOW_LINKS); }
    public static boolean isRegularFile(String path) { return Files.isRegularFile(Path.of(path), LinkOption.NOFOLLOW_LINKS); }
    public static void makeDirectory(String path) throws IOException { Files.createDirectories(Path.of(path)); }
    public static String join(String base, String child) { return Path.of(base).resolve(child).normalize().toString(); }
    public static String normalize(String path) { return Path.of(path).normalize().toString(); }
    public static String fileName(String path) { return Path.of(path).getFileName().toString(); }

    /** Typed indexed snapshot for an explicit Sprig List[String] copy adapter. */
    public static Directory directory(String path) throws IOException { return new Directory(listFiles(path)); }
    public static final class Directory {
        private final List<String> paths;
        private Directory(List<String> paths) { this.paths = paths; }
        public long size() { return paths.size(); }
        public String entry(long index) { return paths.get(Math.toIntExact(index)); }
    }

    /** Sorted snapshot; exposed as an opaque Java list until explicit adapters exist. */
    public static List<String> listFiles(String directory) throws IOException {
        try (var stream = Files.list(Path.of(directory))) {
            return stream.map(path -> path.toAbsolutePath().normalize().toString())
                    .sorted().toList();
        }
    }
}
