package sprig.compiler.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v0.8 project model: {@code sprig.toml} discovery, the default layout
 * ({@code src/main.spr}) and manifest metadata.
 *
 * <p>Dependency resolution is intentionally absent: the model records what the
 * manifest declares, marks it unresolved, and the CLI reports that clearly
 * instead of pretending to resolve it.
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
    public final List<Bin> bins;
    public final List<String> exports;
    public final List<Dependency> dependencies;
    public final List<JvmDependency> jvmDependencies;

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

        public Dependency(String name, String path, String git, String branch) {
            this.name = name;
            this.path = path;
            this.git = git;
            this.branch = branch;
        }

        public boolean isLocal() {
            return path != null;
        }

        public boolean isGit() {
            return git != null;
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
            throw new Toml.TomlException("sprig.toml is missing [project] name", 1);
        }
        this.name = declaredName;
        this.version = project.getOrDefault("version", "0.1.0");
        this.language = project.getOrDefault("language", "0.8");
        this.source = project.getOrDefault("source", "src");
        this.defaultEntry = project.getOrDefault("entry", source + "/main.spr");
        List<String> exported = toml.array("project", "exports");
        if (exported.isEmpty()) {
            exported = toml.array("exports");
        }
        this.exports = List.copyOf(exported);

        List<Bin> parsedBins = new ArrayList<>();
        for (Map<String, String> bin : toml.entries("bin")) {
            String binName = bin.get("name");
            String entry = bin.get("entry");
            if (binName == null || binName.isBlank() || entry == null || entry.isBlank()) {
                throw new Toml.TomlException("[[bin]] requires name and entry", 1);
            }
            parsedBins.add(new Bin(binName, entry));
        }
        this.bins = List.copyOf(parsedBins);

        List<Dependency> deps = new ArrayList<>();
        java.util.Set<String> depNames = new java.util.LinkedHashSet<>();
        for (Map<String, String> dep : toml.entries("dependency")) {
            String depName = dep.get("name");
            if (depName == null || depName.isBlank()) {
                throw new Toml.TomlException("[[dependency]] requires name", 1);
            }
            if (!depNames.add(depName)) {
                throw new Toml.TomlException("Duplicate dependency name '" + depName + "'", 1);
            }
            if (dep.get("path") != null && dep.get("git") != null) {
                throw new Toml.TomlException(
                        "dependency '" + depName + "' cannot be both path and git", 1);
            }
            deps.add(new Dependency(depName, dep.get("path"), dep.get("git"), dep.get("branch")));
        }
        this.dependencies = List.copyOf(deps);

        List<JvmDependency> jvm = new ArrayList<>();
        for (Map<String, String> dep : toml.entries("jvm")) {
            String group = dep.get("group");
            String artifact = dep.get("artifact");
            String depVersion = dep.get("version");
            if (group == null || artifact == null || depVersion == null) {
                throw new Toml.TomlException("[[jvm]] requires group, artifact and version", 1);
            }
            if (depVersion.contains("^") || depVersion.contains(">") || depVersion.contains("<")
                    || depVersion.equals("latest") || depVersion.equals("+")) {
                throw new Toml.TomlException(
                        "[[jvm]] accepts exact versions only: " + depVersion, 1);
            }
            jvm.add(new JvmDependency(group, artifact, depVersion));
        }
        this.jvmDependencies = List.copyOf(jvm);
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
            item.put("git", dep.git);
            item.put("branch", dep.branch);
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
                        ? "not-needed" : "not-implemented");
        return map;
    }
}
