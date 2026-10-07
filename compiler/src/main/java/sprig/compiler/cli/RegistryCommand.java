package sprig.compiler.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import sprig.compiler.diag.Codes;
import sprig.compiler.diag.Diagnostic;
import sprig.compiler.diag.JsonWriter;
import sprig.compiler.diag.Phase;
import sprig.compiler.project.DepError;
import sprig.compiler.project.GitCache;
import sprig.compiler.project.Project;
import sprig.compiler.project.Registry;
import sprig.compiler.project.Toml;

/**
 * {@code sprig search [TEXT]} lists the packages the registries know, and
 * {@code sprig publish} writes the current project's entry into a local
 * registry directory. Neither touches a lock: a registry only says where
 * packages live, and {@code sprig add NAME} turns that into a Git dependency.
 */
final class RegistryCommand {
    private RegistryCommand() {}

    static int run(String[] args) {
        String command = args[0];
        boolean json = false;
        for (String arg : args) if (arg.equals("--json")) json = true;
        try {
            return command.equals("search") ? search(args, json) : publish(args, json);
        } catch (UsageException e) {
            return failure(command, json, Diagnostic.error(Codes.CLI_OPTION, Phase.CLI, e.getMessage(), null, null), 2);
        } catch (Toml.TomlException e) {
            return failure(command, json, Diagnostic.error(Codes.PROJECT_MANIFEST, Phase.CLI,
                    "Invalid sprig.toml (line " + e.line + "): " + e.getMessage(), null, null), 1);
        } catch (DepError e) {
            Diagnostic diagnostic = Diagnostic.error(e.code, Phase.CLI, e.getMessage(), null, null);
            if (!e.data.isEmpty()) diagnostic.withData(new LinkedHashMap<>(e.data));
            if (e.data.get("detail") instanceof String detail) diagnostic.withHint(detail);
            return failure(command, json, diagnostic, 1);
        } catch (IOException e) {
            return failure(command, json, Diagnostic.error(Codes.PROJECT_MANIFEST, Phase.CLI,
                    command + " failed: " + e.getMessage(), null, null), 1);
        }
    }

    private static int search(String[] args, boolean json) throws DepError {
        String query = null;
        String registryName = null;
        boolean offline = false;
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--json" -> { }
                case "--offline" -> offline = true;
                case "--registry" -> {
                    if (i + 1 >= args.length || args[i + 1].startsWith("--")) throw new UsageException("--registry requires a name");
                    registryName = args[++i];
                }
                default -> {
                    if (arg.startsWith("-")) throw new UsageException("Unknown option " + arg);
                    if (query != null) throw new UsageException("sprig search takes at most one search text");
                    query = arg;
                }
            }
        }
        Path manifest = Project.findManifest(Path.of(""));
        Project project = manifest == null ? null : Project.load(manifest);
        List<Registry.Source> sources = Registry.sources(project);
        if (registryName != null) {
            String wanted = registryName;
            sources = sources.stream().filter(source -> source.name().equals(wanted)).toList();
            if (sources.isEmpty()) {
                throw new DepError(Codes.DEP_REGISTRY, "No registry named '" + registryName + "' is declared"
                        + (project == null ? " (no sprig.toml here, so only the default registry applies)" : " in sprig.toml"),
                        "Declare it with a [[registry]] table, or omit --registry.");
            }
        }
        List<Map<String, Object>> registries = new ArrayList<>();
        List<Registry.Entry> matches = new ArrayList<>();
        for (Registry.Source source : sources) {
            Path projectRoot = project == null ? null : project.root;
            Path root = Registry.follow(source, Registry.locate(source, projectRoot, offline), projectRoot, offline);
            List<Registry.Entry> entries = Registry.load(source, root);
            Map<String, Object> described = new LinkedHashMap<>();
            described.put("name", source.name());
            described.put("source", source.describe());
            described.put("packages", entries.size());
            registries.add(described);
            matches.addAll(Registry.search(entries, query));
        }
        if (json) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("query", query == null ? "" : query);
            details.put("registries", registries);
            details.put("packages", matches.stream().map(Registry.Entry::asMap).toList());
            System.out.print(JsonWriter.result(List.of(), manifest == null ? null : manifest.toUri().toString(),
                    "search", 0, null, details));
            return 0;
        }
        if (matches.isEmpty()) {
            System.out.println(query == null ? "No packages are listed." : "No package matches '" + query + "'.");
        }
        for (Registry.Entry entry : matches) {
            Registry.Release latest = entry.latest();
            long yanked = entry.releases.stream().filter(Registry.Release::yanked).count();
            System.out.println(entry.name + "  " + (latest == null ? "(no usable releases)" : latest.version())
                    + "  " + GitCache.redact(entry.git) + (entry.subdir == null ? "" : " " + entry.subdir)
                    + (entry.license == null ? "" : "  " + entry.license)
                    + (yanked == 0 ? "" : "  (" + yanked + " yanked)")
                    + (entry.description.isEmpty() ? "" : "\n    " + entry.description));
        }
        System.out.println(matches.size() + " package(s) in " + registries.size() + " registry(ies); "
                + "add one with: sprig add NAME [--version V]");
        return 0;
    }

    private static int publish(String[] args, boolean json) throws DepError, IOException {
        String registry = null;
        String git = null;
        String subdir = null;
        String refKind = null;
        String ref = null;
        String rev = null;
        String version = null;
        String description = null;
        String license = null;
        List<String> owners = new ArrayList<>();
        String yank = null;
        String reason = null;
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--json")) continue;
            if (!arg.startsWith("--")) throw new UsageException("Unexpected argument " + arg);
            if (i + 1 >= args.length || args[i + 1].startsWith("--")) throw new UsageException(arg + " requires a value");
            String value = args[++i];
            switch (arg) {
                case "--registry" -> registry = value;
                case "--git" -> git = value;
                case "--subdir" -> subdir = value;
                case "--tag", "--branch" -> {
                    if (refKind != null) throw new UsageException("Choose only one of --tag or --branch");
                    refKind = arg.substring(2);
                    ref = value;
                }
                case "--rev" -> {
                    if (!value.matches("[0-9a-fA-F]{40}")) throw new UsageException("--rev must be a full 40-character commit SHA");
                    rev = value.toLowerCase(java.util.Locale.ROOT);
                }
                case "--version" -> version = value;
                case "--description" -> description = value;
                case "--license" -> license = value;
                case "--owner" -> owners.add(value);
                case "--yank" -> yank = value;
                case "--reason" -> reason = value;
                default -> throw new UsageException("Unknown option " + arg);
            }
        }
        if (registry == null) throw new UsageException("sprig publish requires --registry NAME_OR_DIR, a local registry directory");
        if (yank == null && refKind == null && rev == null) {
            throw new UsageException("sprig publish requires --tag TAG (the commit is recorded), --branch NAME or --rev SHA: "
                    + "the Git ref that holds this version; or --yank VERSION --reason TEXT to withdraw a release");
        }
        if (yank != null && (refKind != null || rev != null)) throw new UsageException("--yank takes no --tag, --branch or --rev");
        if (yank != null && (reason == null || reason.isBlank())) throw new UsageException("--yank requires --reason TEXT");
        if (refKind != null && refKind.equals("branch") && rev != null) throw new UsageException("--rev pins a --tag, not a --branch");
        Path manifest = Project.findManifest(Path.of(""));
        if (manifest == null) {
            throw new DepError(Codes.PROJECT_MANIFEST, "No sprig.toml found in this directory or above",
                    "Run sprig publish inside the package to publish.");
        }
        Project project = Project.load(manifest);
        Registry.validateName(project.name, null);
        Registry.Source source = null;
        for (Registry.Source declared : project.registries) {
            if (declared.name().equals(registry)) source = declared;
        }
        Path root;
        if (source != null) {
            if (!source.isLocal()) {
                throw new DepError(Codes.DEP_REGISTRY, "Registry '" + registry + "' is a Git registry; publish into a local "
                        + "checkout of it and open a pull request there", "Clone the index repository and pass --registry with its directory.");
            }
            root = project.root.resolve(source.path()).normalize();
        } else {
            root = Path.of(registry).toAbsolutePath().normalize();
        }
        if (!Files.isDirectory(root)) {
            throw new DepError(Codes.DEP_REGISTRY, "Registry directory not found: " + root,
                    "Pass the name of a [[registry]] with a path, or an existing directory.");
        }
        Registry.Source local = new Registry.Source(source == null ? root.getFileName().toString() : source.name(),
                root.toString(), null, null, null);
        Registry.Index index = Registry.index(local, root);
        Path packages = root.resolve(Registry.PACKAGES_DIR);
        Path file = packages.resolve(project.name + ".toml");
        Registry.Entry existing = Files.isRegularFile(file) ? Registry.parse(local, file) : null;
        if (git == null) git = existing == null ? null : existing.git;
        if (git == null) throw new UsageException("sprig publish requires --git URL the first time a package is published");
        GitCache.rejectCredentials(git);
        if (subdir == null && existing != null) subdir = existing.subdir;
        if (subdir != null) subdir = Project.normalizeSubdir(subdir);
        if (description == null) description = existing == null ? "" : existing.description;
        if (license == null) license = existing != null && existing.license != null ? existing.license : project.license;
        if (owners.isEmpty() && existing != null) owners = new ArrayList<>(existing.owners);
        List<Registry.Release> releases = new ArrayList<>();
        Registry.Release release;
        boolean yanked = false;
        if (yank != null) {
            if (existing == null || existing.release(yank) == null) {
                throw new DepError(Codes.DEP_REGISTRY, "Package '" + project.name + "' has no version " + yank + " to yank in " + root,
                        "Run sprig search " + project.name + " for the listed versions.");
            }
            for (Registry.Release listed : existing.releases) {
                if (listed.version().equals(yank)) {
                    release = new Registry.Release(listed.version(), listed.refKind(), listed.ref(), listed.rev(), true, reason);
                    releases.add(release);
                } else {
                    releases.add(listed);
                }
            }
            release = existing.release(yank);
            yanked = true;
        } else {
            if (version == null) version = project.version;
            if (!Registry.isSemVer(version)) {
                throw new UsageException("Version " + version + " is not SemVer (MAJOR.MINOR.PATCH, optional -pre and +build)");
            }
            if (existing != null) {
                for (Registry.Release listed : existing.releases) {
                    if (listed.version().equals(version)) {
                        throw new DepError(Codes.DEP_REGISTRY, "Version " + version + " of '" + project.name
                                + "' is already published; a published release never changes",
                                "Publish a new version, or withdraw this one with --yank " + version + " --reason TEXT.");
                    }
                    releases.add(listed);
                }
            }
            if (refKind == null) {
                release = new Registry.Release(version, "rev", rev, null, false, null);
            } else if (refKind.equals("tag")) {
                if (rev == null) rev = resolveTag(project.root, git, ref);
                release = new Registry.Release(version, "tag", ref, rev, false, null);
            } else {
                release = new Registry.Release(version, "branch", ref, null, false, null);
            }
            releases.add(release);
        }
        Registry.Entry entry = new Registry.Entry(local.name(), project.name, description, git, subdir, license, owners, releases);
        if (index.strict()) Registry.requireStrict(entry);
        Files.createDirectories(packages);
        Files.writeString(file, Registry.render(entry), StandardCharsets.UTF_8);
        if (json) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("file", file.toString());
            details.put("yanked", yanked);
            details.put("release", release.version());
            details.put("package", entry.asMap());
            System.out.print(JsonWriter.result(List.of(), manifest.toUri().toString(), "publish", 0, null, details));
        } else {
            System.out.println((yanked ? "Yanked " : "Published ") + project.name + " " + release.version()
                    + (yanked ? "" : " (" + release.refKind() + " " + release.ref()
                            + (release.rev() != null && !release.refKind().equals("rev") ? ", commit " + release.rev() : "") + ")")
                    + " in " + file);
            System.out.println("Next: commit " + Registry.PACKAGES_DIR + "/" + project.name + ".toml and open a pull request to the "
                    + "index repository that changes only that file; its CI validates the entry. Others then run: sprig add "
                    + project.name);
        }
        return 0;
    }

    /** The commit a tag points at: from the package's own checkout when it is one, otherwise from the remote. */
    private static String resolveTag(Path projectRoot, String git, String tag) throws DepError {
        try {
            Process process = new ProcessBuilder("git", "-C", projectRoot.toString(), "rev-parse", "--verify", "--quiet", tag + "^{commit}")
                    .redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.waitFor() == 0 && output.matches("[0-9a-f]{40}")) return output;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }
        return new GitCache(GitCache.defaultRoot(), false).remoteRevision(git, "tag", tag);
    }

    private static int failure(String command, boolean json, Diagnostic diagnostic, int exitCode) {
        if (json) System.out.print(JsonWriter.result(List.of(diagnostic), null, command, exitCode, null));
        else System.err.println(diagnostic.format());
        return exitCode;
    }

    private static final class UsageException extends RuntimeException {
        UsageException(String message) {
            super(message);
        }
    }
}
