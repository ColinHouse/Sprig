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

    /**
     * One release of a package: a SemVer version and the Git ref that holds it.
     * A tag release may carry the full commit {@code rev} it pointed at when it
     * was published, so a moved tag is detected; a yanked release is never
     * chosen for a new dependency, while locks that pin it still resolve.
     */
    public record Release(String version, String refKind, String ref, String rev, boolean yanked, String reason) {
        public Release(String version, String refKind, String ref) {
            this(version, refKind, ref, null, false, null);
        }

        /** The commit this release is pinned to: the written rev, or the ref itself when it is one. */
        public String pinnedRev() {
            return refKind.equals("rev") ? ref : rev;
        }
    }

    /** The {@code registry.toml} of an index: its name, whether it enforces the default-registry rules, and a forwarding address. */
    public record Index(String name, String description, boolean strict, String movedTo, String movedToSubdir) {
    }

    /** One package of an index. The last release listed is the newest. */
    public static final class Entry {
        public final String registry;
        public final String name;
        public final String description;
        public final String git;
        public final String subdir;
        /** SPDX license identifier, or null when the index does not record one. */
        public final String license;
        /** GitHub handles allowed to change the entry without maintainer approval. */
        public final List<String> owners;
        public final List<Release> releases;

        public Entry(String registry, String name, String description, String git, String subdir, List<Release> releases) {
            this(registry, name, description, git, subdir, null, List.of(), releases);
        }

        public Entry(String registry, String name, String description, String git, String subdir, String license,
                List<String> owners, List<Release> releases) {
            this.registry = registry;
            this.name = name;
            this.description = description;
            this.git = git;
            this.subdir = subdir;
            this.license = license;
            this.owners = List.copyOf(owners);
            this.releases = List.copyOf(releases);
        }

        /** The newest release by SemVer order that is not yanked, or null. */
        public Release latest() {
            Release best = null;
            for (Release release : releases) {
                if (release.yanked()) continue;
                if (best == null || compareVersions(release.version(), best.version()) > 0) best = release;
            }
            return best;
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
            map.put("license", license);
            map.put("owners", owners);
            List<Map<String, Object>> list = new ArrayList<>();
            for (Release release : releases) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("version", release.version());
                item.put(release.refKind(), release.ref());
                if (release.rev() != null && !release.refKind().equals("rev")) item.put("rev", release.rev());
                item.put("yanked", release.yanked());
                if (release.reason() != null) item.put("reason", release.reason());
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

    /**
     * An index that moved says so in its registry.toml ({@code moved_to = URL}); the
     * old directory stays read-only and SDKs follow the address once.
     */
    public static Path follow(Source source, Path root, Path projectRoot, boolean offline) throws DepError {
        Index index = index(source, root);
        if (index.movedTo() == null) return root;
        GitCache.rejectCredentials(index.movedTo());
        Source target = new Source(source.name(), null, index.movedTo(), DEFAULT_BRANCH, index.movedToSubdir());
        Path moved = locate(target, projectRoot, offline);
        if (index(target, moved).movedTo() != null) {
            throw new DepError(Codes.DEP_REGISTRY, "Registry '" + source.name() + "' moved to " + GitCache.redact(index.movedTo())
                    + ", which has moved again; an index is followed once", null);
        }
        return moved;
    }

    /** The registry.toml of an index, with defaults when the file is absent. */
    public static Index index(Source source, Path root) throws DepError {
        Path file = root.resolve(INDEX_FILE);
        if (!Files.isRegularFile(file)) return new Index(source.name(), "", false, null, null);
        try {
            Toml toml = Toml.parse(Files.readAllLines(file, StandardCharsets.UTF_8));
            Map<String, String> table = toml.table("registry");
            String strict = table.getOrDefault("strict", "false");
            if (!strict.equals("true") && !strict.equals("false")) {
                throw new DepError(Codes.DEP_REGISTRY, "registry.toml strict must be \"true\" or \"false\"", null);
            }
            String movedTo = table.get("moved_to");
            if (movedTo != null && movedTo.isBlank()) movedTo = null;
            String movedSubdir = table.get("moved_to_subdir");
            return new Index(table.getOrDefault("name", source.name()), table.getOrDefault("description", ""),
                    strict.equals("true"), movedTo, movedSubdir == null ? null : Project.normalizeSubdir(movedSubdir));
        } catch (IOException e) {
            throw new DepError(Codes.DEP_REGISTRY, "Cannot read " + file + ": " + e.getMessage(), null);
        } catch (Toml.TomlException e) {
            throw new DepError(Codes.DEP_REGISTRY, "registry.toml line " + e.line + ": " + e.getMessage(), null);
        }
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
        boolean strict = index(source, root).strict();
        List<Entry> entries = new ArrayList<>();
        try (Stream<Path> files = Files.list(packages)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".toml")).sorted().toList()) {
                Entry entry = parse(source, file);
                if (strict) requireStrict(entry);
                entries.add(entry);
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
            String license = pkg.get("license");
            if (license != null && license.isBlank()) {
                throw new DepError(Codes.DEP_REGISTRY, "Registry entry " + name + " has a blank license", null);
            }
            List<String> owners = pkg.containsKey("owners") ? toml.array("package", "owners") : List.of();
            for (String owner : owners) {
                if (!owner.matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?")) {
                    throw new DepError(Codes.DEP_REGISTRY, "Registry entry " + name + " owner '" + owner
                            + "' is not a GitHub handle", null);
                }
            }
            List<Release> releases = new ArrayList<>();
            List<Map<String, String>> items = toml.entries("release");
            for (Map<String, String> item : items) {
                String version = item.get("version");
                if (version == null || !isSemVer(version)) {
                    throw new DepError(Codes.DEP_REGISTRY, "Registry entry " + name
                            + " has a [[release]] whose version is not SemVer (MAJOR.MINOR.PATCH with an optional -pre and +build): "
                            + version, null);
                }
                String tag = item.get("tag");
                String branch = item.get("branch");
                String rev = item.get("rev");
                if (rev != null && !rev.matches("[0-9a-fA-F]{40}")) {
                    throw new DepError(Codes.DEP_REGISTRY, "Registry entry " + name + " release " + version
                            + " has a rev that is not a full 40-character commit SHA", null);
                }
                if (rev != null) rev = rev.toLowerCase(Locale.ROOT);
                String kind;
                String ref;
                if (tag != null && branch == null) {
                    kind = "tag";
                    ref = tag;
                } else if (branch != null && tag == null && rev == null) {
                    kind = "branch";
                    ref = branch;
                } else if (tag == null && branch == null && rev != null) {
                    kind = "rev";
                    ref = rev;
                } else {
                    throw new DepError(Codes.DEP_REGISTRY, "Registry entry " + name + " release " + version
                            + " needs a tag (with an optional rev), a branch, or a rev", null);
                }
                String yanked = item.getOrDefault("yanked", "false");
                if (!yanked.equals("true") && !yanked.equals("false")) {
                    throw new DepError(Codes.DEP_REGISTRY, "Registry entry " + name + " release " + version
                            + " has yanked = \"" + yanked + "\"; write \"true\" or \"false\"", null);
                }
                String reason = item.get("reason");
                if (yanked.equals("true") && (reason == null || reason.isBlank())) {
                    throw new DepError(Codes.DEP_REGISTRY, "Registry entry " + name + " release " + version
                            + " is yanked without a reason", null);
                }
                if (releases.stream().anyMatch(r -> r.version().equals(version))) {
                    throw new DepError(Codes.DEP_REGISTRY, "Registry entry " + name + " lists version " + version
                            + " twice", null);
                }
                releases.add(new Release(version, kind, ref, kind.equals("rev") ? null : rev, yanked.equals("true"), reason));
            }
            return new Entry(source.name(), name, pkg.getOrDefault("description", ""), git, subdir, license, owners, releases);
        } catch (IOException e) {
            throw new DepError(Codes.DEP_REGISTRY, "Cannot read registry entry " + file + ": " + e.getMessage(), null);
        } catch (Toml.TomlException e) {
            throw new DepError(Codes.DEP_REGISTRY, "Registry entry " + file.getFileName() + " line " + e.line
                    + ": " + e.getMessage(), null);
        }
    }

    /** Names reserved in every index: the bundled std and the Sprig repository's own names. */
    public static final List<String> RESERVED_NAMES = List.of("std", "sprig", "sprig-compiler", "sprig-runtime");

    /** A package name is lowercase letters, digits and hyphens, starting with a letter; std and sprig are reserved. */
    public static void validateName(String name, Path file) throws DepError {
        if (!name.matches("[a-z][a-z0-9-]*") || RESERVED_NAMES.contains(name)) {
            throw new DepError(Codes.DEP_REGISTRY, "Registry package name '" + name + "' is not a valid package name"
                    + (file == null ? "" : " (" + file.getFileName() + ")"),
                    "Use lowercase letters, digits and '-', starting with a letter; std and sprig are reserved.");
        }
    }

    private static final java.util.regex.Pattern SEMVER = java.util.regex.Pattern.compile(
            "(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?");

    public static boolean isSemVer(String version) {
        return version != null && SEMVER.matcher(version).matches();
    }

    /** SemVer precedence: numeric parts, then a pre-release sorts before the release and compares per identifier. */
    public static int compareVersions(String left, String right) {
        java.util.regex.Matcher a = SEMVER.matcher(left);
        java.util.regex.Matcher b = SEMVER.matcher(right);
        if (!a.matches() || !b.matches()) return left.compareTo(right);
        for (int group = 1; group <= 3; group++) {
            int cmp = Long.compare(Long.parseLong(a.group(group)), Long.parseLong(b.group(group)));
            if (cmp != 0) return cmp;
        }
        String preA = a.group(4);
        String preB = b.group(4);
        if (preA == null && preB == null) return 0;
        if (preA == null) return 1;
        if (preB == null) return -1;
        String[] partsA = preA.split("\\.");
        String[] partsB = preB.split("\\.");
        for (int i = 0; i < Math.max(partsA.length, partsB.length); i++) {
            if (i >= partsA.length) return -1;
            if (i >= partsB.length) return 1;
            boolean numA = partsA[i].matches("[0-9]+");
            boolean numB = partsB[i].matches("[0-9]+");
            int cmp;
            if (numA && numB) cmp = Long.compare(Long.parseLong(partsA[i]), Long.parseLong(partsB[i]));
            else if (numA) cmp = -1;
            else if (numB) cmp = 1;
            else cmp = partsA[i].compareTo(partsB[i]);
            if (cmp != 0) return cmp;
        }
        return 0;
    }

    /**
     * The default registry's rules: every release is a tag pinned to its commit
     * (a branch is mutable, so it is rejected), and the entry names its license
     * and at least one owner. Path and private registries may relax them.
     */
    public static void requireStrict(Entry entry) throws DepError {
        if (entry.license == null) {
            throw new DepError(Codes.DEP_REGISTRY, "Registry entry " + entry.name + " needs license = \"SPDX-ID\" in this registry",
                    "Publish with --license, or set [project] license in the package's sprig.toml.");
        }
        if (entry.owners.isEmpty()) {
            throw new DepError(Codes.DEP_REGISTRY, "Registry entry " + entry.name + " needs owners = [\"github-handle\"] in this registry",
                    "Publish with --owner HANDLE the first time; later changes come from an owner or get maintainer approval.");
        }
        for (Release release : entry.releases) {
            if (!release.refKind().equals("tag") || release.rev() == null) {
                throw new DepError(Codes.DEP_REGISTRY, "Registry entry " + entry.name + " release " + release.version()
                        + " must name a tag and the commit rev it points at in this registry; a branch is mutable",
                        "Publish the release with --tag TAG; sprig publish records the commit.");
            }
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
        if (entry.license != null) out.append("license = ").append(toml(entry.license)).append('\n');
        if (!entry.owners.isEmpty()) {
            out.append("owners = [");
            for (int i = 0; i < entry.owners.size(); i++) {
                if (i > 0) out.append(", ");
                out.append(toml(entry.owners.get(i)));
            }
            out.append("]\n");
        }
        for (Release release : entry.releases) {
            out.append("\n[[release]]\nversion = ").append(toml(release.version())).append('\n')
                    .append(release.refKind()).append(" = ").append(toml(release.ref())).append('\n');
            if (release.rev() != null && !release.refKind().equals("rev")) out.append("rev = ").append(toml(release.rev())).append('\n');
            if (release.yanked()) {
                out.append("yanked = \"true\"\n");
                out.append("reason = ").append(toml(release.reason())).append('\n');
            }
        }
        return out.toString();
    }

    private static String toml(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
