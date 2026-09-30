package sprig.runtime.host;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
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

    /** Lexical parent after normalization; a leaf or filesystem root has no parent. */
    public static String parent(String path) {
        Path parent = Path.of(path).normalize().getParent();
        return parent == null ? null : parent.toString();
    }

    /** Absolute normalized path; this does not resolve symbolic links or require existence. */
    public static String absolute(String path) {
        return Path.of(path).toAbsolutePath().normalize().toString();
    }

    /** Copy one regular, non-symlink file; existing targets are never overwritten. */
    public static void copyFile(String source, String target) throws IOException {
        Path from = Path.of(source);
        requireRegularFile(from, "copy source");
        Path to = Path.of(target);
        if (Files.exists(to, LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException(to.toString());
        Files.copy(from, to);
    }

    /** Move one regular, non-symlink file; existing targets are never overwritten. */
    public static void move(String source, String target) throws IOException {
        Path from = Path.of(source);
        requireRegularFile(from, "move source");
        Path to = Path.of(target);
        if (Files.exists(to, LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException(to.toString());
        Files.move(from, to);
    }

    /** Remove exactly one regular, non-symlink file; directories and links are rejected. */
    public static void removeFile(String path) throws IOException {
        Path file = Path.of(path);
        requireRegularFile(file, "remove target");
        Files.delete(file);
    }

    /**
     * Write a UTF-8 sibling temporary file, close it, then replace the target.
     * Atomic replacement is preferred; if the filesystem does not support it,
     * the fallback is a same-filesystem replacement move without crash-atomicity.
     * This method does not promise fsync/crash durability.
     */
    public static void atomicWriteUtf8(String path, String text) throws IOException {
        Path target = Path.of(path).toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent))
            throw new NoSuchFileException("Parent directory does not exist for " + target);
        String name = target.getFileName() == null ? "sprig" : target.getFileName().toString();
        Path temporary = Files.createTempFile(parent, "." + name + ".", ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Return a newly-created OS temporary file for ordinary application use. */
    public static String tempFile() throws IOException {
        return Files.createTempFile("sprig-", ".tmp").toAbsolutePath().normalize().toString();
    }

    private static void requireRegularFile(Path file, String description) throws IOException {
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(description + " must be an existing regular file: " + file);
        }
    }

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
