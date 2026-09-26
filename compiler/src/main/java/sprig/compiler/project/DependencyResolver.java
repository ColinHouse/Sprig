package sprig.compiler.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import sprig.compiler.Compiler;
import sprig.compiler.diag.Codes;
import sprig.compiler.diag.Diagnostic;
import sprig.compiler.diag.Diagnostics;
import sprig.compiler.diag.Phase;
import sprig.compiler.diag.Span;

/**
 * v0.8 shared dependency resolver for local and Git Sprig packages.
 *
 * <p>One resolved project model feeds {@code check}, {@code build},
 * {@code run}, {@code api}, {@code doctor}, {@code deps} and
 * {@code project}; no command re-implements resolution. Dependency aliases are
 * package-local, exports are enforced for external consumers, and Git
 * revisions come from the lockfile once resolved.
 *
 * <p>JVM (Maven) dependencies are represented in the lockfile model but
 * artifact resolution is not implemented in this alpha; see the validation
 * report for the recorded blocker.
 */
public final class DependencyResolver {
    private DependencyResolver() {
    }

    public static final class Package {
        public String id;
        public String owner;
        public final String alias;
        public final Project project;
        public final Path root;
        public final Path sourceRoot;
        public final List<String> exports;
        public final String manifestSha;
        public final String kind;
        public final String url;
        public final String requested;
        public final String revision;
        public final boolean portable;
        public final Map<String, Package> aliases = new LinkedHashMap<>();
        public final List<Package> direct = new ArrayList<>();

        Package(String alias, Project project, Path root, String kind, String url,
                String requested, String revision, String manifestSha, boolean portable) {
            this.alias = alias;
            this.project = project;
            this.root = root;
            this.sourceRoot = root.resolve(project.source).normalize().toAbsolutePath();
            this.exports = project.exports;
            this.manifestSha = manifestSha;
            this.kind = kind;
            this.url = url;
            this.requested = requested;
            this.revision = revision;
            this.portable = portable;
        }

        Lockfile.SprigEntry toEntry() {
            Lockfile.SprigEntry entry = new Lockfile.SprigEntry();
            entry.name = alias;
            entry.id = id;
            entry.owner = owner;
            entry.kind = kind;
            entry.projectName = project.name;
            entry.manifestSha = manifestSha;
            entry.source = project.source;
            entry.portable = portable;
            if (kind.equals("local")) {
                entry.path = root.toString();
            } else {
                entry.url = url;
                entry.requested = requested;
                entry.revision = revision;
            }
            return entry;
        }
    }

    public static final class Result implements Compiler.FileImportResolver {
        public final Package root;
        public final List<Package> packages = new ArrayList<>();
        public final Lockfile lock;

        Result(Package root, Lockfile lock) {
            this.root = root;
            this.lock = lock;
            collect(root);
        }

        private void collect(Package pkg) {
            packages.add(pkg);
            for (Package child : pkg.direct) {
                collect(child);
            }
        }

        public List<Lockfile.SprigEntry> entries() {
            List<Lockfile.SprigEntry> out = new ArrayList<>();
            for (Package pkg : packages) {
                if (!pkg.kind.equals("root")) {
                    out.add(pkg.toEntry());
                }
            }
            return out;
        }

        public Package packageOf(Path file) {
            Path canonical = canonical(file);
            Package best = null;
            for (Package pkg : packages) {
                if (canonical.startsWith(pkg.root)
                        && (best == null || pkg.root.getNameCount() > best.root.getNameCount())) {
                    best = pkg;
                }
            }
            return best;
        }

        @Override
        public Path resolve(Path fromFile, String spec, Diagnostics diagnostics,
                            String uri, Span span) {
            if (!spec.startsWith("@")) {
                Path base = fromFile.getParent() == null ? Path.of(".") : fromFile.getParent();
                return base.resolve(spec).normalize().toAbsolutePath();
            }
            int slash = spec.indexOf('/');
            if (slash < 2 || slash == spec.length() - 1) {
                diagnostics.add(Diagnostic.error(Codes.DEP_NOT_FOUND, Phase.NAME,
                        "Package import must be written as \"@alias/module.spr\": " + spec,
                        uri, span).withData(Map.of("import", spec)));
                return null;
            }
            String alias = spec.substring(1, slash);
            String rest = spec.substring(slash + 1).replace('\\', '/');
            if (rest.startsWith("/") || java.util.Arrays.asList(rest.split("/")).contains("..")) {
                diagnostics.error(Codes.DEP_NOT_FOUND, Phase.NAME, "Package import escapes source root: " + spec, uri, span);
                return null;
            }
            rest = Path.of(rest).normalize().toString().replace('\\', '/');
            Package owner = packageOf(fromFile);
            if (owner == null) {
                diagnostics.add(Diagnostic.error(Codes.DEP_NOT_FOUND, Phase.NAME,
                        "Package import outside a resolved project: " + spec, uri, span));
                return null;
            }
            Package dependency = owner.aliases.get(alias);
            if (dependency == null) {
                diagnostics.add(Diagnostic.error(Codes.DEP_NOT_FOUND, Phase.NAME,
                        "Package '" + owner.project.name + "' has no dependency alias '@" + alias + "'",
                        uri, span)
                        .withData(Map.of("alias", alias, "package", owner.project.name))
                        .withHint("Declare [[dependency]] name = \"" + alias
                                + "\" in " + owner.project.manifest.getFileName() + "."));
                return null;
            }
            Path target = dependency.sourceRoot.resolve(rest).normalize().toAbsolutePath();
            if (!target.startsWith(dependency.sourceRoot)) {
                diagnostics.add(Diagnostic.error(Codes.DEP_NOT_FOUND, Phase.NAME,
                        "Package import escapes the dependency source root: " + spec, uri, span)
                        .withData(Map.of("import", spec, "dependency", dependency.project.name)));
                return null;
            }
            if (!Files.isRegularFile(target)) {
                diagnostics.add(Diagnostic.error(Codes.DEP_NOT_FOUND, Phase.NAME,
                        "Module '" + rest + "' not found in dependency '" + alias + "'",
                        uri, span)
                        .withData(Map.of("alias", alias, "module", rest)));
                return null;
            }
            try {
                if (!target.toRealPath().startsWith(dependency.sourceRoot.toRealPath())) {
                    diagnostics.error(Codes.DEP_NOT_FOUND, Phase.NAME,
                            "Package import symlink escapes source root: " + spec, uri, span);
                    return null;
                }
            } catch (IOException e) {
                diagnostics.error(Codes.DEP_NOT_FOUND, Phase.NAME, "Cannot inspect package import: " + spec, uri, span);
                return null;
            }
            if (!dependency.exports.contains(rest)) {
                diagnostics.add(Diagnostic.error(Codes.PROJECT_NOT_EXPORTED, Phase.NAME,
                        "Dependency '" + alias + "' does not export '" + rest + "'",
                        uri, span)
                        .withData(Map.of("alias", alias, "module", rest,
                                "exports", dependency.exports))
                        .withHint("Only modules listed in the dependency's [project] exports are importable."));
                return null;
            }
            return target;
        }
    }

    /** Resolve mode: queries Git branches and produces a fresh lockfile. */
    public static Result resolve(Project project, boolean offline) {
        Lockfile lock = new Lockfile();
        lock.lockVersion = Lockfile.VERSION;
        lock.language = project.language;
        lock.compiler = sprig.compiler.tooling.Catalog.COMPILER_VERSION;
        Package root = build(project, "root", "", "root", null, null, null, lock, offline, true, new ArrayDeque<>());
        lock.sprig.addAll(new Result(root, lock).entries());
        return new Result(root, lock);
    }

    /** Build mode: uses the existing lockfile and never queries a moving ref. */
    public static Result load(Project project, Lockfile lock, boolean offline) {
        String digest;
        try {
            digest = Lockfile.digest(project.manifest);
        } catch (IOException e) {
            throw new DepError(Codes.PROJECT_MANIFEST,
                    "Cannot read sprig.toml: " + e.getMessage(), null);
        }
        if (lock.manifestSha == null || !lock.manifestSha.equals(digest)) {
            throw new DepError(Codes.PROJECT_LOCK_STALE,
                    "sprig.toml and sprig.lock do not match",
                    project.manifest.toString())
                    .with("hint", "Run `sprig resolve`.");
        }
        Package root = build(project, "root", "", "root", null, null, null, lock, offline, false, new ArrayDeque<>());
        if (new Result(root, lock).entries().size() != lock.sprig.size())
            throw new DepError(Codes.PROJECT_LOCK_STALE, "Lock contains unexpected dependency edges; run `sprig resolve`", null);
        return new Result(root, lock);
    }

    private static Package build(Project project, String id, String alias, String kind, String url,
                                 String requested, String revision, Lockfile lock, boolean offline,
                                 boolean resolveMode, Deque<Path> stack) {
        String manifestSha;
        try {
            manifestSha = Lockfile.digest(project.manifest);
        } catch (IOException e) {
            throw new DepError(Codes.PROJECT_MANIFEST,
                    "Cannot read manifest: " + e.getMessage(), project.manifest.toString());
        }
        Package pkg = new Package(alias, project, project.root, kind, url, requested, revision,
                manifestSha, false);
        if (!project.jvmDependencies.isEmpty())
            throw new DepError(Codes.DEP_MAVEN, "Maven dependencies are unsupported in package " + project.name, null);
        pkg.id = id;
        pkg.owner = id.equals("root") ? "" : id.substring(0, id.lastIndexOf("/@"));
        for (Project.Dependency dependency : project.dependencies) {
            String edgeId = Lockfile.edgeId(id, dependency.name);
            if (pkg.aliases.containsKey(dependency.name)) {
                throw new DepError(Codes.PROJECT_MANIFEST,
                        "Duplicate dependency alias '" + dependency.name + "' in "
                                + project.name, project.manifest.toString());
            }
            Path depRoot;
            String depKind;
            String depUrl = null;
            String depRequested = null;
            String depRevision = null;
            if (dependency.isLocal()) {
                depRoot = canonical(project.root.resolve(dependency.path));
                depKind = "local";
                if (!Files.isDirectory(depRoot)) {
                    throw new DepError(Codes.DEP_NOT_FOUND,
                            "Local dependency '" + dependency.name + "' not found at " + depRoot,
                            project.manifest.toString());
                }
            } else if (dependency.isGit()) {
                depKind = "git";
                depUrl = stripCredentials(dependency.git);
                depRequested = "branch:" + (dependency.branch == null ? "main" : dependency.branch);
                GitCache git = new GitCache(GitCache.defaultRoot(), offline);
                if (resolveMode) {
                    if (!git.available()) {
                        throw new DepError(Codes.DEP_GIT,
                                "Git is required for dependency '" + dependency.name
                                        + "' but 'git' is not available", null);
                    }
                    depRevision = git.remoteRevision(depUrl, dependency.branch == null
                            ? "main" : dependency.branch);
                } else {
                    Lockfile.SprigEntry entry = findLockEntry(lock, edgeId);
                    if (entry == null || !"git".equals(entry.kind)
                            || !depUrl.equals(entry.url)
                            || !depRequested.equals(entry.requested)) {
                        throw new DepError(Codes.PROJECT_LOCK_STALE,
                                "Lockfile entry for Git dependency '" + dependency.name
                                        + "' does not match sprig.toml", project.manifest.toString())
                                .with("hint", "Run `sprig resolve`.");
                    }
                    depRevision = entry.revision;
                }
                depRoot = canonical(git.materialize(depUrl, depRevision));
            } else {
                throw new DepError(Codes.DEP_NOT_FOUND,
                        "Dependency '" + dependency.name + "' needs a path or git field",
                        project.manifest.toString());
            }
            Path manifest = depRoot.resolve(Project.MANIFEST);
            if (!Files.isRegularFile(manifest)) {
                throw new DepError(Codes.DEP_NOT_FOUND,
                        "Dependency '" + dependency.name + "' has no " + Project.MANIFEST
                                + " at " + depRoot, null);
            }
            Path canonicalRoot = canonical(depRoot);
            if (stack.contains(canonicalRoot)) {
                List<String> chain = new ArrayList<>();
                chain.add(dependency.name);
                for (Path item : stack) {
                    chain.add(item.getFileName().toString());
                }
                chain.add(dependency.name);
                throw new DepError(Codes.DEP_CYCLE,
                        "Dependency cycle: " + String.join(" -> ", chain), null)
                        .with("cycle", chain);
            }
            Project depProject = Project.load(manifest);
            if (!compatibleLanguage(depProject.language)) {
                throw new DepError(Codes.PROJECT_UNSUPPORTED,
                        "Dependency '" + dependency.name + "' requires language "
                                + depProject.language + ", which this compiler does not support",
                        manifest.toString());
            }
            String depManifestSha = shaOf(manifest);
            if (!resolveMode) {
                Lockfile.SprigEntry entry = findLockEntry(lock, edgeId);
                if (entry == null || entry.manifestSha == null
                        || !entry.manifestSha.equals(depManifestSha)
                        || !depKind.equals(entry.kind) || !depProject.name.equals(entry.projectName)
                        || !depProject.source.equals(entry.source)
                        || (depKind.equals("local") && !depRoot.toString().equals(entry.path))) {
                    throw new DepError(Codes.PROJECT_LOCK_STALE,
                            "Dependency manifest changed for '" + dependency.name + "'",
                            manifest.toString()).with("hint", "Run `sprig resolve`.");
                }
            }
            stack.push(canonicalRoot);
            Package child = build(depProject, edgeId, dependency.name, depKind, depUrl, depRequested,
                    depRevision, lock, offline, resolveMode, stack);
            stack.pop();
            pkg.aliases.put(dependency.name, child);
            pkg.direct.add(child);
        }
        return pkg;
    }

    private static Lockfile.SprigEntry findLockEntry(Lockfile lock, String id) {
        for (Lockfile.SprigEntry entry : lock.sprig) {
            if (entry.id.equals(id)) {
                return entry;
            }
        }
        return null;
    }

    /** Credentials in a Git URL are unsupported in v0.8; strip them. */
    static String stripCredentials(String url) {
        int scheme = url.indexOf("://");
        if (scheme < 0) {
            return url;
        }
        int at = url.indexOf('@', scheme + 3);
        if (at < 0) {
            return url;
        }
        return url.substring(0, scheme + 3) + url.substring(at + 1);
    }

    private static boolean compatibleLanguage(String language) {
        if (language == null) {
            return true;
        }
        String normalized = language.trim();
        return normalized.equals("0.7") || normalized.equals("0.8")
                || normalized.equals("0.8-dev");
    }

    private static String shaOf(Path file) {
        try {
            return Lockfile.digest(file);
        } catch (IOException e) {
            throw new DepError(Codes.DEP_NOT_FOUND,
                    "Cannot read manifest " + file + ": " + e.getMessage(), null);
        }
    }

    private static Path canonical(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path.toAbsolutePath().normalize();
        }
    }
}
