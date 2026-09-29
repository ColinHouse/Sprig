package sprig.compiler.project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import sprig.compiler.diag.Codes;

/**
 * {@code sprig.lock}: generated-only, deterministic machine state. Only
 * {@code sprig resolve} writes it. Unknown schema versions are rejected.
 */
public final class Lockfile {
    public static final int VERSION = 4;

    public static String edgeId(String owner, String alias) {
        return owner + "/@" + java.net.URLEncoder.encode(alias, StandardCharsets.UTF_8);
    }

    public static final class SprigEntry {
        public String id;
        public String owner;
        public String name;
        public String kind;          // "local" | "git"
        public String path;          // local only, canonical
        public String url;           // git only, credentials stripped
        public String requested;     // git only, e.g. "branch:main"
        public String revision;      // git only, exact SHA
        public String projectName;
        public String manifestSha;
        public String source;
        public boolean portable;
    }

    public static final class JvmEntry {
        public String group;
        public String artifact;
        public String version;
        public String repository;
        public String sha256;
        public boolean direct;
        public String extension = "jar";
        public String classifier = "";
        public int classpathOrder = -1;
        public String coordinate() { return group + ":" + artifact + ":" + extension + ":" + classifier + ":" + version; }
    }
    public record JvmEdge(String parent, String child) {}

    public int lockVersion;
    public String language;
    public String compiler;
    public String manifestSha;
    public String stdlibVersion;
    public String stdlibSha;
    public final List<SprigEntry> sprig = new ArrayList<>();
    public final List<JvmEntry> jvm = new ArrayList<>();
    public final List<JvmEdge> jvmEdges = new ArrayList<>();

    public static String digest(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : out) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String digest(Path file) throws IOException {
        return digest(Files.readAllBytes(file));
    }

    /** Parses a lockfile; schema problems raise structured errors. */
    public static Lockfile parse(String content) throws DepError {
        Toml toml;
        try {
            toml = Toml.parse(List.of(content.split("\n", -1)), true);
        } catch (Toml.TomlException e) {
            throw new DepError(Codes.PROJECT_LOCK_SCHEMA,
                    "sprig.lock is not valid (line " + e.line + "): " + e.getMessage(), null);
        }
        Lockfile lock = new Lockfile();
        String version = toml.scalar("", "lock-version");
        if (version == null) {
            throw new DepError(Codes.PROJECT_LOCK_SCHEMA,
                    "sprig.lock has no lock-version", null);
        }
        try {
            lock.lockVersion = Integer.parseInt(version);
        } catch (NumberFormatException e) {
            throw new DepError(Codes.PROJECT_LOCK_SCHEMA,
                    "Unsupported lock-version: " + version, null);
        }
        if (lock.lockVersion != VERSION) {
            throw new DepError(Codes.PROJECT_LOCK_SCHEMA,
                    "Unsupported lockfile schema " + lock.lockVersion + "; expected " + VERSION + "; run `sprig resolve`", null);
        }
        lock.language = toml.scalar("", "language");
        lock.compiler = toml.scalar("", "compiler");
        lock.manifestSha = toml.scalar("", "manifest-sha256");
        lock.stdlibVersion = toml.scalar("", "stdlib-version");
        lock.stdlibSha = toml.scalar("", "stdlib-sha256");
        if ((lock.stdlibVersion == null) != (lock.stdlibSha == null)
                || (lock.stdlibSha != null && !lock.stdlibSha.matches("[0-9a-f]{64}")))
            throw new DepError(Codes.PROJECT_LOCK_SCHEMA, "Invalid bundled std identity", null);
        if (lock.manifestSha == null || !lock.manifestSha.matches("[0-9a-f]{64}"))
            throw new DepError(Codes.PROJECT_LOCK_SCHEMA, "Invalid root manifest digest", null);
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (var entry : toml.entries("sprig")) {
            SprigEntry sprig = new SprigEntry();
            sprig.id = entry.get("id");
            sprig.owner = entry.get("owner");
            sprig.name = entry.get("name");
            sprig.kind = entry.get("kind");
            sprig.path = entry.get("path");
            sprig.url = entry.get("url");
            sprig.requested = entry.get("requested");
            sprig.revision = entry.get("revision");
            sprig.projectName = entry.get("project-name");
            sprig.manifestSha = entry.get("manifest-sha256");
            sprig.source = entry.get("source");
            String portableRaw = entry.get("portable");
            if (portableRaw == null || !(portableRaw.equals("true") || portableRaw.equals("false")))
                throw new DepError(Codes.PROJECT_LOCK_SCHEMA,
                        "sprig.lock entry '" + sprig.name + "' is missing a boolean portable field", null);
            sprig.portable = portableRaw.equals("true");
            if (sprig.id == null || sprig.owner == null || sprig.name == null
                    || sprig.name.isBlank()
                    || !(sprig.owner.equals("root") || sprig.owner.startsWith("root/@"))
                    || !sprig.id.equals(edgeId(sprig.owner, sprig.name)) || !ids.add(sprig.id))
                throw new DepError(Codes.PROJECT_LOCK_SCHEMA, "Missing, duplicate or inconsistent dependency edge identity", null);
            if (sprig.kind == null || !java.util.List.of("local", "git").contains(sprig.kind)
                    || sprig.manifestSha == null || !sprig.manifestSha.matches("[0-9a-f]{64}"))
                throw new DepError(Codes.PROJECT_LOCK_SCHEMA, "Invalid dependency kind or manifest digest", null);
            if (sprig.name == null || sprig.kind == null || sprig.projectName == null
                    || sprig.manifestSha == null || sprig.source == null) {
                throw new DepError(Codes.PROJECT_LOCK_SCHEMA,
                        "sprig.lock [[sprig]] entry is missing required fields", null);
            }
            if (sprig.kind.equals("git")
                    && (sprig.url == null || sprig.requested == null || sprig.revision == null)) {
                throw new DepError(Codes.PROJECT_LOCK_SCHEMA,
                        "sprig.lock git entry '" + sprig.name + "' is missing url/requested/revision", null);
            }
            if (sprig.kind.equals("local")) {
                if (sprig.path == null || sprig.path.isBlank()) {
                    throw new DepError(Codes.PROJECT_LOCK_SCHEMA,
                            "sprig.lock local entry '" + sprig.name + "' is missing path", null);
                }
                if (sprig.portable) {
                    if (Path.of(sprig.path).isAbsolute() || !normalizedLocator(sprig.path).equals(sprig.path)) {
                        throw new DepError(Codes.PROJECT_LOCK_SCHEMA,
                                "sprig.lock local entry '" + sprig.name
                                        + "' must store a normalized owner-relative locator", null);
                    }
                } else if (!Path.of(sprig.path).isAbsolute()) {
                    throw new DepError(Codes.PROJECT_LOCK_SCHEMA,
                            "sprig.lock local entry '" + sprig.name
                                    + "' is not portable and must store a canonical absolute path", null);
                }
            }
            if (sprig.kind.equals("git")) {
                if (sprig.portable) {
                    throw new DepError(Codes.PROJECT_LOCK_SCHEMA,
                            "sprig.lock git entry '" + sprig.name + "' cannot be portable", null);
                }
                if (sprig.path != null) {
                    throw new DepError(Codes.PROJECT_LOCK_SCHEMA,
                            "sprig.lock git entry '" + sprig.name + "' must not store a path", null);
                }
            }
            if (sprig.kind.equals("git") && !sprig.revision.matches("[0-9a-f]{40}"))
                throw new DepError(Codes.PROJECT_LOCK_SCHEMA, "Git revision must be an exact commit SHA", null);
            lock.sprig.add(sprig);
        }
        for (SprigEntry entry : lock.sprig)
            if (!entry.owner.equals("root") && !ids.contains(entry.owner))
                throw new DepError(Codes.PROJECT_LOCK_SCHEMA, "Unknown dependency edge owner", null);
        java.util.Set<String> coordinates = new java.util.HashSet<>();
        java.util.Set<Integer> orders = new java.util.HashSet<>();
        for (var entry : toml.entries("jvm")) {
            JvmEntry jvm = new JvmEntry();
            jvm.group = entry.get("group");
            jvm.artifact = entry.get("artifact");
            jvm.version = entry.get("version");
            jvm.repository = entry.get("repository");
            jvm.sha256 = entry.get("sha256");
            jvm.direct = "true".equals(entry.get("direct"));
            jvm.extension = entry.get("extension");
            jvm.classifier = entry.get("classifier");
            try { jvm.classpathOrder = Integer.parseInt(entry.get("classpath-order")); }
            catch (RuntimeException e) { throw new DepError(Codes.PROJECT_LOCK_SCHEMA, "Invalid Maven classpath order", null); }
            if (jvm.group == null || jvm.artifact == null || jvm.version == null) {
                throw new DepError(Codes.PROJECT_LOCK_SCHEMA,
                        "sprig.lock [[jvm]] entry is missing group/artifact/version", null);
            }
            try { MavenResolver.validate(jvm.group, jvm.artifact, jvm.version); }
            catch (DepError e) { throw new DepError(Codes.PROJECT_LOCK_SCHEMA, e.getMessage(), null); }
            if (jvm.extension == null || !List.of("jar", "pom").contains(jvm.extension)
                    || jvm.classifier == null || !jvm.classifier.matches("[A-Za-z0-9_.-]*")
                    || jvm.classifier.contains("..") || jvm.repository == null
                    || jvm.sha256 == null || !jvm.sha256.matches("[0-9a-f]{64}")
                    || !coordinates.add(jvm.coordinate()) || jvm.classpathOrder < -1
                    || (jvm.extension.equals("pom") != (jvm.classpathOrder == -1))
                    || (jvm.classpathOrder >= 0 && !orders.add(jvm.classpathOrder)))
                throw new DepError(Codes.PROJECT_LOCK_SCHEMA, "Invalid or duplicate Maven artifact identity/checksum/order", null);
            lock.jvm.add(jvm);
        }
        for (int i = 0; i < orders.size(); i++) if (!orders.contains(i))
            throw new DepError(Codes.PROJECT_LOCK_SCHEMA, "Maven classpath order must be contiguous", null);
        java.util.Set<JvmEdge> edges = new java.util.HashSet<>();
        for (var e : toml.entries("jvm-edge")) {
            JvmEdge edge = new JvmEdge(e.get("parent"), e.get("child"));
            if (edge.parent() == null || edge.child() == null
                    || !(edge.parent().equals("root") || coordinates.contains(edge.parent()))
                    || !coordinates.contains(edge.child()) || !edges.add(edge))
                throw new DepError(Codes.PROJECT_LOCK_SCHEMA, "Invalid Maven graph edge", null);
            lock.jvmEdges.add(edge);
        }
        return lock;
    }

    /** Renders the canonical, deterministic lockfile text. */
    public String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("lock-version = ").append(VERSION).append('\n');
        sb.append("language = ").append(quote(language == null ? "0.8" : language)).append('\n');
        sb.append("compiler = ").append(quote(compiler == null ? "" : compiler)).append('\n');
        sb.append("manifest-sha256 = ").append(quote(manifestSha == null ? "" : manifestSha)).append('\n');
        if (stdlibVersion != null) {
            sb.append("stdlib-version = ").append(quote(stdlibVersion)).append('\n');
            sb.append("stdlib-sha256 = ").append(quote(stdlibSha)).append('\n');
        }
        List<SprigEntry> sorted = new ArrayList<>(sprig);
        sorted.sort(Comparator.comparing(e -> e.id));
        for (SprigEntry entry : sorted) {
            sb.append('\n').append("[[sprig]]\n");
            sb.append("id = ").append(quote(entry.id)).append('\n');
            sb.append("owner = ").append(quote(entry.owner)).append('\n');
            sb.append("name = ").append(quote(entry.name)).append('\n');
            sb.append("kind = ").append(quote(entry.kind)).append('\n');
            if (entry.kind.equals("local")) {
                sb.append("path = ").append(quote(entry.path)).append('\n');
                sb.append("portable = ").append(entry.portable).append('\n');
            } else {
                sb.append("url = ").append(quote(entry.url)).append('\n');
                sb.append("requested = ").append(quote(entry.requested)).append('\n');
                sb.append("revision = ").append(quote(entry.revision)).append('\n');
                sb.append("portable = ").append(entry.portable).append('\n');
            }
            sb.append("project-name = ").append(quote(entry.projectName)).append('\n');
            sb.append("source = ").append(quote(entry.source)).append('\n');
            sb.append("manifest-sha256 = ").append(quote(entry.manifestSha)).append('\n');
        }
        List<JvmEntry> jvmSorted = new ArrayList<>(jvm);
        jvmSorted.sort(Comparator.comparing((JvmEntry e) -> e.group)
                .thenComparing(e -> e.artifact).thenComparing(e -> e.version)
                .thenComparing(e -> e.extension).thenComparing(e -> e.classifier));
        for (JvmEntry entry : jvmSorted) {
            sb.append('\n').append("[[jvm]]\n");
            sb.append("group = ").append(quote(entry.group)).append('\n');
            sb.append("artifact = ").append(quote(entry.artifact)).append('\n');
            sb.append("version = ").append(quote(entry.version)).append('\n');
            if (entry.repository != null) {
                sb.append("repository = ").append(quote(entry.repository)).append('\n');
            }
            if (entry.sha256 != null) {
                sb.append("sha256 = ").append(quote(entry.sha256)).append('\n');
            }
            sb.append("extension = ").append(quote(entry.extension)).append('\n');
            sb.append("classifier = ").append(quote(entry.classifier)).append('\n');
            sb.append("classpath-order = ").append(entry.classpathOrder).append('\n');
            sb.append("direct = ").append(entry.direct).append('\n');
        }
        jvmEdges.stream().distinct().sorted(Comparator.comparing(JvmEdge::parent).thenComparing(JvmEdge::child))
                .forEach(e -> sb.append("\n[[jvm-edge]]\nparent = ").append(quote(e.parent()))
                        .append("\nchild = ").append(quote(e.child())).append('\n'));
        return sb.toString();
    }

    /** Normalized forward-slash locator spelling; {@code .} stays {@code .}. */
    public static String normalizedLocator(String raw) {
        String normalized = Path.of(raw).normalize().toString().replace('\\', '/');
        return normalized.isEmpty() ? "." : normalized;
    }

    private static String quote(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
