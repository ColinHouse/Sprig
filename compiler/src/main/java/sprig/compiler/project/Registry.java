package sprig.compiler.project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import sprig.compiler.diag.Codes;

/**
 * A package registry: an index of Sprig packages that says where each one
 * lives (a Git repository, a subdirectory and the tag, branch or revision of
 * each release). It adds no new way to fetch code: {@code sprig add NAME}
 * looks the name up and writes the ordinary Git dependency the index names,
 * and the lock pins the commit as for any Git dependency.
 *
 * <p>An index is a directory with an optional {@code registry.toml} and one
 * {@code packages/NAME.toml} per package. It is reached through a local path
 * or a Git URL (a branch, read at {@code add}/{@code search} time and pinned
 * for {@code --offline}). A project declares its registries with
 * {@code [[registry]]} tables; without any, the default registry is the
 * {@code registry/} directory of the Sprig repository, which lists the
 * first-party libraries.
 */
public final class Registry {
    public static final String DEFAULT_NAME = "sprig";
    public static final String DEFAULT_URL = "https://github.com/ColinHouse/Sprig.git";
    public static final String DEFAULT_BRANCH = "main";
    public static final String DEFAULT_SUBDIR = "registry";
    public static final String INDEX_FILE = "registry.toml";
    public static final String PACKAGES_DIR = "packages";

    private Registry() {
    }

    /** Where an index lives: a local directory, or a branch of a Git repository (optionally a subdirectory). */
    public record Source(String name, String path, String url, String branch, String subdir) {
        public boolean isLocal() {
            return path != null;
        }

        public String describe() {
            return isLocal() ? path : GitCache.redact(url) + (subdir == null ? "" : "/" + subdir) + "@" + branch;
        }

        public static Source defaultSource() {
            return new Source(DEFAULT_NAME, null, DEFAULT_URL, DEFAULT_BRANCH, DEFAULT_SUBDIR);
        }
    }

    /** One release of a package: a version label and the Git ref that holds it. */
    public record Release(String version, String refKind, String ref) {
    }

    /** One package of an index. The last release listed is the newest. */
    public static final class Entry {
        public final String registry;
        public final String name;
        public final String description;
        public final String git;
        public final String subdir;
        public final List<Release> releases;

        public Entry(String registry, String name, String description, String git, String subdir, List<Release> releases) {
            this.registry = registry;
            this.name = name;
            this.description = description;
            this.git = git;
            this.subdir = subdir;
            this.releases = List.copyOf(releases);
        }

        public Release latest() {
            return releases.isEmpty() ? null : releases.get(releases.size() - 1);
        }

        public Release release(String version) {
            for (Release release : releases) {
                if (release.version().equals(version)) return release;
            }
            return null;
        }

        public Map<String, Object> asMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", name);
            map.put("registry", registry);
            map.put("description", description);
            map.put("git", GitCache.redact(git));
            map.put("subdir", subdir == null ? "." : subdir);
            List<Map<String, Object>> list = new ArrayList<>();
            for (Release release : releases) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("version", release.version());
                item.put(release.refKind(), release.ref());
                list.add(item);
            }
            map.put("releases", list);
            Release latest = latest();
            map.put("latest", latest == null ? null : latest.version());
            return map;
        }
    }

    /** The registries a command consults: the project's declared ones, or the default. */
    public static List<Source> sources(Project project) {
        if (project != null && !project.registries.isEmpty()) {
            return project.registries;
        }
        return List.of(Source.defaultSource());
    }

    /** The directory holding the index of a source; a Git source is fetched (or, offline, taken from its pin). */
    public static Path locate(Source source, Path projectRoot, boolean offline) throws DepError {
        if (source.isLocal()) {
            Path base = projectRoot == null ? Path.of("").toAbsolutePath() : projectRoot;
            Path root = base.resolve(source.path()).normalize();
            if (!Files.isDirectory(root)) {
                throw new DepError(Codes.DEP_REGISTRY, "Registry '" + source.name() + "' directory not found: " + root,
                        "A registry is a directory with packages/NAME.toml files; check the [[registry]] path.");
            }
            return root;
        }
        GitCache.rejectCredentials(source.url());
        GitCache cache = new GitCache(GitCache.defaultRoot(), offline);
        Path pin = pinPath(source);
        String revision;
        if (offline) {
            try {
                revision = Files.isRegularFile(pin) ? Files.readString(pin, StandardCharsets.UTF_8).trim() : null;
            } catch (IOException e) {
                revision = null;
            }
            if (revision == null || !revision.matches("[0-9a-f]{40}")) {
                throw new DepError(Codes.DEP_OFFLINE, "Offline mode has no cached index for registry '" + source.name()
                        + "' (" + source.describe() + ")", "Run sprig search or sprig add once with network access.");
            }
        } else {
            revision = cache.remoteRevision(source.url(), "branch", source.branch());
            try {
                Files.createDirectories(pin.getParent());
                Files.writeString(pin, revision + "\n", StandardCharsets.UTF_8);
            } catch (IOException ignored) {
                // The pin only serves --offline; the index was still read.
            }
        }
        Path checkout = cache.materialize(source.url(), revision);
        Path root = source.subdir() == null ? checkout
                : checkout.resolve(Project.normalizeSubdir(source.subdir())).normalize();
        if (!root.startsWith(checkout) || !Files.isDirectory(root)) {
            throw new DepError(Codes.DEP_REGISTRY, "Registry '" + source.name() + "' has no directory "
                    + source.subdir() + " at " + source.describe(), null);
        }
        return root;
    }

    private static Path pinPath(Source source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((source.url() + "\n" + source.branch()).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 12; i++) hex.append(String.format("%02x", hash[i]));
            return Path.of(System.getProperty("user.home"), ".sprig", "registry", hex + ".rev");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Every package of an index, by name. */
    public static List<Entry> load(Source source, Path root) throws DepError {
        Path packages = root.resolve(PACKAGES_DIR);
        if (!Files.isDirectory(packages)) {
            throw new DepError(Codes.DEP_REGISTRY, "Registry '" + source.name() + "' has no " + PACKAGES_DIR
                    + " directory at " + root, "An index holds one packages/NAME.toml per package.");
        }
        List<Entry> entries = new ArrayList<>();
        try (Stream<Path> files = Files.list(packages)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".toml")).sorted().toList()) {
                entries.add(parse(source, file));
            }
        } catch (IOException e) {
            throw new DepError(Codes.DEP_REGISTRY, "Cannot read registry '" + source.name() + "': " + e.getMessage(), null);
        }
        return entries;
    }

    public static Entry parse(Source source, Path file) throws DepError {
        String stem = file.getFileName().toString();
        stem = stem.substring(0, stem.length() - ".toml".length());
        try {
            Toml toml = Toml.parse(Files.readAllLines(file, StandardCharsets.UTF_8));
            Map<String, String> pkg = toml.table("package");
            String name = pkg.get("name");
            if (name == null || !name.equals(stem)) {
                throw new DepError(Codes.DEP_REGISTRY, "Registry entry " + file.getFileName()
                        + " must declare [package] name = \"" + stem + "\"", null);
            }
            validateName(name, file);
            String git = pkg.get("git");
            if (git == null || git.isBlank()) {
                throw new DepError(Codes.DEP_REGISTRY, "Registry entry " + name + " needs [package] git = URL", null);
            }
            GitCache.rejectCredentials(git);
            String subdir = pkg.get("subdir") == null ? null : Project.normalizeSubdir(pkg.get("subdir"));
            List<Release> releases = new ArrayList<>();
            List<Map<String, String>> items = toml.entries("release");
            for (Map<String, String> item : items) {
                String version = item.get("version");
                if (version == null || !version.matches("[A-Za-z0-9][A-Za-z0-9_.+-]*")) {
                    throw new DepError(Codes.DEP_REGISTRY, "Registry entry " + name
                            + " has a [[release]] without a valid version", null);
                }
                int refs = 0;
                String kind = null;
                for (String candidate : List.of("tag", "branch", "rev")) {
                    if (item.get(candidate) != null) {
                        refs++;
                        kind = candidate;
                    }
                }
                if (refs != 1) {
                    throw new DepError(Codes.DEP_REGISTRY, "Registry entry " + name + " release " + version
                            + " needs exactly one of tag, branch or rev", null);
                }
                String ref = item.get(kind);
                if (kind.equals("rev") && !ref.matches("[0-9a-fA-F]{40}")) {
                    throw new DepError(Codes.DEP_REGISTRY, "Registry entry " + name + " release " + version
                            + " has a rev that is not a full 40-character commit SHA", null);
                }
                if (releases.stream().anyMatch(r -> r.version().equals(version))) {
                    throw new DepError(Codes.DEP_REGISTRY, "Registry entry " + name + " lists version " + version
                            + " twice", null);
                }
                releases.add(new Release(version, kind, kind.equals("rev") ? ref.toLowerCase(Locale.ROOT) : ref));
            }
            return new Entry(source.name(), name, pkg.getOrDefault("description", ""), git, subdir, releases);
        } catch (IOException e) {
            throw new DepError(Codes.DEP_REGISTRY, "Cannot read registry entry " + file + ": " + e.getMessage(), null);
        } catch (Toml.TomlException e) {
            throw new DepError(Codes.DEP_REGISTRY, "Registry entry " + file.getFileName() + " line " + e.line
                    + ": " + e.getMessage(), null);
        }
    }

    /** A package name is a dependency alias: letters, digits, '-' and '_', never 'std'. */
    public static void validateName(String name, Path file) throws DepError {
        if (!name.matches("[A-Za-z][A-Za-z0-9_-]*") || name.equals("std")) {
            throw new DepError(Codes.DEP_REGISTRY, "Registry package name '" + name + "' is not a valid dependency name"
                    + (file == null ? "" : " (" + file.getFileName() + ")"),
                    "Use letters, digits, '-' and '_', starting with a letter; std is reserved.");
        }
    }

    /** The entries whose name or description contains the query (case-insensitive); every entry for an empty query. */
    public static List<Entry> search(List<Entry> entries, String query) {
        if (query == null || query.isBlank()) return entries;
        String needle = query.toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.name.toLowerCase(Locale.ROOT).contains(needle)
                    || entry.description.toLowerCase(Locale.ROOT).contains(needle)) {
                out.add(entry);
            }
        }
        return out;
    }

    /** The TOML text of an entry, as sprig publish writes it. */
    public static String render(Entry entry) {
        StringBuilder out = new StringBuilder("[package]\nname = ").append(toml(entry.name)).append('\n');
        if (!entry.description.isEmpty()) out.append("description = ").append(toml(entry.description)).append('\n');
        out.append("git = ").append(toml(entry.git)).append('\n');
        if (entry.subdir != null && !entry.subdir.equals(".")) out.append("subdir = ").append(toml(entry.subdir)).append('\n');
        for (Release release : entry.releases) {
            out.append("\n[[release]]\nversion = ").append(toml(release.version())).append('\n')
                    .append(release.refKind()).append(" = ").append(toml(release.ref())).append('\n');
        }
        return out.toString();
    }

    private static String toml(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
