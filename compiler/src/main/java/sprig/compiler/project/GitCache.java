package sprig.compiler.project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import sprig.compiler.diag.Codes;

/**
 * v0.8 Git dependency support through the system {@code git} binary. Git is
 * only required for projects that actually declare Git dependencies. All
 * invocations use argument lists (never a shell) and the cache layout is
 * {@code ~/.sprig/git/repos/<id>.git} plus immutable
 * {@code ~/.sprig/git/checkouts/<id>/<revision>} trees.
 */
public final class GitCache {
    private final Path root;
    private final boolean offline;

    public GitCache(Path root, boolean offline) {
        this.root = root;
        this.offline = offline;
    }

    public static Path defaultRoot() {
        String home = System.getProperty("user.home");
        return Path.of(home, ".sprig", "git");
    }

    /** Redacts credentials from a URL for every user-visible output. */
    public static String redact(String url) {
        int query = url.indexOf('?');
        int fragment = url.indexOf('#');
        int end = url.length();
        if (query >= 0) end = Math.min(end, query);
        if (fragment >= 0) end = Math.min(end, fragment);
        String safe = url.substring(0, end);
        int scheme = safe.indexOf("://");
        if (scheme >= 0) {
            int at = safe.indexOf('@', scheme + 3);
            int slash = safe.indexOf('/', scheme + 3);
            if (at >= 0 && (slash < 0 || at < slash))
                safe = safe.substring(0, scheme + 3) + "***@" + safe.substring(at + 1);
        } else {
            int at = safe.indexOf('@');
            int colon = safe.indexOf(':');
            if (at >= 0 && (colon < 0 || at < colon))
                safe = "***@" + safe.substring(at + 1);
        }
        return query >= 0 ? safe + "?***" : safe;
    }

    /** Git credentials and query/fragment data are not supported in manifests. */
    public static void rejectCredentials(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            boolean scpUser = !url.contains("://") && url.matches("^[^/]+@[^:]+:.+");
            if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null || scpUser)
                throw new DepError(Codes.DEP_GIT,
                        "Git URLs with credentials, query parameters or fragments are unsupported", null);
        } catch (IllegalArgumentException e) {
            throw new DepError(Codes.DEP_GIT, "Invalid Git repository URL", null);
        }
    }

    public boolean available() {
        return run(List.of("--version"), null) == 0;
    }

    public String version() {
        String output = capture(List.of("--version"), null);
        return output == null ? null : output.trim();
    }

    /** Resolves branch/tag intent, or validates an exact caller-supplied commit SHA. */
    public String remoteRevision(String url, String kind, String ref) throws DepError {
        if (kind.equals("rev")) {
            if (ref == null || !ref.matches("[0-9a-f]{40}"))
                throw new DepError(Codes.DEP_GIT, "Git rev must be a full 40-character commit SHA", null);
            return ref;
        }
        if (offline) {
            throw new DepError(Codes.DEP_OFFLINE,
                    "Offline mode cannot resolve Git " + kind + " '" + ref + "' of " + redact(url),
                    "Run without --offline to resolve the ref, or keep the existing lock.");
        }
        String namespace = switch (kind) {
            case "branch" -> "refs/heads/";
            case "tag" -> "refs/tags/";
            default -> throw new DepError(Codes.DEP_GIT, "Unsupported Git ref intent: " + kind, null);
        };
        String target = namespace + ref;
        if (run(List.of("check-ref-format", target), null) != 0)
            throw new DepError(Codes.DEP_GIT, "Invalid Git " + kind + " name '" + ref + "'", null);
        List<String> query = new ArrayList<>(List.of("ls-remote", url, target));
        if (kind.equals("tag")) query.add(target + "^{}");
        String output = capture(query, null);
        if (output == null) {
            throw new DepError(Codes.DEP_GIT,
                    "Git " + kind + " lookup failed for " + redact(url)
                            + "; check the repository URL and network access", null);
        }
        String tagObjectRevision = null;
        for (String line : output.split("\n")) {
            String trimmed = line.trim();
            String[] parts = trimmed.split("\\s+", 2);
            if (parts.length != 2) continue;
            if (parts[1].equals(target + "^{}")) return parts[0];
            if (parts[1].equals(target)) {
                if (kind.equals("branch")) return parts[0];
                tagObjectRevision = parts[0];
            }
        }
        if (tagObjectRevision != null) return tagObjectRevision;
        throw new DepError(Codes.DEP_GIT, "Git " + kind + " '" + ref + "' not found in "
                + redact(url), null);
    }

    /** Materializes one immutable revision from a bare cache into a checkout. */
    public Path materialize(String url, String revision) throws DepError {
        if (revision == null || !revision.matches("[0-9a-f]{40}"))
            throw new DepError(Codes.DEP_GIT, "Invalid locked Git revision", null);
        Path mutex = root.resolve("locks").resolve(hash(url) + ".lock");
        try {
            Files.createDirectories(mutex.getParent());
            try (var channel = java.nio.channels.FileChannel.open(mutex,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE);
                 var lock = channel.lock()) {
                return materializeLocked(url, revision);
            }
        } catch (IOException e) {
            throw new DepError(Codes.DEP_GIT, "Git cache lock failure: " + e.getMessage(), null);
        }
    }

    private boolean validCheckout(Path checkout, String revision) {
        try {
            if (!Files.isRegularFile(checkout.resolve(".sprig-revision"))
                    || !Files.readString(checkout.resolve(".sprig-revision")).trim().equals(revision)) return false;
            String head = capture(List.of("-C", checkout.toString(), "rev-parse", "HEAD"), null);
            String status = capture(List.of("-C", checkout.toString(), "status", "--porcelain",
                    "--untracked-files=all", "--ignored"), null);
            return head != null && head.trim().equals(revision) && status != null
                    && status.lines().allMatch(line -> line.equals("?? .sprig-revision")
                            || line.equals("!! .sprig-revision"))
                    && matchesTree(checkout, revision);
        } catch (IOException e) { return false; }
    }

    /** Compare bytes to the commit tree even if Git index flags hide changes. */
    private boolean matchesTree(Path checkout, String revision) throws IOException {
        String tree = capture(List.of("-C", checkout.toString(), "ls-tree", "-r", "-z", revision), null);
        if (tree == null) return false;
        for (String entry : tree.split("\0")) {
            if (entry.isEmpty()) continue;
            int tab = entry.indexOf('\t');
            if (tab < 0) return false;
            String[] metadata = entry.substring(0, tab).split(" ");
            if (metadata.length != 3 || !metadata[1].equals("blob")) return false;
            Path file = checkout.resolve(entry.substring(tab + 1));
            byte[] bytes;
            if (metadata[0].equals("120000")) {
                if (!Files.isSymbolicLink(file)) return false;
                bytes = Files.readSymbolicLink(file).toString().getBytes(StandardCharsets.UTF_8);
            } else {
                if (Files.isSymbolicLink(file) || !Files.isRegularFile(file)) return false;
                // Git tracks the POSIX owner execute bit; Windows ACL execution
                // permission is a different property and cannot represent that mode.
                if (Files.getFileStore(file).supportsFileAttributeView("posix")
                        && Files.getPosixFilePermissions(file).contains(
                            java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE)
                            != metadata[0].equals("100755")) return false;
                bytes = Files.readAllBytes(file);
            }
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-1");
                digest.update(("blob " + bytes.length + "\0").getBytes(StandardCharsets.UTF_8));
                String actual = java.util.HexFormat.of().formatHex(digest.digest(bytes));
                if (!actual.equals(metadata[2])) return false;
            } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
        }
        return true;
    }

    private Path materializeLocked(String url, String revision) throws DepError {
        String id = hash(url);
        Path bare = root.resolve("repos").resolve(id + ".git");
        Path checkout = root.resolve("checkouts").resolve(id).resolve(revision);
        try {
            Files.createDirectories(checkout.getParent());
            if (validCheckout(checkout, revision)) return checkout;
            if (Files.exists(checkout))
                throw new DepError(Codes.DEP_GIT, "Git cache checkout is corrupted or modified: " + revision,
                        "Remove the corrupted cache checkout and run `sprig resolve`.");
            if (!Files.isDirectory(bare)) {
                if (offline) {
                    throw new DepError(Codes.DEP_OFFLINE,
                            "Git cache has no repository for " + redact(url)
                                    + "; required revision " + revision, bare.toString());
                }
                Files.createDirectories(bare.getParent());
                Path tempBare = Files.createTempDirectory(bare.getParent(), id + ".tmp-");
                try {
                    if (run(List.of("clone", "--bare", "--quiet", url, tempBare.toString()), null) != 0)
                        throw new DepError(Codes.DEP_GIT, "git clone failed for " + redact(url), null);
                    install(tempBare, bare);
                } finally { deleteRecursively(tempBare); }
            }
            if (run(List.of("--git-dir=" + bare, "cat-file", "-e", revision + "^{commit}"), null) != 0) {
                if (offline) {
                    throw new DepError(Codes.DEP_OFFLINE,
                            "Git cache is missing revision " + revision + " of " + redact(url),
                            bare.toString());
                }
                run(List.of("--git-dir=" + bare, "fetch", "--quiet", "origin",
                        "+refs/heads/*:refs/remotes/origin/*",
                        "+refs/tags/*:refs/tags/*"), null);
                if (run(List.of("--git-dir=" + bare, "cat-file", "-e", revision + "^{commit}"), null) != 0) {
                    throw new DepError(Codes.DEP_GIT,
                            "Git revision " + revision + " not found in " + redact(url), null);
                }
            }
            Path tempCheckout = Files.createTempDirectory(checkout.getParent(), revision + ".tmp-");
            try {
            if (run(List.of("clone", "--quiet", "--no-checkout", "--shared",
                    "--config", "core.autocrlf=false", "--config", "core.eol=lf",
                    "--config", "core.symlinks=true",
                    bare.toString(), tempCheckout.toString()), null) != 0) {
                throw new DepError(Codes.DEP_GIT, "git checkout failed for " + redact(url), null);
            }
            if (run(List.of("-C", tempCheckout.toString(), "checkout", "--quiet", "--detach", revision),
                    null) != 0) {
                throw new DepError(Codes.DEP_GIT, "git checkout failed for revision " + revision, null);
            }
            String head = capture(List.of("-C", tempCheckout.toString(), "rev-parse", "HEAD"), null);
            if (head == null || !head.trim().equals(revision)) {
                throw new DepError(Codes.DEP_GIT,
                        "Git checkout does not match locked revision " + revision, null);
            }
            Files.writeString(tempCheckout.resolve(".sprig-revision"), revision + "\n");
            if (!validCheckout(tempCheckout, revision))
                throw new DepError(Codes.DEP_GIT, "Git checkout verification failed", null);
            install(tempCheckout, checkout);
            } finally { deleteRecursively(tempCheckout); }
            return checkout;
        } catch (IOException e) {
            throw new DepError(Codes.DEP_GIT, "Git cache I/O failure: " + e.getMessage(), null);
        }
    }

    // ------------------------------------------------------------------

    private static void install(Path temp, Path destination) throws IOException {
        try { Files.move(temp, destination, java.nio.file.StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(temp, destination); }
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                sb.append(String.format("%02x", bytes[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            List<Path> paths = new ArrayList<>(walk.toList());
            paths.sort((a, b) -> b.getNameCount() - a.getNameCount());
            for (Path item : paths) {
                Files.deleteIfExists(item);
            }
        }
    }

    private int run(List<String> args, Path cwd) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(args);
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            if (cwd != null) {
                builder.directory(cwd.toFile());
            }
            builder.redirectErrorStream(true);
            Process process = builder.start();
            process.getInputStream().readAllBytes();
            return process.waitFor();
        } catch (IOException e) {
            return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    private String capture(List<String> args, Path cwd) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(args);
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            if (cwd != null) {
                builder.directory(cwd.toFile());
            }
            builder.redirectErrorStream(false);
            Process process = builder.start();
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.getErrorStream().readAllBytes();
            return process.waitFor() == 0 ? out : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
