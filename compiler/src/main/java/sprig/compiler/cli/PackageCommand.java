package sprig.compiler.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import sprig.compiler.diag.Codes;
import sprig.compiler.diag.Diagnostic;
import sprig.compiler.diag.JsonWriter;
import sprig.compiler.diag.Phase;
import sprig.compiler.diag.Span;
import sprig.compiler.project.DepError;
import sprig.compiler.project.DependencyResolver;
import sprig.compiler.project.GitCache;
import sprig.compiler.project.Lockfile;
import sprig.compiler.project.MavenResolver;
import sprig.compiler.project.Project;
import sprig.compiler.project.Toml;

/** Comment-preserving, transactional manifest dependency edits. */
final class PackageCommand {
    private PackageCommand() {}

    static int run(String[] args) {
        Request request = null;
        boolean json = contains(args, "--json");
        try {
            request = parse(args);
            Path manifest = Project.findManifest(Path.of(""));
            if (manifest == null) throw failure(Codes.PROJECT_MANIFEST,
                    "No sprig.toml found in this directory or above", null);
            byte[] original = Files.readAllBytes(manifest);
            Project current = Project.load(manifest);
            String source = new String(original, StandardCharsets.UTF_8);
            String candidateText = edit(source, current, request);
            byte[] candidateBytes = candidateText.getBytes(StandardCharsets.UTF_8);

            Path candidateFile = null;
            Path lockTemp = null;
            try {
                candidateFile = writeTemp(current.root, ".sprig-manifest-", ".tmp", candidateBytes);
                Project candidate = Project.load(candidateFile);
                Lockfile previous = readPreviousLock(current.lockPath(), request.offline);
                DependencyResolver.Result result = DependencyResolver.resolve(candidate, request.offline, previous);
                result.lock.manifestSha = Lockfile.digest(candidateFile);
                result.lock.language = candidate.language;
                result.lock.compiler = sprig.compiler.tooling.Catalog.COMPILER_VERSION;
                MavenResolver.resolve(result, request.offline);
                byte[] newLock = result.lock.render().getBytes(StandardCharsets.UTF_8);
                byte[] oldLock = Files.isRegularFile(current.lockPath())
                        ? Files.readAllBytes(current.lockPath()) : null;
                lockTemp = writeTemp(current.root, ".sprig-lock-", ".tmp", newLock);

                moveReplace(candidateFile, manifest);
                candidateFile = null;
                request.manifestChanged = true;
                try {
                    moveReplace(lockTemp, current.lockPath());
                    lockTemp = null;
                    request.lockChanged = oldLock == null || !Arrays.equals(oldLock, newLock);
                } catch (IOException publishError) {
                    try {
                        Path restore = writeTemp(current.root, ".sprig-manifest-restore-", ".tmp", original);
                        try {
                            moveReplace(restore, manifest);
                            request.manifestChanged = false;
                        } finally {
                            Files.deleteIfExists(restore);
                        }
                    } catch (IOException restoreError) {
                        publishError.addSuppressed(restoreError);
                    }
                    throw publishError;
                }
                return success(request, current, result, oldLock, newLock);
            } finally {
                if (candidateFile != null) Files.deleteIfExists(candidateFile);
                if (lockTemp != null) Files.deleteIfExists(lockTemp);
            }
        } catch (UsageException e) {
            return failure(args.length == 0 ? "add" : args[0], json,
                    Diagnostic.error(Codes.CLI_OPTION, Phase.CLI, e.getMessage(), null, null),
                    request, 2);
        } catch (Toml.TomlException e) {
            String command = request == null ? args[0] : request.action;
            Diagnostic diagnostic = Diagnostic.error(Codes.PROJECT_MANIFEST, Phase.CLI,
                    "Invalid sprig.toml (line " + e.line + "): " + e.getMessage(),
                    manifestUri(), Span.point(Math.max(0, e.line - 1), 0));
            return failure(command, json, diagnostic, request, 1);
        } catch (DepError e) {
            Diagnostic diagnostic = Diagnostic.error(e.code, Phase.CLI, e.getMessage(), manifestUri(), null);
            if (!e.data.isEmpty()) diagnostic.withData(new LinkedHashMap<>(e.data));
            Object hint = e.data.get("hint");
            if (hint instanceof String text) diagnostic.withHint(text);
            String command = request == null ? args[0] : request.action;
            return failure(command, json, diagnostic, request, 1);
        } catch (IOException | IllegalArgumentException e) {
            String command = request == null ? (args.length == 0 ? "add" : args[0]) : request.action;
            return failure(command, json,
                    Diagnostic.error(Codes.PROJECT_MANIFEST, Phase.CLI,
                            "Dependency update failed: " + e.getMessage(), manifestUri(), null),
                    request, 1);
        }
    }

    private static Lockfile readPreviousLock(Path path, boolean offline) throws IOException {
        if (!offline || !Files.isRegularFile(path)) return null;
        try {
            return Lockfile.parse(Files.readString(path, StandardCharsets.UTF_8));
        } catch (DepError e) {
            return null;
        }
    }

    private static int success(Request request, Project originalProject,
                               DependencyResolver.Result result, byte[] oldLock, byte[] newLock) {
        Map<String, Object> dependency = new LinkedHashMap<>();
        dependency.put("kind", request.kind);
        if (request.alias != null) dependency.put("alias", request.alias);
        if (request.path != null) dependency.put("path", request.path);
        if (request.url != null) {
            dependency.put("url", GitCache.redact(request.url));
            dependency.put("intentKind", request.refKind == null ? "branch" : request.refKind);
            dependency.put("intentValue", request.ref == null ? "main" : request.ref);
            dependency.put("subdir", request.subdir == null ? "." : request.subdir);
            Lockfile.SprigEntry entry = result.lock.sprig.stream()
                    .filter(item -> item.id.equals(Lockfile.edgeId("root", request.alias)))
                    .findFirst().orElse(null);
            if (entry != null) {
                dependency.put("resolvedCommit", entry.revision);
                dependency.put("projectName", entry.projectName);
                dependency.put("manifestSha256", entry.manifestSha);
            }
        } else if (request.alias != null && request.action.equals("add")) {
            Lockfile.SprigEntry entry = result.lock.sprig.stream()
                    .filter(item -> item.id.equals(Lockfile.edgeId("root", request.alias)))
                    .findFirst().orElse(null);
            if (entry != null) {
                dependency.put("projectName", entry.projectName);
                dependency.put("manifestSha256", entry.manifestSha);
            }
        }
        if (request.coordinate != null) {
            dependency.put("coordinate", request.coordinate);
            dependency.put("group", request.group);
            dependency.put("artifact", request.artifact);
            if (request.version != null) dependency.put("version", request.version);
            else if (request.action.equals("remove")) {
                dependency.put("removedVersions", originalProject.jvmDependencies.stream()
                        .filter(item -> item.group.equals(request.group)
                                && item.artifact.equals(request.artifact))
                        .map(item -> item.version).distinct().sorted().toList());
            }
        }
        boolean lockChanged = oldLock == null || !Arrays.equals(oldLock, newLock);
        if (request.json) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("manifestChanged", true);
            details.put("lockChanged", lockChanged);
            details.put("dependency", dependency);
            System.out.print(JsonWriter.result(List.of(), originalProject.manifest.toUri().toString(),
                    request.action, 0, null, details));
        } else {
            System.out.println((request.action.equals("add") ? "Added " : "Removed ")
                    + request.kind + " dependency " + request.declaredIdentity()
                    + " and updated " + originalProject.lockPath());
        }
        return 0;
    }

    private static int failure(String command, boolean json, Diagnostic diagnostic,
                               Request request, int exitCode) {
        if (json) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("manifestChanged", request != null && request.manifestChanged);
            details.put("lockChanged", request != null && request.lockChanged);
            if (request != null) details.put("dependency", request.asMap());
            System.out.print(JsonWriter.result(List.of(diagnostic), manifestUri(), command, exitCode, null, details));
        } else {
            System.err.println(diagnostic.format());
        }
        return 1;
    }

    private static String manifestUri() {
        Path path = Project.findManifest(Path.of(""));
        return path == null ? null : path.toUri().toString();
    }

    private static Request parse(String[] args) {
        if (args.length == 0 || !(args[0].equals("add") || args[0].equals("remove")))
            throw new UsageException("Expected `sprig add` or `sprig remove`");
        Request request = new Request(args[0]);
        List<String> positional = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--json") || arg.equals("--offline")) {
                if (!seen.add(arg)) throw new UsageException("Duplicate option " + arg);
                if (arg.equals("--json")) request.json = true;
                else request.offline = true;
                continue;
            }
            if (Set.of("--path", "--git", "--branch", "--tag", "--rev", "--subdir", "--jvm")
                    .contains(arg)) {
                if (!seen.add(arg)) throw new UsageException("Duplicate option " + arg);
                if (i + 1 >= args.length || args[i + 1].startsWith("--"))
                    throw new UsageException(arg + " requires a value");
                String value = args[++i];
                switch (arg) {
                    case "--path" -> request.path = value;
                    case "--git" -> request.url = value;
                    case "--branch" -> { request.refKind = "branch"; request.ref = value; }
                    case "--tag" -> { request.refKind = "tag"; request.ref = value; }
                    case "--rev" -> { request.refKind = "rev"; request.ref = value; }
                    case "--subdir" -> request.subdir = value;
                    case "--jvm" -> request.jvmValue = value;
                    default -> throw new AssertionError(arg);
                }
            } else if (arg.startsWith("-")) {
                throw new UsageException("Unknown option " + arg);
            } else positional.add(arg);
        }
        long refOptions = seen.stream().filter(Set.of("--branch", "--tag", "--rev")::contains).count();
        if (refOptions > 1) throw new UsageException("Choose only one of --branch, --tag or --rev");
        if (request.jvmValue != null) {
            if (!positional.isEmpty() || request.path != null || request.url != null
                    || request.refKind != null || request.subdir != null)
                throw new UsageException("--jvm cannot be combined with a Sprig dependency");
            request.kind = "jvm";
            String[] parts = request.jvmValue.split(":", -1);
            int expected = request.action.equals("add") ? 3 : 2;
            if (parts.length != expected || Arrays.stream(parts).anyMatch(String::isBlank))
                throw new UsageException(request.action.equals("add")
                        ? "--jvm expects GROUP:ARTIFACT:VERSION"
                        : "--jvm expects GROUP:ARTIFACT");
            request.group = parts[0];
            request.artifact = parts[1];
            request.version = expected == 3 ? parts[2] : null;
            try {
                if (request.version != null) MavenResolver.validate(request.group, request.artifact, request.version);
                else MavenResolver.validate(request.group, request.artifact, "1");
            } catch (DepError e) { throw new UsageException(e.getMessage()); }
            request.coordinate = request.version == null
                    ? request.group + ":" + request.artifact
                    : request.group + ":" + request.artifact + ":" + request.version;
            return request;
        }
        if (request.action.equals("add")) {
            if (positional.size() != 1) throw new UsageException("sprig add requires one dependency name");
            request.alias = positional.get(0);
            if (request.alias.isBlank() || request.alias.equals("std"))
                throw new UsageException("Dependency name must be non-empty and cannot be 'std'");
            if ((request.path == null) == (request.url == null))
                throw new UsageException("Choose exactly one of --path PATH or --git URL");
            if (request.path != null && (request.refKind != null || request.subdir != null))
                throw new UsageException("--branch, --tag, --rev and --subdir require --git");
            request.kind = request.path != null ? "sprig-path" : "sprig-git";
            if (request.url != null) {
                rejectCredentialedUrl(request.url);
                if (request.refKind == null) { request.refKind = "branch"; request.ref = "main"; }
                if (request.refKind.equals("rev") && !request.ref.matches("[0-9a-fA-F]{40}"))
                    throw new UsageException("Git --rev must be a full 40-character commit SHA");
                if (request.refKind.equals("rev")) request.ref = request.ref.toLowerCase(java.util.Locale.ROOT);
            }
        } else {
            if (positional.size() != 1 || request.path != null || request.url != null
                    || request.refKind != null || request.subdir != null)
                throw new UsageException("sprig remove requires a dependency name or --jvm GROUP:ARTIFACT");
            request.alias = positional.get(0);
            request.kind = "sprig";
        }
        return request;
    }

    private static void rejectCredentialedUrl(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            boolean scpUser = !url.contains("://") && url.matches("^[^/]+@[^:]+:.+");
            if (uri.getUserInfo() != null || scpUser)
                throw new UsageException("Credentialed Git URLs are unsupported; configure Git authentication outside the manifest");
        } catch (IllegalArgumentException e) {
            throw new UsageException("Invalid Git URL: " + e.getMessage());
        }
    }

    private static String edit(String original, Project project, Request request) {
        if (request.action.equals("add")) {
            if (request.kind.equals("sprig-path") || request.kind.equals("sprig-git")) {
                if (project.dependencies.stream().anyMatch(dep -> dep.name.equals(request.alias)))
                    throw failure(Codes.PROJECT_MANIFEST,
                            "Dependency alias already exists: " + request.alias, null);
                StringBuilder block = new StringBuilder("[[dependency]]\nname = ")
                        .append(toml(request.alias)).append('\n');
                if (request.path != null) block.append("path = ").append(toml(request.path)).append('\n');
                else {
                    block.append("git = ").append(toml(request.url)).append('\n')
                            .append(request.refKind).append(" = ").append(toml(request.ref)).append('\n');
                    if (request.subdir != null) block.append("subdir = ").append(toml(request.subdir)).append('\n');
                }
                return appendBlock(original, block.toString());
            }
            boolean duplicate = project.jvmDependencies.stream()
                    .anyMatch(dep -> dep.group.equals(request.group) && dep.artifact.equals(request.artifact));
            if (duplicate) throw failure(Codes.PROJECT_MANIFEST,
                    "JVM dependency already exists for " + request.group + ":" + request.artifact, null);
            return appendBlock(original, "[[jvm]]\ngroup = " + toml(request.group)
                    + "\nartifact = " + toml(request.artifact)
                    + "\nversion = " + toml(request.version) + "\n");
        }
        return removeBlock(original, request);
    }

    private static String removeBlock(String original, Request request) {
        List<String> lines = new ArrayList<>(Arrays.asList(original.split("\n", -1)));
        List<Block> blocks = blocks(lines);
        List<Block> matches = new ArrayList<>();
        String table = request.kind.equals("jvm") ? "jvm" : "dependency";
        for (Block block : blocks) {
            if (!block.name.equals(table)) continue;
            Toml parsed = Toml.parse(lines.subList(block.start, block.end));
            Map<String, String> values = parsed.entries(table).get(0);
            boolean match = request.kind.equals("jvm")
                    ? request.group.equals(values.get("group")) && request.artifact.equals(values.get("artifact"))
                    : request.alias.equals(values.get("name"));
            if (match) matches.add(block);
        }
        if (matches.isEmpty()) throw failure(Codes.DEP_NOT_FOUND,
                "Dependency not found: " + request.declaredIdentity(), null);
        for (int i = matches.size() - 1; i >= 0; i--) {
            Block block = matches.get(i);
            List<String> retained = new ArrayList<>();
            for (String line : lines.subList(block.start, block.end)) {
                if (commentOrBlank(line)) retained.add(line);
            }
            lines.subList(block.start, block.end).clear();
            lines.addAll(block.start, retained);
        }
        return String.join("\n", lines);
    }

    private static boolean commentOrBlank(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return true;
        boolean inString = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"' && (i == 0 || line.charAt(i - 1) != '\\')) inString = !inString;
            else if (c == '#' && !inString) return line.substring(i).trim().startsWith("#")
                    && line.substring(0, i).isBlank();
        }
        return false;
    }

    private static List<Block> blocks(List<String> lines) {
        List<Block> result = new ArrayList<>();
        int start = -1;
        String name = null;
        for (int i = 0; i <= lines.size(); i++) {
            String header = i == lines.size() ? null : tableHeader(lines.get(i));
            if (header == null) continue;
            if (start >= 0) result.add(new Block(start, i, name));
            start = i;
            name = header;
        }
        if (start >= 0) result.add(new Block(start, lines.size(), name));
        return result;
    }

    private static String tableHeader(String line) {
        int comment = line.indexOf('#');
        String trimmed = (comment >= 0 ? line.substring(0, comment) : line).trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return null;
        if (trimmed.startsWith("[[") && trimmed.endsWith("]]")) return trimmed.substring(2, trimmed.length() - 2).trim();
        return trimmed.substring(1, trimmed.length() - 1).trim();
    }

    private static String appendBlock(String original, String block) {
        String lineEnd = original.contains("\r\n") ? "\r\n" : "\n";
        String separator = original.isEmpty() ? "" : original.endsWith("\n")
                ? lineEnd : lineEnd + lineEnd;
        return original + separator + block.replace("\n", lineEnd);
    }

    private static String toml(String value) {
        if (value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
            throw new UsageException("Dependency values cannot contain quotes or line breaks");
        return "\"" + value + "\"";
    }

    private static Path writeTemp(Path root, String prefix, String suffix, byte[] bytes) throws IOException {
        Path path = Files.createTempFile(root, prefix, suffix);
        try {
            Files.write(path, bytes);
            return path;
        } catch (IOException e) {
            Files.deleteIfExists(path);
            throw e;
        }
    }

    private static void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException | UnsupportedOperationException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static DepError failure(String code, String message, String detail) {
        return new DepError(code, message, detail);
    }

    private static boolean contains(String[] values, String value) {
        for (String item : values) if (item.equals(value)) return true;
        return false;
    }

    private static final class Request {
        final String action;
        String kind;
        String alias;
        String path;
        String url;
        String refKind;
        String ref;
        String subdir;
        String jvmValue;
        String group;
        String artifact;
        String version;
        String coordinate;
        boolean json;
        boolean offline;
        boolean manifestChanged;
        boolean lockChanged;
        Request(String action) { this.action = action; }
        String declaredIdentity() { return coordinate != null ? coordinate : alias; }
        Map<String, Object> asMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("kind", kind);
            if (alias != null) map.put("alias", alias);
            if (path != null) map.put("path", path);
            if (url != null) map.put("url", GitCache.redact(url));
            if (refKind != null) map.put("intentKind", refKind);
            if (ref != null) map.put("intentValue", ref);
            if (subdir != null) map.put("subdir", subdir);
            if (coordinate != null) map.put("coordinate", coordinate);
            return map;
        }
    }
    private record Block(int start, int end, String name) {}
    private static final class UsageException extends RuntimeException {
        UsageException(String message) { super(message); }
    }
}
