package sprig.compiler.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import sprig.compiler.gen.JavaGenerator;
import sprig.compiler.jvm.JvmClasspath;
import sprig.compiler.tooling.Catalog;

/**
 * Compiled classes of {@code sprig run} and {@code sprig test}, kept between
 * runs. The generated Java is a pure function of the checked program and the
 * compiler, so when the same program is run again the javac step (the larger
 * part of a run's start-up) is skipped and the classes of the previous run
 * are executed.
 *
 * <p>The key is a SHA-256 over everything javac sees: the compiler version,
 * every generated Java file by name and text, the digest of the runtime
 * sources (see {@link RuntimeClasses}), the classpath entries (path, size and
 * modification time) and the Java version. An entry is written next to the key under
 * {@code ~/.sprig/cache/javac} by moving a finished directory into place, so a
 * reader never sees half an entry; the newest {@value #KEEP} entries are kept.
 * {@code SPRIG_JAVAC_CACHE} names another directory, or {@code off} turns the
 * cache off, as {@code sprig run --no-cache} does for one run.
 */
public final class JavacCache {
    private static final int KEEP = 64;
    private static final String MARKER = "sprig-javac-cache.ok";

    private JavacCache() {
    }

    /** The cache directory, or null when the cache is off. */
    public static Path root() {
        String override = System.getenv("SPRIG_JAVAC_CACHE");
        if (override != null && override.trim().equalsIgnoreCase("off")) {
            return null;
        }
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        String home = System.getProperty("user.home");
        if (home == null || home.isBlank()) {
            return null;
        }
        return Path.of(home, ".sprig", "cache", "javac");
    }

    /** The key of this program's classes, or null when something javac depends on cannot be read. */
    public static String key(JavaGenerator.Output output, String runtimeDigest) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            feed(digest, "compiler " + Catalog.COMPILER_VERSION);
            feed(digest, "java " + System.getProperty("java.version"));
            feed(digest, "main " + output.mainClass);
            for (Map.Entry<String, String> entry : new TreeMap<>(output.sources).entrySet()) {
                feed(digest, "source " + entry.getKey());
                feed(digest, entry.getValue());
            }
            feed(digest, "runtime " + runtimeDigest);
            for (Path entry : JvmClasspath.entries()) {
                if (Files.exists(entry)) {
                    feed(digest, "classpath " + entry.toAbsolutePath() + " " + Files.size(entry) + " "
                            + Files.getLastModifiedTime(entry).toMillis());
                } else {
                    feed(digest, "classpath " + entry.toAbsolutePath() + " missing");
                }
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (IOException | NoSuchAlgorithmException | RuntimeException e) {
            return null;
        }
    }

    private static void feed(MessageDigest digest, String text) {
        digest.update(text.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    /** The classes directory of a finished entry, or null. */
    public static Path lookup(Path root, String key) {
        if (root == null || key == null) {
            return null;
        }
        Path entry = root.resolve(key);
        if (!Files.isRegularFile(entry.resolve(MARKER)) || !Files.isDirectory(entry.resolve("classes"))) {
            return null;
        }
        try {
            Files.setLastModifiedTime(entry, FileTime.fromMillis(System.currentTimeMillis()));
        } catch (IOException ignored) {
            // The entry is still usable; only its recency for pruning is lost.
        }
        return entry.resolve("classes");
    }

    /**
     * Stores the classes just compiled. The copy is built beside the entry and
     * moved into place in one step; when another run stored the same key first,
     * its entry is kept. Failures are ignored: the cache only saves time.
     */
    public static void store(Path root, String key, Path classesDir) {
        if (root == null || key == null) {
            return;
        }
        Path entry = root.resolve(key);
        if (Files.exists(entry)) {
            return;
        }
        Path staging = null;
        try {
            Files.createDirectories(root);
            staging = Files.createTempDirectory(root, "staging-");
            copyTree(classesDir, staging.resolve("classes"));
            Files.writeString(staging.resolve(MARKER), key + "\n", StandardCharsets.UTF_8);
            try {
                Files.move(staging, entry, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException raced) {
                Main.deleteRecursively(staging);
            }
            prune(root, KEEP);
        } catch (IOException | RuntimeException e) {
            if (staging != null) {
                try {
                    Main.deleteRecursively(staging);
                } catch (IOException ignored) {
                    // Best effort only.
                }
            }
        }
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> stream = Files.walk(from)) {
            for (Path path : stream.toList()) {
                Path target = to.resolve(from.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /** Keeps the {@code keep} most recently used entries; staging directories older than an hour are leftovers. */
    static void prune(Path root, int keep) throws IOException {
        List<Path> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.list(root)) {
            for (Path path : stream.toList()) {
                if (!Files.isDirectory(path)) continue;
                if (path.getFileName().toString().startsWith("staging-")) {
                    if (Files.getLastModifiedTime(path).toMillis() < System.currentTimeMillis() - 3_600_000L) {
                        Main.deleteRecursively(path);
                    }
                    continue;
                }
                entries.add(path);
            }
        }
        if (entries.size() <= keep) {
            return;
        }
        entries.sort(Comparator.comparingLong(path -> {
            try {
                return Files.getLastModifiedTime(path).toMillis();
            } catch (IOException e) {
                return 0L;
            }
        }));
        for (Path stale : entries.subList(0, entries.size() - keep)) {
            Main.deleteRecursively(stale);
        }
    }
}
