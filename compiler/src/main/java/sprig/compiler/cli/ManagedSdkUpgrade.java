package sprig.compiler.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Safe updater for user-scoped, versioned Sprig SDK installations. */
final class ManagedSdkUpgrade {
    private static final String API = "https://api.github.com/repos/ColinHouse/Sprig/releases?per_page=100";
    private static final String DOWNLOADS = "https://github.com/ColinHouse/Sprig/releases/download";
    private static final Pattern TAG = Pattern.compile("v[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9][A-Za-z0-9.-]*)?");
    private static final Pattern JSON_TAG = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"(v[^\\\"]+)\\\"");
    private static final Pattern JSON_VALUE = Pattern.compile("\\\"%s\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
    private static final long MAX_ARCHIVE = 256L * 1024 * 1024;
    private static final long MAX_EXPANDED = 768L * 1024 * 1024;

    private ManagedSdkUpgrade() {}

    static int run(String[] args) {
        boolean check;
        if (args.length == 1) check = false;
        else if (args.length == 2 && args[1].equals("--check")) check = true;
        else {
            System.err.println("Usage: sprig upgrade [--check]");
            return 2;
        }
        try {
            Layout layout = managedLayout();
            String latest = latestRelease();
            if (layout.activeTag.equals(latest)) {
                System.out.println("Sprig " + latest + " is already the latest published SDK.");
                return 0;
            }
            if (check) {
                System.out.println("Sprig " + layout.activeTag + " is installed; " + latest + " is available.");
                return 0;
            }
            installAndSwitch(layout, latest);
            System.out.println("Upgraded Sprig " + layout.activeTag + " to " + latest + ". Previous SDK versions are retained.");
            return 0;
        } catch (UpgradeFailure e) {
            System.err.println("sprig upgrade: " + e.getMessage());
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("sprig upgrade: interrupted; the active SDK was left unchanged");
            return 130;
        } catch (IOException e) {
            System.err.println("sprig upgrade: " + safeMessage(e) + "; the active SDK was left unchanged");
            return 1;
        }
    }

    private static Layout managedLayout() throws IOException, UpgradeFailure {
        String configured = System.getProperty("sprig.home");
        if (configured == null || configured.isBlank())
            throw new UpgradeFailure("cannot locate this SDK; run the user-scoped installer first");
        Path home = Path.of(configured).toAbsolutePath().normalize();
        boolean windows = System.getProperty("os.name", "").startsWith("Windows");
        if (Files.exists(home.resolve(".git"), LinkOption.NOFOLLOW_LINKS))
            throw new UpgradeFailure("this is a source checkout; update it with Git and rebuild with "
                    + (windows ? "py -3 scripts/build.py" : "scripts/build.sh"));
        if (windows)
            throw new UpgradeFailure("managed installation and upgrade support Linux and macOS; on Windows, "
                    + "download and verify a newer release ZIP and extract it in place of this SDK");

        Path root;
        Path parent = home.getParent();
        if (home.getFileName() != null && home.getFileName().toString().equals("current")) {
            root = parent;
        } else if (parent != null && parent.getFileName() != null
                && parent.getFileName().toString().equals("versions")) {
            root = parent.getParent();
        } else {
            throw new UpgradeFailure("this SDK is an unmanaged extracted ZIP; use scripts/install-sprig.sh to create a managed installation");
        }
        if (root == null || !Files.isDirectory(root.resolve("versions")))
            throw new UpgradeFailure("managed SDK version directory is missing; reinstall with scripts/install-sprig.sh");
        root = root.toRealPath();
        Path marker = home.resolve("sprig-install.json");
        String kind = metadata(marker, "installationKind");
        String version = metadata(marker, "version");
        String source = metadata(marker, "sourceReleaseTag");
        if (!"managed".equals(kind) || version == null || !version.equals(source) || !TAG.matcher(version).matches())
            throw new UpgradeFailure("SDK installation metadata is missing or invalid; use scripts/install-sprig.sh");

        Path current = root.resolve("current");
        if (!Files.isSymbolicLink(current))
            throw new UpgradeFailure("managed current pointer is missing or is not a symlink; active SDK was not changed");
        Path versions = root.resolve("versions").toRealPath();
        Path active = current.toRealPath();
        if (!active.startsWith(versions) || !Files.isDirectory(active))
            throw new UpgradeFailure("managed current pointer escapes the versions directory; active SDK was not changed");
        Path activeMarker = active.resolve("sprig-install.json");
        String activeTag = metadata(activeMarker, "version");
        if (activeTag == null || !"managed".equals(metadata(activeMarker, "installationKind"))
                || !activeTag.equals(metadata(activeMarker, "sourceReleaseTag"))
                || !TAG.matcher(activeTag).matches())
            throw new UpgradeFailure("active SDK metadata is missing or invalid; active SDK was not changed");
        return new Layout(root, versions, current, active, activeTag);
    }

    private static String latestRelease() throws IOException, InterruptedException, UpgradeFailure {
        String api = endpoint("SPRIG_TEST_RELEASES_API_URL", API);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(api)).timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "sprig-sdk-upgrade").GET().build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200)
            throw new UpgradeFailure("release lookup returned HTTP " + response.statusCode());
        Matcher matcher = JSON_TAG.matcher(response.body());
        while (matcher.find()) {
            String tag = matcher.group(1);
            if (TAG.matcher(tag).matches()) return tag;
        }
        throw new UpgradeFailure("official release list contains no supported Sprig SDK tag");
    }

    private static String endpoint(String variable, String official) throws UpgradeFailure {
        String value = System.getenv(variable);
        if (value == null || value.isBlank()) return official;
        if (value.startsWith("http://127.0.0.1:") || value.startsWith("http://localhost:")) return value;
        throw new UpgradeFailure(variable + " may only point to a local test fixture");
    }

    private static void installAndSwitch(Layout layout, String tag)
            throws IOException, InterruptedException, UpgradeFailure {
        String base = endpoint("SPRIG_TEST_RELEASE_BASE_URL", DOWNLOADS);
        Path staging = Files.createTempDirectory(layout.versions, ".upgrade-");
        Path pointerTemp = layout.root.resolve(".current-upgrade-" + ProcessHandle.current().pid());
        try {
            String archiveName = "sprig-" + tag + "-jdk.zip";
            Path archive = staging.resolve(archiveName);
            byte[] checksumBytes = download(base + "/" + tag + "/" + archiveName + ".sha256", 16 * 1024);
            String expected = parseChecksum(checksumBytes, archiveName);
            downloadTo(base + "/" + tag + "/" + archiveName, archive);
            String actual = sha256(archive);
            if (!actual.equalsIgnoreCase(expected))
                throw new UpgradeFailure("SHA-256 mismatch for " + archiveName + "; active SDK was left unchanged");

            Path unpacked = staging.resolve("unpacked");
            Files.createDirectory(unpacked);
            extract(archive, unpacked);
            Path candidate = unpacked.resolve("sprig-" + tag + "-jdk");
            if (!Files.isRegularFile(candidate.resolve("bin/sprig"))
                    || !Files.isExecutable(candidate.resolve("bin/sprig")))
                throw new UpgradeFailure("release archive lacks executable bin/sprig");
            smoke(candidate, tag);

            Path target = layout.versions.resolve(tag);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                String markerHash = metadata(target.resolve("sprig-install.json"), "sdkArchiveSha256");
                if (!"managed".equals(metadata(target.resolve("sprig-install.json"), "installationKind"))
                        || !tag.equals(metadata(target.resolve("sprig-install.json"), "sourceReleaseTag"))
                        || markerHash == null || !actual.equalsIgnoreCase(markerHash))
                    throw new UpgradeFailure("version directory already exists with different or unverified contents; refusing to overwrite " + target);
                smoke(target, tag);
            } else {
                Files.writeString(candidate.resolve("sprig-install.json"), metadataJson(tag, actual), StandardCharsets.UTF_8);
                try {
                    Files.move(candidate, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    throw new UpgradeFailure("filesystem does not support atomic version installation; active SDK was left unchanged");
                }
            }

            Files.createSymbolicLink(pointerTemp, layout.root.relativize(target));
            try {
                Files.move(pointerTemp, layout.current, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                throw new UpgradeFailure("filesystem does not support atomic SDK switching; active SDK was left unchanged");
            }
        } finally {
            Files.deleteIfExists(pointerTemp);
            deleteTree(staging);
        }
    }

    private static byte[] download(String url, int limit) throws IOException, InterruptedException, UpgradeFailure {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
                .header("User-Agent", "sprig-sdk-upgrade").GET().build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200)
            throw new UpgradeFailure("download returned HTTP " + response.statusCode() + " for " + safeUrl(url));
        if (response.body().length > limit)
            throw new UpgradeFailure("download exceeded the permitted size for " + safeUrl(url));
        return response.body();
    }

    private static void downloadTo(String url, Path destination) throws IOException, InterruptedException, UpgradeFailure {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(3))
                .header("User-Agent", "sprig-sdk-upgrade").GET().build();
        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(destination));
        if (response.statusCode() != 200) {
            Files.deleteIfExists(destination);
            throw new UpgradeFailure("SDK download returned HTTP " + response.statusCode() + "; active SDK was left unchanged");
        }
        if (Files.size(destination) > MAX_ARCHIVE) {
            Files.deleteIfExists(destination);
            throw new UpgradeFailure("SDK archive exceeds 256 MiB; active SDK was left unchanged");
        }
    }

    private static String parseChecksum(byte[] bytes, String archive) throws UpgradeFailure {
        String line = new String(bytes, StandardCharsets.US_ASCII).trim();
        Matcher matcher = Pattern.compile("^([0-9a-fA-F]{64})\\s+\\*?([^\\s]+)$").matcher(line);
        if (!matcher.matches() || !archive.equals(matcher.group(2)))
            throw new UpgradeFailure("official SHA-256 file is malformed or names a different archive");
        return matcher.group(1);
    }

    private static void extract(Path archive, Path destination) throws IOException, UpgradeFailure {
        Set<Path> written = new HashSet<>();
        long total = 0;
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                Path output = destination.resolve(entry.getName()).normalize();
                if (!output.startsWith(destination) || entry.getName().startsWith("/"))
                    throw new UpgradeFailure("SDK archive contains an unsafe path");
                if (!written.add(output)) throw new UpgradeFailure("SDK archive contains a duplicate path");
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Files.createDirectories(output.getParent());
                    try (var stream = Files.newOutputStream(output)) {
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = input.read(buffer)) >= 0) {
                            total += count;
                            if (total > MAX_EXPANDED) throw new UpgradeFailure("SDK archive expands beyond 768 MiB");
                            stream.write(buffer, 0, count);
                        }
                    }
                    if (output.toString().contains("/bin/sprig")) output.toFile().setExecutable(true, true);
                }
                input.closeEntry();
            }
        }
    }

    private static void smoke(Path sdk, String tag) throws IOException, InterruptedException, UpgradeFailure {
        Process process = new ProcessBuilder(sdk.resolve("bin/sprig").toString(), "version")
                .directory(sdk.toFile()).redirectErrorStream(true).start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            try { process.getInputStream().transferTo(output); }
            catch (IOException ignored) { }
        });
        reader.start();
        if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new UpgradeFailure("new SDK version smoke test timed out");
        }
        reader.join(1000);
        String result = output.toString(StandardCharsets.UTF_8).trim();
        if (process.exitValue() != 0)
            throw new UpgradeFailure("new SDK version smoke test failed: " + result);
        Matcher version = Pattern.compile("(?:sprig-compiler\\s+)?(v?[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9][A-Za-z0-9.-]*)?)").matcher(result);
        if (!version.find() || !(version.group(1).equals(tag) || ("v" + version.group(1)).equals(tag)))
            throw new UpgradeFailure("new SDK smoke test reported the wrong version: " + result);
    }

    private static String metadata(Path file, String key) throws IOException {
        if (!Files.isRegularFile(file)) return null;
        String text = Files.readString(file, StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile(String.format(JSON_VALUE.pattern(), Pattern.quote(key))).matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String metadataJson(String tag, String digest) {
        return "{\n  \"installationKind\": \"managed\",\n  \"version\": \"" + tag
                + "\",\n  \"sourceReleaseTag\": \"" + tag
                + "\",\n  \"sdkArchiveSha256\": \"" + digest + "\"\n}\n";
    }

    private static String sha256(Path file) throws IOException, UpgradeFailure {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new UpgradeFailure("JDK does not provide SHA-256");
        }
    }

    private static String safeUrl(String url) {
        try { return URI.create(url).getHost(); }
        catch (RuntimeException e) { return "release server"; }
    }

    private static String safeMessage(IOException e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? "I/O operation failed" : message;
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private record Layout(Path root, Path versions, Path current, Path active, String activeTag) {}
    private static final class UpgradeFailure extends Exception {
        UpgradeFailure(String message) { super(message); }
    }
}
