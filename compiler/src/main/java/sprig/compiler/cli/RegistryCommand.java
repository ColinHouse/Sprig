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
            Path root = Registry.locate(source, project == null ? null : project.root, offline);
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
            System.out.println(entry.name + "  " + (latest == null ? "(no releases)" : latest.version())
                    + "  " + GitCache.redact(entry.git) + (entry.subdir == null ? "" : " " + entry.subdir)
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
        String version = null;
        String description = null;
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
                case "--tag", "--branch", "--rev" -> {
                    if (refKind != null) throw new UsageException("Choose only one of --tag, --branch or --rev");
                    refKind = arg.substring(2);
                    ref = value;
                }
                case "--version" -> version = value;
                case "--description" -> description = value;
                default -> throw new UsageException("Unknown option " + arg);
            }
        }
        if (registry == null) throw new UsageException("sprig publish requires --registry NAME_OR_DIR, a local registry directory");
        if (refKind == null) throw new UsageException("sprig publish requires one of --tag TAG, --branch NAME or --rev SHA: the Git ref that holds this version");
        if (refKind.equals("rev") && !ref.matches("[0-9a-fA-F]{40}")) throw new UsageException("--rev must be a full 40-character commit SHA");
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
                        + "checkout of it and push that", "Clone the index repository and pass --registry with its directory.");
            }
            root = project.root.resolve(source.path()).normalize();
        } else {
            root = Path.of(registry).toAbsolutePath().normalize();
        }
        if (!Files.isDirectory(root)) {
            throw new DepError(Codes.DEP_REGISTRY, "Registry directory not found: " + root,
                    "Pass the name of a [[registry]] with a path, or an existing directory.");
        }
        Path packages = root.resolve(Registry.PACKAGES_DIR);
        Path file = packages.resolve(project.name + ".toml");
        Registry.Source local = new Registry.Source(source == null ? root.getFileName().toString() : source.name(),
                root.toString(), null, null, null);
        Registry.Entry existing = Files.isRegularFile(file) ? Registry.parse(local, file) : null;
        if (git == null) git = existing == null ? null : existing.git;
        if (git == null) throw new UsageException("sprig publish requires --git URL the first time a package is published");
        GitCache.rejectCredentials(git);
        if (subdir == null && existing != null) subdir = existing.subdir;
        if (subdir != null) subdir = Project.normalizeSubdir(subdir);
        if (version == null) version = project.version;
        if (!version.matches("[A-Za-z0-9][A-Za-z0-9_.+-]*")) throw new UsageException("Invalid version " + version);
        if (description == null) description = existing == null ? "" : existing.description;
        List<Registry.Release> releases = new ArrayList<>();
        boolean replaced = false;
        if (existing != null) {
            for (Registry.Release release : existing.releases) {
                if (release.version().equals(version)) {
                    replaced = true;
                    continue;
                }
                releases.add(release);
            }
        }
        Registry.Release release = new Registry.Release(version, refKind,
                refKind.equals("rev") ? ref.toLowerCase(java.util.Locale.ROOT) : ref);
        releases.add(release);
        Registry.Entry entry = new Registry.Entry(local.name(), project.name, description, git, subdir, releases);
        Files.createDirectories(packages);
        Files.writeString(file, Registry.render(entry), StandardCharsets.UTF_8);
        if (json) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("file", file.toString());
            details.put("replacedVersion", replaced);
            details.put("package", entry.asMap());
            System.out.print(JsonWriter.result(List.of(), manifest.toUri().toString(), "publish", 0, null, details));
        } else {
            System.out.println((replaced ? "Updated " : "Published ") + project.name + " " + version + " (" + refKind
                    + " " + ref + ") in " + file);
            System.out.println("Commit and push the registry so others can run: sprig add " + project.name);
        }
        return 0;
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
