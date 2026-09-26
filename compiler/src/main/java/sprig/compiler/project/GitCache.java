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
        int scheme = url.indexOf("://");
        if (scheme < 0) {
            return url;
        }
        int at = url.indexOf('@', scheme + 3);
        if (at < 0) {
            return url;
        }
        return url.substring(0, scheme + 3) + "***@" + url.substring(at + 1);
    }

    public boolean available() {
        return run(List.of("--version"), null) == 0;
    }

    public String version() {
        String output = capture(List.of("--version"), null);
        return output == null ? null : output.trim();
    }

    /** Resolves a branch to its current commit; network unless cached metadata suffices. */
    public String remoteRevision(String url, String branch) throws DepError {
        if (offline) {
            throw new DepError(Codes.DEP_OFFLINE,
                    "Offline mode cannot resolve Git branch '" + branch + "' of " + redact(url),
                    "Run without --offline to resolve the branch, or keep the existing lock.");
        }
        String output = capture(List.of("ls-remote", url, "refs/heads/" + branch), null);
        if (output == null) {
            throw new DepError(Codes.DEP_GIT, "git unavailable: cannot resolve " + redact(url), null);
        }
        for (String line : output.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.endsWith("refs/heads/" + branch)) {
                return trimmed.split("\\s+")[0];
            }
        }
        throw new DepError(Codes.DEP_GIT,
                "Git branch '" + branch + "' not found in " + redact(url), null);
    }

    /** Materializes one immutable revision from a bare cache into a checkout. */
    public Path materialize(String url, String revision) throws DepError {
        String id = hash(url);
        Path bare = root.resolve("repos").resolve(id + ".git");
        Path checkout = root.resolve("checkouts").resolve(id).resolve(revision);
        try {
            Files.createDirectories(checkout.getParent());
            if (Files.isRegularFile(checkout.resolve(".sprig-revision"))) {
                String recorded = Files.readString(checkout.resolve(".sprig-revision")).trim();
                if (recorded.equals(revision)) {
                    return checkout;
                }
            }
            if (!Files.isDirectory(bare)) {
                if (offline) {
                    throw new DepError(Codes.DEP_OFFLINE,
                            "Git cache has no repository for " + redact(url)
                                    + "; required revision " + revision, bare.toString());
                }
                Files.createDirectories(bare.getParent());
                if (run(List.of("clone", "--bare", "--quiet", url, bare.toString()), null) != 0) {
                    throw new DepError(Codes.DEP_GIT, "git clone failed for " + redact(url), null);
                }
            }
            if (run(List.of("--git-dir=" + bare, "cat-file", "-e", revision + "^{commit}"), null) != 0) {
                if (offline) {
                    throw new DepError(Codes.DEP_OFFLINE,
                            "Git cache is missing revision " + revision + " of " + redact(url),
                            bare.toString());
                }
                run(List.of("--git-dir=" + bare, "fetch", "--quiet", "origin",
                        "+refs/heads/*:refs/remotes/origin/*"), null);
                if (run(List.of("--git-dir=" + bare, "cat-file", "-e", revision + "^{commit}"), null) != 0) {
                    throw new DepError(Codes.DEP_GIT,
                            "Git revision " + revision + " not found in " + redact(url), null);
                }
            }
            deleteRecursively(checkout);
            if (run(List.of("clone", "--quiet", "--no-checkout", "--shared",
                    bare.toString(), checkout.toString()), null) != 0) {
                throw new DepError(Codes.DEP_GIT, "git checkout failed for " + redact(url), null);
            }
            if (run(List.of("-C", checkout.toString(), "checkout", "--quiet", "--detach", revision),
                    null) != 0) {
                throw new DepError(Codes.DEP_GIT, "git checkout failed for revision " + revision, null);
            }
            String head = capture(List.of("-C", checkout.toString(), "rev-parse", "HEAD"), null);
            if (head == null || !head.trim().equals(revision)) {
                throw new DepError(Codes.DEP_GIT,
                        "Git checkout does not match locked revision " + revision, null);
            }
            Files.writeString(checkout.resolve(".sprig-revision"), revision + "\n");
            return checkout;
        } catch (IOException e) {
            throw new DepError(Codes.DEP_GIT, "Git cache I/O failure: " + e.getMessage(), null);
        }
    }

    // ------------------------------------------------------------------

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
