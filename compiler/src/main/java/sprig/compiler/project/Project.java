package sprig.compiler.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/**
 * v0.8 project model: {@code sprig.toml} discovery, the default layout
 * ({@code src/main.spr}) and manifest metadata.
 *
 * <p>Dependency graph and artifact resolution live in DependencyResolver and
 * MavenResolver; this model only validates manifest declarations.
 */
public final class Project {
    public static final String MANIFEST = "sprig.toml";
    public static final String LOCKFILE = "sprig.lock";

    public final Path root;
    public final Path manifest;
    public final String name;
    public final String version;
    public final String language;
    public final String source;
    public final String defaultEntry;
    public final boolean hasExplicitEntry;
    public final List<Bin> bins;
    public final List<String> exports;
    public final List<Dependency> dependencies;
    public final List<JvmDependency> jvmDependencies;
    /** Declared package registries, in order; empty means the default registry. */
    public final List<Registry.Source> registries;

    public static final class Bin {
        public final String name;
        public final String entry;

        public Bin(String name, String entry) {
            this.name = name;
            this.entry = entry;
        }
    }

    public static final class Dependency {
        public final String name;
        public final String path;
        public final String git;
        public final String branch;
        public final String tag;
        public final String rev;
        public final String subdir;

        public Dependency(String name, String path, String git, String branch,
                          String tag, String rev, String subdir) {
            this.name = name;
            this.path = path;
            this.git = git;
            this.branch = branch;
            this.tag = tag;
            this.rev = rev;
            this.subdir = subdir;
        }

        public boolean isLocal() {
            return path != null;
        }

        public boolean isGit() {
            return git != null;
        }

        /** Relative declarations are owner-relative and survive relocation. */
        public boolean isPortable() {
            return isLocal() && !Path.of(path).isAbsolute();
        }

        /** Normalized forward-slash locator used as the portable lock path. */
        public String locator() {
            return Lockfile.normalizedLocator(path);
        }
    }

    public static final class JvmDependency {
        public final String group;
        public final String artifact;
        public final String version;

        public JvmDependency(String group, String artifact, String version) {
            this.group = group;
            this.artifact = artifact;
            this.version = version;
        }
    }

    private Project(Path root, Path manifest, Toml toml) {
        this.root = root;
        this.manifest = manifest;
        Map<String, String> project = toml.table("project");
        String declaredName = project.get("name");
        if (declaredName == null || declaredName.isBlank()) {
            throw new Toml.TomlException("sprig.toml is missing [project] name",
                    toml.tableLine("project", "name"));
        }
        this.name = declaredName;
        this.version = project.getOrDefault("version", "0.1.0");
        this.language = project.getOrDefault("language", "0.8");
        this.source = project.getOrDefault("source", "src");
        this.hasExplicitEntry = project.containsKey("entry");
        this.defaultEntry = project.getOrDefault("entry", source + "/main.spr");
        validatePath(source, "source", toml.tableLine("project", "source"));
        validatePath(defaultEntry, "entry", toml.tableLine("project", "entry"));
        List<String> exported = toml.array("project", "exports");
        if (exported.isEmpty()) {
            exported = toml.array("exports");
        }
        this.exports = List.copyOf(exported);

        Set<String> binNames = new HashSet<>();
        List<Bin> parsedBins = new ArrayList<>();
        List<Map<String, String>> binEntries = toml.entries("bin");
        for (int i = 0; i < binEntries.size(); i++) {
            Map<String, String> bin = binEntries.get(i);
            String binName = bin.get("name");
            String entry = bin.get("entry");
            if (binName == null || binName.isBlank() || entry == null || entry.isBlank()) {
                String missing = binName == null || binName.isBlank() ? "name" : "entry";
                throw new Toml.TomlException("[[bin]] requires name and entry",
                        toml.entryLine("bin", i, missing));
            }
            if (!binNames.add(binName)) throw new Toml.TomlException("Duplicate bin name '" + binName + "'",
                    toml.entryLine("bin", i, "name"));
            validatePath(entry, "bin entry", toml.entryLine("bin", i, "entry"));
            parsedBins.add(new Bin(binName, entry));
        }
        this.bins = List.copyOf(parsedBins);

        Set<String> depNames = new HashSet<>();
        List<Dependency> deps = new ArrayList<>();
        List<Map<String, String>> dependencyEntries = toml.entries("dependency");
        for (int i = 0; i < dependencyEntries.size(); i++) {
            Map<String, String> dep = dependencyEntries.get(i);
            String depName = dep.get("name");
            if (depName == null || depName.isBlank()) {
                throw new Toml.TomlException("[[dependency]] requires name",
                        toml.entryLine("dependency", i, "name"));
            }
            if ("std".equals(depName)) throw new Toml.TomlException("Dependency alias std is reserved for the bundled standard library",
                    toml.entryLine("dependency", i, "name"));
            if (!depNames.add(depName)) throw new Toml.TomlException("Duplicate dependency name '" + depName + "'",
                    toml.entryLine("dependency", i, "name"));
            if ((dep.get("path") != null) == (dep.get("git") != null)) {
                throw new Toml.TomlException(
                        "dependency '" + depName + "' requires exactly one of path or git",
                        toml.lastEntryLine("dependency", i, Set.of("path", "git")));
            }
            for (String key : List.of("branch", "tag", "rev", "subdir"))
                if (dep.get(key) != null && dep.get("git") == null)
                    throw new Toml.TomlException(key + " is only valid for a git dependency",
                            toml.entryLine("dependency", i, key));
            int refs = (dep.get("branch") == null ? 0 : 1)
                    + (dep.get("tag") == null ? 0 : 1)
                    + (dep.get("rev") == null ? 0 : 1);
            if (refs > 1)
                throw new Toml.TomlException("Git branch, tag and rev ref intents are mutually exclusive",
                        toml.lastEntryLine("dependency", i, Set.of("branch", "tag", "rev")));
            for (String key : List.of("path", "git", "branch", "tag", "rev", "subdir"))
                if (dep.containsKey(key) && dep.get(key).isBlank())
                    throw new Toml.TomlException("Dependency " + key + " cannot be blank",
                            toml.entryLine("dependency", i, key));
            String rev = dep.get("rev");
            if (rev != null) {
                if (!rev.matches("[0-9a-fA-F]{40}"))
                    throw new Toml.TomlException("Git rev must be a full 40-character commit SHA",
                            toml.entryLine("dependency", i, "rev"));
                rev = rev.toLowerCase(java.util.Locale.ROOT);
            }
            String subdir = dep.containsKey("subdir") ? normalizeSubdir(dep.get("subdir"),
                    toml.entryLine("dependency", i, "subdir")) : null;
            deps.add(new Dependency(depName, dep.get("path"), dep.get("git"),
                    dep.get("branch"), dep.get("tag"), rev, subdir));
        }
        this.dependencies = List.copyOf(deps);

        List<JvmDependency> jvm = new ArrayList<>();
        List<Map<String, String>> jvmEntries = toml.entries("jvm");
        for (int i = 0; i < jvmEntries.size(); i++) {
            Map<String, String> dep = jvmEntries.get(i);
            String group = dep.get("group");
            String artifact = dep.get("artifact");
            String depVersion = dep.get("version");
            if (group == null || group.isBlank() || artifact == null || artifact.isBlank() || depVersion == null || depVersion.isBlank()) {
                String missing = group == null || group.isBlank() ? "group"
                        : artifact == null || artifact.isBlank() ? "artifact" : "version";
                throw new Toml.TomlException("[[jvm]] requires group, artifact and version",
                        toml.entryLine("jvm", i, missing));
            }
            if (!depVersion.matches("[A-Za-z0-9][A-Za-z0-9_.-]*")
                    || depVersion.equalsIgnoreCase("LATEST") || depVersion.equalsIgnoreCase("RELEASE")) {
                throw new Toml.TomlException(
                        "[[jvm]] accepts exact versions only: " + depVersion,
                        toml.entryLine("jvm", i, "version"));
            }
            try { MavenResolver.validate(group, artifact, depVersion); }
            catch (DepError e) { throw new Toml.TomlException(e.getMessage(), toml.entryLine("jvm", i, "version")); }
            jvm.add(new JvmDependency(group, artifact, depVersion));
        }
        this.jvmDependencies = List.copyOf(jvm);

        Set<String> registryNames = new HashSet<>();
        List<Registry.Source> sources = new ArrayList<>();
        List<Map<String, String>> registryEntries = toml.entries("registry");
        for (int i = 0; i < registryEntries.size(); i++) {
            Map<String, String> entry = registryEntries.get(i);
            String registryName = entry.get("name");
            if (registryName == null || registryName.isBlank())
                throw new Toml.TomlException("[[registry]] requires name", toml.entryLine("registry", i, "name"));
            if (!registryNames.add(registryName))
                throw new Toml.TomlException("Duplicate registry name '" + registryName + "'",
                        toml.entryLine("registry", i, "name"));
            if ((entry.get("path") != null) == (entry.get("url") != null))
                throw new Toml.TomlException("registry '" + registryName + "' requires exactly one of path or url",
                        toml.lastEntryLine("registry", i, Set.of("path", "url")));
            for (String key : List.of("branch", "subdir"))
                if (entry.get(key) != null && entry.get("url") == null)
                    throw new Toml.TomlException(key + " is only valid for a url registry",
                            toml.entryLine("registry", i, key));
            for (String key : List.of("path", "url", "branch", "subdir"))
                if (entry.containsKey(key) && entry.get(key).isBlank())
                    throw new Toml.TomlException("Registry " + key + " cannot be blank", toml.entryLine("registry", i, key));
            String subdir = entry.containsKey("subdir")
                    ? normalizeSubdir(entry.get("subdir"), toml.entryLine("registry", i, "subdir")) : null;
            sources.add(new Registry.Source(registryName, entry.get("path"), entry.get("url"),
                    entry.getOrDefault("branch", "main"), subdir));
        }
        this.registries = List.copyOf(sources);
    }

    private static void validatePath(String text, String field, int line) {
        try {
            if (text.isBlank()) throw new IllegalArgumentException("empty path");
            Path.of(text);
        } catch (IllegalArgumentException e) {
            throw new Toml.TomlException("Invalid " + field + " path: " + e.getMessage(), line);
        }
    }

    /** Canonical relative Git package directory; the repository root is ".". */
    public static String normalizeSubdir(String raw) {
        return normalizeSubdir(raw, 1);
    }

    private static String normalizeSubdir(String raw, int line) {
        if (raw == null || raw.isBlank())
            throw new Toml.TomlException("Git subdir cannot be blank", line);
        String portable = raw.replace('\\', '/');
        if (portable.startsWith("/") || portable.matches("^[A-Za-z]:.*"))
            throw new Toml.TomlException("Git subdir must be relative, not absolute", line);
        List<String> components = new ArrayList<>();
        for (String component : portable.split("/", -1)) {
            if (component.equals(".."))
                throw new Toml.TomlException("Git subdir cannot contain '..' path components", line);
            if (component.isEmpty() || component.equals(".")) continue;
            if (component.matches("^[A-Za-z]:.*"))
                throw new Toml.TomlException("Git subdir cannot contain a drive-qualified path component", line);
            try {
                if (Path.of(component).isAbsolute())
                    throw new Toml.TomlException("Git subdir must be relative, not absolute", line);
            } catch (java.nio.file.InvalidPathException e) {
                throw new Toml.TomlException("Invalid Git subdir: " + e.getMessage(), line);
            }
            components.add(component);
        }
        return components.isEmpty() ? "." : String.join("/", components);
    }

    /** Loads one manifest file (does not search upward). */
    public static Project load(Path manifest) {
        try {
            Toml toml = Toml.parse(Files.readAllLines(manifest));
            return new Project(manifest.toAbsolutePath().getParent(), manifest.toAbsolutePath(), toml);
        } catch (IOException e) {
            throw new Toml.TomlException("Cannot read sprig.toml: " + e.getMessage(), 1);
        }
    }

    /** Finds the nearest {@code sprig.toml} at or above {@code start}. */
    public static Path findManifest(Path start) {
        Path dir = start.toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve(MANIFEST);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return null;
    }

    /** Discovers a project from {@code start}, or returns null. */
    public static Project discover(Path start) {
        Path manifest = findManifest(start);
        return manifest == null ? null : load(manifest);
    }

    public Path entryPath() {
        return root.resolve(defaultEntry);
    }

    public Path lockPath() {
        return root.resolve(LOCKFILE);
    }

    /** The entry for {@code --bin name}, or null when the name is unknown. */
    public Path entryForBin(String binName) {
        for (Bin bin : bins) {
            if (bin.name.equals(binName)) {
                return root.resolve(bin.entry);
            }
        }
        return null;
    }

    public Map<String, Object> toJsonMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("schemaVersion", 1);
        map.put("root", root.toString());
        map.put("name", name);
        map.put("version", version);
        map.put("language", language);
        map.put("source", source);
        map.put("entry", defaultEntry);
        List<Object> binaryList = new ArrayList<>();
        for (Bin bin : bins) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", bin.name);
            item.put("entry", bin.entry);
            binaryList.add(item);
        }
        map.put("binaries", binaryList);
        map.put("exports", exports);
        map.put("manifest", manifest.toString());
        map.put("lockfile", lockPath().toString());
        map.put("lockStatus", Files.isRegularFile(lockPath()) ? "present" : "absent");
        List<Object> deps = new ArrayList<>();
        for (Dependency dep : dependencies) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", dep.name);
            item.put("path", dep.path);
            item.put("git", dep.git == null ? null : GitCache.redact(dep.git));
            item.put("branch", dep.branch);
            item.put("tag", dep.tag);
            item.put("rev", dep.rev);
            item.put("subdir", dep.subdir);
            item.put("kind", dep.isGit() ? "git" : dep.isLocal() ? "local" : "unknown");
            item.put("resolved", false);
            deps.add(item);
        }
        map.put("sprigDependencies", deps);
        List<Object> jvm = new ArrayList<>();
        for (JvmDependency dep : jvmDependencies) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("group", dep.group);
            item.put("artifact", dep.artifact);
            item.put("version", dep.version);
            item.put("resolved", false);
            jvm.add(item);
        }
        map.put("jvmDependencies", jvm);
        map.put("dependencyResolution",
                (dependencies.isEmpty() && jvmDependencies.isEmpty())
                        ? "not-needed" : "declared");
        return map;
    }
}
