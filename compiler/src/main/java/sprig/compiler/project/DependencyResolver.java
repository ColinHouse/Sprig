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
 * <p>JVM declarations across the resolved package tree are collected by
 * Apache Maven Resolver. Build consumers validate locked content without
 * re-resolving the Maven graph.
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
        public final String locator;
        public final String subdir;
        public final boolean portable;
        public final Map<String, Package> aliases = new LinkedHashMap<>();
        public final List<Package> direct = new ArrayList<>();
        /** Physical root for ownership checks; the root project's path may be a Windows 8.3 alias. */
        final Path physicalRoot;

        Package(String alias, Project project, Path root, String kind, String url,
                String requested, String revision, String locator, String subdir,
                String manifestSha, boolean portable) {
            this.alias = alias;
            this.project = project;
            this.root = root;
            this.physicalRoot = canonical(root);
            this.sourceRoot = root.resolve(project.source).normalize().toAbsolutePath();
            this.exports = project.exports;
            this.manifestSha = manifestSha;
            this.kind = kind;
            this.url = url;
            this.requested = requested;
            this.revision = revision;
            this.locator = locator;
            this.subdir = subdir;
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
                entry.path = locator;
            } else {
                entry.url = url;
                entry.requested = requested;
                entry.revision = revision;
                entry.subdir = subdir == null ? "." : subdir;
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
                if (canonical.startsWith(pkg.physicalRoot)
                        && (best == null || pkg.physicalRoot.getNameCount() > best.physicalRoot.getNameCount())) {
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

    /** Resolve mode: follows Git ref intent and produces a fresh exact-SHA lockfile. */
    public static Result resolve(Project project, boolean offline) {
        return resolve(project, offline, null);
    }

    /** Resolve a candidate manifest, optionally reusing matching locked Git revisions offline. */
    public static Result resolve(Project project, boolean offline, Lockfile previousLock) {
        Lockfile lock = new Lockfile();
        lock.lockVersion = Lockfile.VERSION;
        lock.language = project.language;
        lock.compiler = sprig.compiler.tooling.Catalog.COMPILER_VERSION;
        Package root = build(project, "root", "", "root", null, null, null, null, null, false, lock, offline, true,
                previousLock, new ArrayDeque<>());
        lock.sprig.addAll(new Result(root, lock).entries());
        return new Result(root, lock);
    }

    /** Build mode for a discovered project: reads its sprig.lock, which must exist. */
    public static Result loadLocked(Project project, boolean offline) throws IOException {
        Path lockPath = project.lockPath();
        if (!Files.isRegularFile(lockPath)) {
            throw new DepError(Codes.PROJECT_LOCK_MISSING,
                    "Project '" + project.name + "' has no sprig.lock", project.manifest.toString())
                    .with("hint", "Run `sprig resolve`.");
        }
        Lockfile lock = Lockfile.parse(Files.readString(lockPath));
        return load(project, lock, offline);
    }

    /** Build mode: uses the existing lockfile and never queries a moving ref. */
    public static Result load(Project project, Lockfile lock, boolean offline) {
        if (!sprig.compiler.tooling.Catalog.COMPILER_VERSION.equals(lock.compiler))
            throw new DepError(Codes.PROJECT_LOCK_STALE,
                    "Compiler version in sprig.lock does not match this SDK; run `sprig resolve`", null);
        String digest;
        try {
            digest = Lockfile.manifestDigest(project.manifest);
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
        Package root = build(project, "root", "", "root", null, null, null, null, null, false, lock, offline, false,
                null, new ArrayDeque<>());
        if (new Result(root, lock).entries().size() != lock.sprig.size())
            throw new DepError(Codes.PROJECT_LOCK_STALE, "Lock contains unexpected dependency edges; run `sprig resolve`", null);
        Result result = new Result(root, lock);
        MavenResolver.validateDeclarations(result);
        MavenResolver.load(lock);
        return result;
    }

    private static Package build(Project project, String id, String alias, String kind, String url,
                                 String requested, String revision, String locator, String subdir,
                                 boolean portable,
                                 Lockfile lock, boolean offline,
                                 boolean resolveMode, Lockfile previousLock, Deque<Path> stack) {
        String manifestSha;
        try {
            manifestSha = Lockfile.manifestDigest(project.manifest);
        } catch (IOException e) {
            throw new DepError(Codes.PROJECT_MANIFEST,
                    "Cannot read manifest: " + e.getMessage(), project.manifest.toString());
        }
        Package pkg = new Package(alias, project, project.root, kind, url, requested, revision,
                locator, subdir, manifestSha, portable);
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
            String depLocator = null;
            String depSubdir = null;
            boolean depPortable = false;
            if (dependency.isLocal()) {
                depRoot = canonical(project.root.resolve(dependency.path));
                depKind = "local";
                depPortable = dependency.isPortable();
                depLocator = depPortable ? dependency.locator() : depRoot.toString();
                if (!Files.isDirectory(depRoot)) {
                    throw new DepError(Codes.DEP_NOT_FOUND,
                            "Local dependency '" + dependency.name + "' not found at " + depRoot,
                            project.manifest.toString());
                }
            } else if (dependency.isGit()) {
                depKind = "git";
                GitCache.rejectCredentials(dependency.git);
                depUrl = stripCredentials(dependency.git);
                String refKind;
                String refValue;
                if (dependency.rev != null) {
                    refKind = "rev";
                    refValue = dependency.rev;
                } else if (dependency.tag != null) {
                    refKind = "tag";
                    refValue = dependency.tag;
                } else {
                    refKind = "branch";
                    refValue = dependency.branch == null ? "main" : dependency.branch;
                }
                depRequested = refKind + ":" + refValue;
                depSubdir = dependency.subdir == null ? "." : dependency.subdir;
                GitCache git = new GitCache(GitCache.defaultRoot(), offline);
                if (resolveMode) {
                    if (!git.available()) {
                        throw new DepError(Codes.DEP_GIT,
                                "Git is required for dependency '" + dependency.name
                                        + "' but 'git' is not available", null);
                    }
                    Lockfile.SprigEntry previous = offline && previousLock != null
                            ? findLockEntry(previousLock, edgeId) : null;
                    if (previous != null && "git".equals(previous.kind)
                            && depUrl.equals(previous.url) && depRequested.equals(previous.requested)
                            && depSubdir.equals(previous.subdir)) {
                        depRevision = previous.revision;
                    } else {
                        depRevision = git.remoteRevision(depUrl, refKind, refValue);
                    }
                } else {
                    Lockfile.SprigEntry entry = findLockEntry(lock, edgeId);
                    if (entry == null || !"git".equals(entry.kind)
                            || !depUrl.equals(entry.url)
                            || !depRequested.equals(entry.requested)
                            || !depSubdir.equals(entry.subdir)) {
                        throw new DepError(Codes.PROJECT_LOCK_STALE,
                                "Lockfile entry for Git dependency '" + dependency.name
                                        + "' does not match sprig.toml", project.manifest.toString())
                                .with("hint", "Run `sprig resolve`.");
                    }
                    depRevision = entry.revision;
                }
                Path checkoutRoot = canonical(git.materialize(depUrl, depRevision));
                depRoot = gitPackageRoot(checkoutRoot, depSubdir, dependency.name);
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
                        || (depKind.equals("local")
                            && (depPortable != entry.portable || !depLocator.equals(entry.path)))) {
                    throw new DepError(Codes.PROJECT_LOCK_STALE,
                            "Dependency manifest changed for '" + dependency.name + "'",
                            manifest.toString()).with("hint", "Run `sprig resolve`.");
                }
            }
            stack.push(canonicalRoot);
            Package child = build(depProject, edgeId, dependency.name, depKind, depUrl, depRequested,
                    depRevision, depLocator, depSubdir, depPortable, lock, offline, resolveMode,
                    previousLock, stack);
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
            return Lockfile.manifestDigest(file);
        } catch (IOException e) {
            throw new DepError(Codes.DEP_NOT_FOUND,
                    "Cannot read manifest " + file + ": " + e.getMessage(), null);
        }
    }

    /**
     * Physical path for containment checks. A working directory reached through a
     * symlink, junction or Windows 8.3 short name (for example a {@code RUNNER~1}
     * temporary directory) must not split one directory into two.
     */
    public static Path canonical(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path.toAbsolutePath().normalize();
        }
    }

    private static Path gitPackageRoot(Path checkoutRoot, String subdir, String alias) {
        Path selected = checkoutRoot;
        if (!subdir.equals(".")) {
            for (String component : subdir.split("/")) {
                selected = selected.resolve(component);
                if (Files.isSymbolicLink(selected))
                    throw new DepError(Codes.DEP_NOT_FOUND,
                            "Git dependency '" + alias + "' subdir contains a symbolic-link component: " + subdir,
                            null);
            }
        }
        selected = selected.toAbsolutePath().normalize();
        if (!selected.startsWith(checkoutRoot))
            throw new DepError(Codes.DEP_NOT_FOUND,
                    "Git dependency '" + alias + "' subdir escapes its repository: " + subdir, null);
        if (!Files.exists(selected, java.nio.file.LinkOption.NOFOLLOW_LINKS))
            throw new DepError(Codes.DEP_NOT_FOUND,
                    "Git dependency '" + alias + "' subdir has no " + Project.MANIFEST + ": " + subdir,
                    null);
        if (!Files.isDirectory(selected, java.nio.file.LinkOption.NOFOLLOW_LINKS))
            throw new DepError(Codes.DEP_NOT_FOUND,
                    "Git dependency '" + alias + "' subdir is not a directory: " + subdir, null);
        Path manifest = selected.resolve(Project.MANIFEST);
        if (Files.isSymbolicLink(manifest)
                || !Files.isRegularFile(manifest, java.nio.file.LinkOption.NOFOLLOW_LINKS))
            throw new DepError(Codes.DEP_NOT_FOUND,
                    "Git dependency '" + alias + "' subdir has no " + Project.MANIFEST + ": " + subdir,
                    null);
        try {
            Path realCheckout = checkoutRoot.toRealPath();
            Path realSelected = selected.toRealPath();
            if (!realSelected.startsWith(realCheckout))
                throw new DepError(Codes.DEP_NOT_FOUND,
                        "Git dependency '" + alias + "' subdir escapes its repository: " + subdir, null);
            return realSelected;
        } catch (IOException e) {
            throw new DepError(Codes.DEP_NOT_FOUND,
                    "Cannot inspect Git dependency '" + alias + "' subdir: " + e.getMessage(), null);
        }
    }
}
