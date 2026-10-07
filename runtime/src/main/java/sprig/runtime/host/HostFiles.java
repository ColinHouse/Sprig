package sprig.runtime.host;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;

/** Explicit platform services for a future Sprig-written compiler frontend.
 * No lexer, parser, symbol, type or code-generation semantics live here. */
public final class HostFiles {
    private HostFiles() {}

    /**
     * A failure whose reason is already in plain words. The common cases are
     * checked before the operation, so the reason does not depend on the JDK's
     * message text or on the operating system's wording.
     */
    public static final class Failure extends IOException {
        public Failure(String reason) { super(reason); }
    }

    /** The reason an operation failed, for @std/files: "cannot read data/x.txt: " + reason(problem). */
    public static String reason(IOException problem) {
        if (problem instanceof Failure) return problem.getMessage();
        if (problem instanceof CharacterCodingException) return "not valid UTF-8";
        if (problem instanceof NoSuchFileException) return "no such file";
        if (problem instanceof FileAlreadyExistsException) return "it already exists";
        if (problem instanceof DirectoryNotEmptyException) return "the directory is not empty";
        if (problem instanceof NotDirectoryException) return "it is not a directory";
        if (problem instanceof AccessDeniedException) return "access denied";
        String message = problem.getMessage();
        return message == null || message.isBlank() ? problem.getClass().getSimpleName() : message;
    }

    public static String readUtf8(String path) throws IOException {
        Path file = Path.of(path);
        if (Files.isDirectory(file)) throw new Failure("it is a directory");
        if (!Files.exists(file)) throw new Failure("no such file");
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    public static void writeUtf8(String path, String text) throws IOException {
        Path file = Path.of(path);
        requireWritable(file);
        Files.writeString(file, text, StandardCharsets.UTF_8);
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
    public static void makeDirectory(String path) throws IOException {
        Path directory = Path.of(path);
        if (Files.exists(directory) && !Files.isDirectory(directory)) throw new Failure("a file with that name already exists");
        Files.createDirectories(directory);
    }
    public static String join(String base, String child) { return Path.of(base).resolve(child).normalize().toString(); }
    public static String normalize(String path) { return Path.of(path).normalize().toString(); }
    /** The last name of the path, or null for a root such as / that has none. */
    public static String fileName(String path) {
        Path name = Path.of(path).getFileName();
        return name == null ? null : name.toString();
    }

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
        requireRegularFile(from, "the source");
        Path to = Path.of(target);
        requireNewTarget(to);
        Files.copy(from, to);
    }

    /** Move one regular, non-symlink file; existing targets are never overwritten. */
    public static void move(String source, String target) throws IOException {
        Path from = Path.of(source);
        requireRegularFile(from, "the source");
        Path to = Path.of(target);
        requireNewTarget(to);
        Files.move(from, to);
    }

    /** Remove exactly one regular, non-symlink file; directories and links are rejected. */
    public static void removeFile(String path) throws IOException {
        Path file = Path.of(path);
        requireRegularFile(file, null);
        Files.delete(file);
    }

    /**
     * Write a UTF-8 sibling temporary file, close it, then replace the target.
     * A symbolic link at the path is followed, as {@link #writeUtf8} follows
     * it: the file it leads to is the target, and the link stays. A replaced
     * file keeps its POSIX permissions where the file system has them; a new
     * one gets what writeUtf8 would create it with (rw-rw-rw- less the
     * process umask).
     * Atomic replacement is preferred; if the filesystem does not support it,
     * the fallback is a same-filesystem replacement move without crash-atomicity.
     * This method does not promise fsync/crash durability.
     */
    public static void atomicWriteUtf8(String path, String text) throws IOException {
        Path target = linkTarget(Path.of(path).toAbsolutePath());
        requireWritable(target);
        Path parent = target.getParent();
        String name = target.getFileName() == null ? "sprig" : target.getFileName().toString();
        boolean posix = target.getFileSystem().supportedFileAttributeViews().contains("posix");
        Set<PosixFilePermission> kept = null;
        if (posix) {
            try {
                kept = Files.getPosixFilePermissions(target);
            } catch (NoSuchFileException absent) {
                // a new file
            }
        }
        // A replaced file's new text stays owner-only (createTempFile's default) until its
        // permissions are copied; a new file starts with the ones it will keep.
        Path temporary = posix && kept == null
                ? Files.createTempFile(parent, "." + name + ".", ".tmp", NEW_FILE)
                : Files.createTempFile(parent, "." + name + ".", ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            if (kept != null) {
                try {
                    Files.setPosixFilePermissions(temporary, kept);
                } catch (IOException refused) {
                    // As with Files.copy and COPY_ATTRIBUTES: a file system that refuses
                    // permissions (FAT, some network mounts) does not fail the write.
                }
            }
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

    /** What Files.writeString creates a new file with, before the process umask applies. */
    private static final FileAttribute<Set<PosixFilePermission>> NEW_FILE =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-rw-rw-"));

    /** Links in a row that linkTarget follows; Linux gives up after as many (ELOOP). */
    private static final int MAX_LINKS = 40;

    /**
     * The file that writing to an absolute path reaches: a symbolic link at the
     * path is followed, link after link, to the file it names, which need not
     * exist yet. Links among the parent directories are left to the system.
     */
    private static Path linkTarget(Path file) throws IOException {
        Path current = file;
        for (int links = 0; Files.isSymbolicLink(current); links++) {
            if (links == MAX_LINKS) throw new Failure("too many levels of symbolic links");
            current = current.resolveSibling(Files.readSymbolicLink(current));
        }
        return current;
    }

    /** Only an existing regular file that is not a symbolic link; role is "the source", or null for the path itself. */
    private static void requireRegularFile(Path file, String role) throws IOException {
        String subject = role == null ? "it" : role;
        if (Files.isSymbolicLink(file)) throw new Failure(subject + " is a symbolic link");
        if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) throw new Failure(subject + " is a directory");
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
            throw new Failure(role == null ? "no such file" : role + " does not exist");
    }

    private static void requireNewTarget(Path target) throws IOException {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new Failure("the target already exists");
        Path parent = target.toAbsolutePath().normalize().getParent();
        if (parent != null && !Files.isDirectory(parent)) throw new Failure("the target's parent directory does not exist");
    }

    private static void requireWritable(Path file) throws IOException {
        if (Files.isDirectory(file)) throw new Failure("it is a directory");
        Path parent = file.toAbsolutePath().normalize().getParent();
        if (parent == null || !Files.isDirectory(parent)) throw new Failure("the parent directory does not exist");
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
        Path folder = Path.of(directory);
        if (!Files.exists(folder)) throw new Failure("no such directory");
        if (!Files.isDirectory(folder)) throw new Failure("it is not a directory");
        try (var stream = Files.list(folder)) {
            return stream.map(path -> path.toAbsolutePath().normalize().toString())
                    .sorted().toList();
        }
    }
}
