package sprig.compiler.cli;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Stream;
import sprig.compiler.diag.Codes;
import sprig.compiler.diag.Diagnostic;
import sprig.compiler.diag.Diagnostics;
import sprig.compiler.diag.Phase;
import sprig.compiler.diag.Span;
import sprig.compiler.jvm.JavacRunner;

/**
 * The runtime classes every program's output carries, compiled once per
 * runtime and JDK instead of once per program.
 *
 * <p>{@code scripts/build.py} compiles the runtime sources into
 * {@code build/runtime-classes}, and the SDK ships that directory as
 * {@code lib/runtime-classes}. javac then compiles only a program's generated
 * sources, with these classes on its classpath, and the classes are copied
 * next to the program's: the output holds the same files, byte for byte, as
 * when the runtime sources were compiled together with every program.
 *
 * <p>Equal bytes need the same javac, so a shipped directory serves only the
 * Java version that compiled it, which its {@value #STAMP} records with the
 * digest of the sources and the javac options. Another JDK, a checkout whose
 * runtime sources changed after the build, or a layout without the directory
 * compiles the runtime once into {@code ~/.sprig/cache/runtime}, keyed the same
 * way. When even that cache cannot be written, the runtime sources are compiled
 * together with the program, as before.
 *
 * <p>The digest also stands for the runtime in the javac cache key. An SDK
 * ships its sources and its classes from one build, so its stamp is trusted
 * and the sources are not read; a checkout's sources can be edited after a
 * build, so there the digest is computed from them.
 */
public final class RuntimeClasses {
    static final String DIRECTORY = "runtime-classes";
    static final String STAMP = "sprig-runtime.properties";
    private static final int KEEP = 16;
    private static final Map<Path, RuntimeClasses> LOCATED = new HashMap<>();

    private final Path sourceDir;
    private final String digest;
    private final Path shipped;
    private Path classes;
    private boolean resolved;

    private RuntimeClasses(Path sourceDir, String digest, Path shipped) {
        this.sourceDir = sourceDir;
        this.digest = digest;
        this.shipped = shipped;
    }

    /** The runtime this compiler compiles programs against, or null when its sources cannot be found. */
    static synchronized RuntimeClasses locate() throws IOException {
        Path sourceDir = Main.runtimeSourceDir();
        if (sourceDir == null) {
            return null;
        }
        RuntimeClasses known = LOCATED.get(sourceDir);
        if (known != null) {
            return known;
        }
        // <home>/runtime/src/main/java: the SDK or checkout root is four levels up.
        Path home = sourceDir.resolve("../../../..").normalize();
        String digest;
        Path shipped = null;
        Stamp sdk = Stamp.read(home.resolve("lib").resolve(DIRECTORY));
        if (sdk != null) {
            digest = sdk.digest;
            if (sdk.serves(digest)) shipped = sdk.classes;
        } else {
            digest = digest(sourceDir, listSources(sourceDir));
            Stamp checkout = Stamp.read(home.resolve("build").resolve(DIRECTORY));
            if (checkout != null && checkout.serves(digest)) shipped = checkout.classes;
        }
        RuntimeClasses located = new RuntimeClasses(sourceDir, digest, shipped);
        LOCATED.put(sourceDir, located);
        return located;
    }

    /** The error a command reports when it cannot find the runtime sources. */
    static void reportMissing(Diagnostics diagnostics) {
        diagnostics.error(Codes.JVM_INTERNAL, Phase.JVM,
                "Sprig runtime sources not found; set -Dsprig.home or build via scripts/build.sh",
                null, null);
    }

    /** SHA-256 of the runtime sources: each relative path, its length and its bytes, in path order. */
    String digest() {
        return digest;
    }

    /** The precompiled classes shipped beside the compiler, when they serve this JVM; otherwise null. */
    Path shippedClasses() {
        return shipped;
    }

    /**
     * Compiles a program's generated sources against the runtime classes and
     * copies those classes into {@code classesDir}, which then holds what
     * compiling the runtime sources with the program produced.
     */
    boolean compileProgram(Path classesDir, List<Path> programSources, Diagnostics diagnostics,
                           Map<Path, Map<Integer, Span>> lineMaps, Map<Path, String> uris) throws IOException {
        Path runtime = classes();
        if (runtime != null) {
            if (!JavacRunner.compile(classesDir, programSources, List.of(runtime), diagnostics, lineMaps, uris)) {
                return false;
            }
            try {
                copyTree(runtime, classesDir);
                return true;
            } catch (IOException e) {
                // A cache entry another process pruned meanwhile: compile the runtime from its sources.
                deleteRecursively(classesDir);
            }
        }
        List<Path> sources = new ArrayList<>(programSources);
        sources.addAll(listSources(sourceDir));
        return JavacRunner.compile(classesDir, sources, diagnostics, lineMaps, uris);
    }

    /**
     * The compiled runtime for this JVM: the shipped classes, or an entry of the
     * user cache compiled on first use. Null when neither is available; the
     * runtime sources are then compiled with the program.
     */
    synchronized Path classes() {
        if (!resolved) {
            resolved = true;
            classes = shipped != null ? shipped : cached();
        }
        return classes;
    }

    private Path cached() {
        Path root = cacheRoot();
        if (root == null) {
            return null;
        }
        Path entry = root.resolve(cacheKey(digest));
        try {
            Stamp stamp = Stamp.read(entry);
            if (stamp != null && stamp.serves(digest)) {
                try {
                    Files.setLastModifiedTime(entry, FileTime.fromMillis(System.currentTimeMillis()));
                } catch (IOException ignored) {
                    // Still usable; only its recency for pruning is lost.
                }
                return stamp.classes;
            }
            List<Path> sources = listSources(sourceDir);
            // Never file classes under a digest their sources do not have.
            if (!digest(sourceDir, sources).equals(digest)) {
                return null;
            }
            Files.createDirectories(root);
            Path staging = Files.createTempDirectory(root, "staging-");
            try {
                if (!compileInto(staging, sources, digest, new Diagnostics())) {
                    // The sources do not compile; compiling them with the program reports why.
                    return null;
                }
                try {
                    Files.move(staging, entry, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException raced) {
                    // Another process stored the same entry first and it holds the same
                    // classes; an entry without a stamp is broken and is replaced.
                    if (Stamp.read(entry) == null) {
                        deleteRecursively(entry);
                        Files.move(staging, entry, StandardCopyOption.ATOMIC_MOVE);
                    }
                }
            } finally {
                deleteRecursively(staging);
            }
            JavacCache.prune(root, KEEP);
            stamp = Stamp.read(entry);
            return stamp != null && stamp.serves(digest) ? stamp.classes : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** The user cache of runtime classes, or null without a home directory. */
    static Path cacheRoot() {
        String home = System.getProperty("user.home");
        if (home == null || home.isBlank()) {
            return null;
        }
        return Path.of(home, ".sprig", "cache", "runtime");
    }

    private static String cacheKey(String digest) {
        MessageDigest sha = sha256();
        sha.update(("runtime " + digest + "\0java " + javaVersion() + "\0options " + options())
                .getBytes(StandardCharsets.UTF_8));
        return hex(sha.digest());
    }

    /** Compiles the sources into {@code entry/classes} and writes the stamp beside them. */
    private static boolean compileInto(Path entry, List<Path> sources, String digest, Diagnostics diagnostics)
            throws IOException {
        if (!JavacRunner.compileRuntime(entry.resolve("classes"), sources, diagnostics) || diagnostics.hasErrors()) {
            return false;
        }
        Files.writeString(entry.resolve(STAMP), "# The Sprig runtime sources compiled by one javac.\n"
                + "digest=" + digest + "\n"
                + "java=" + javaVersion() + "\n"
                + "options=" + options() + "\n", StandardCharsets.UTF_8);
        return true;
    }

    static String digest(Path sourceDir, List<Path> sources) throws IOException {
        Map<String, Path> byName = new TreeMap<>();
        for (Path source : sources) {
            byName.put(sourceDir.relativize(source).toString().replace('\\', '/'), source);
        }
        MessageDigest sha = sha256();
        for (Map.Entry<String, Path> source : byName.entrySet()) {
            byte[] bytes = Files.readAllBytes(source.getValue());
            sha.update((source.getKey() + "\0" + bytes.length + "\0").getBytes(StandardCharsets.UTF_8));
            sha.update(bytes);
        }
        return hex(sha.digest());
    }

    private static List<Path> listSources(Path sourceDir) throws IOException {
        try (Stream<Path> stream = Files.walk(sourceDir)) {
            return stream.filter(path -> path.toString().endsWith(".java") && Files.isRegularFile(path))
                    .sorted()
                    .toList();
        }
    }

    private static String javaVersion() {
        return System.getProperty("java.version");
    }

    private static String options() {
        return String.join(" ", JavacRunner.OPTIONS);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    static void copyTree(Path from, Path to) throws IOException {
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

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            for (Path file : stream.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        }
    }

    /** A compiled runtime: its stamp and its classes directory. */
    private record Stamp(String digest, String java, String options, Path classes) {
        static Stamp read(Path entry) {
            Path file = entry.resolve(STAMP);
            Path classes = entry.resolve("classes");
            if (!Files.isRegularFile(file) || !Files.isDirectory(classes)) {
                return null;
            }
            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (IOException | IllegalArgumentException e) {
                return null;
            }
            String digest = properties.getProperty("digest");
            String java = properties.getProperty("java");
            String options = properties.getProperty("options");
            if (digest == null || java == null || options == null) {
                return null;
            }
            return new Stamp(digest, java, options, classes);
        }

        /** Whether these classes are what this JVM's javac makes of sources with that digest. */
        boolean serves(String sourceDigest) {
            return digest.equals(sourceDigest) && java.equals(javaVersion()) && options.equals(options());
        }
    }

    /**
     * {@code scripts/build.py}: compiles the runtime sources (first argument)
     * into a directory (second argument) of classes and a stamp, replacing it.
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("usage: RuntimeClasses <runtime source directory> <output directory>");
            System.exit(2);
        }
        Path sourceDir = Path.of(args[0]).toAbsolutePath().normalize();
        Path target = Path.of(args[1]).toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        Path staging = Files.createTempDirectory(target.getParent(), target.getFileName() + "-");
        int status = 0;
        try {
            List<Path> sources = listSources(sourceDir);
            Diagnostics diagnostics = new Diagnostics();
            if (sources.isEmpty() || !compileInto(staging, sources, digest(sourceDir, sources), diagnostics)) {
                System.err.println("Cannot compile the runtime sources in " + sourceDir);
                for (Diagnostic diagnostic : diagnostics.all()) {
                    System.err.println(diagnostic.format());
                }
                status = 1;
            } else {
                deleteRecursively(target);
                Files.move(staging, target);
            }
        } finally {
            deleteRecursively(staging);
        }
        System.exit(status);
    }
}
