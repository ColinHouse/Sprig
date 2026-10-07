package sprig.compiler.project;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import sprig.compiler.diag.Codes;

/** Maven effective models and conflict mediation are delegated to Apache Resolver. */
public final class MavenResolver {
    private MavenResolver() {}
    public static Path cacheRoot() {
        String override = System.getenv("SPRIG_MAVEN_CACHE");
        return (override == null ? Path.of(System.getProperty("user.home"), ".sprig", "maven")
                : Path.of(override)).toAbsolutePath().normalize();
    }
    private static String repository() {
        String value = System.getenv("SPRIG_MAVEN_REPOSITORY");
        if (value == null) return "https://repo.maven.apache.org/maven2/";
        java.net.URI uri;
        try { uri = java.net.URI.create(value); }
        catch (IllegalArgumentException e) { throw failure("Invalid Maven repository URI"); }
        if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !("https".equals(uri.getScheme()) || "file".equals(uri.getScheme())))
            throw failure("Maven repository must be https:// or file:// without credentials/query/fragment");
        return value.endsWith("/") ? value : value + "/";
    }
    public static void validate(String group, String artifact, String version) {
        if (!group.matches("[A-Za-z0-9_]+(?:[.-][A-Za-z0-9_]+)*")
                || !artifact.matches("[A-Za-z0-9_]+(?:[.-][A-Za-z0-9_]+)*")
                || !version.matches("[A-Za-z0-9][A-Za-z0-9_.-]*")
                || version.contains("..") || version.toUpperCase(java.util.Locale.ROOT).contains("SNAPSHOT")
                || List.of("LATEST", "RELEASE").contains(version.toUpperCase(java.util.Locale.ROOT)))
            throw failure("Only safe exact release coordinates are supported: " + group + ":" + artifact + ":" + version);
    }
    private static DepError failure(String message) { return new DepError(Codes.DEP_MAVEN, message, null); }
    private static String key(Artifact a) {
        return a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getExtension() + ":"
                + a.getClassifier() + ":" + a.getVersion();
    }
    public static Path contentPath(Lockfile.JvmEntry entry) {
        return cacheRoot().resolve("artifacts").resolve(entry.sha256 + "." + entry.extension);
    }
    public static List<Path> load(Lockfile lock) {
        List<Path> entries = new ArrayList<>();
        for (Lockfile.JvmEntry entry : lock.jvm) {
            Path file = contentPath(entry);
            if (!Files.isRegularFile(file)) throw new DepError(Codes.DEP_OFFLINE,
                    "Locked Maven artifact is not cached: " + entry.coordinate(), null)
                    .with("hint", "Run `sprig resolve` online to populate the cache.")
                    .with("coordinate", entry.coordinate());
            try {
                if (!Lockfile.digest(file).equals(entry.sha256)) throw new DepError(Codes.DEP_CHECKSUM,
                        "Locked Maven artifact checksum mismatch: " + entry.coordinate(), null)
                        .with("expectedSha256", entry.sha256).with("coordinate", entry.coordinate());
            } catch (IOException e) { throw failure("Cannot verify cached artifact: " + e.getMessage()); }
            if (entry.classpathOrder >= 0) entries.add(file);
        }
        entries.sort(java.util.Comparator.comparingInt(p -> {
            for (Lockfile.JvmEntry e : lock.jvm) if (contentPath(e).equals(p)) return e.classpathOrder;
            return Integer.MAX_VALUE;
        }));
        return entries;
    }
    public static void validateDeclarations(DependencyResolver.Result graph) {
        Set<String> expected = new LinkedHashSet<>();
        for (DependencyResolver.Package pkg : graph.packages)
            for (Project.JvmDependency d : pkg.project.jvmDependencies)
                expected.add(d.group + ":" + d.artifact + ":jar::" + d.version);
        Set<String> actual = new LinkedHashSet<>();
        for (Lockfile.JvmEntry e : graph.lock.jvm) if (e.direct) actual.add(e.coordinate());
        if (!actual.equals(expected) || (expected.isEmpty() && !graph.lock.jvm.isEmpty()))
            throw new DepError(Codes.PROJECT_LOCK_STALE, "Locked Maven roots do not match project declarations", null)
                    .with("hint", "Run `sprig resolve`.");
    }
    /** Called only by explicit dependency-resolution commands; consumers never invoke network resolution. */
    public static void resolve(DependencyResolver.Result graph, boolean offline) {
        List<Project.JvmDependency> declarations = new ArrayList<>();
        for (DependencyResolver.Package pkg : graph.packages) declarations.addAll(pkg.project.jvmDependencies);
        if (declarations.isEmpty()) return;
        Map<String, String> versions = new LinkedHashMap<>();
        for (Project.JvmDependency dep : declarations) {
            validate(dep.group, dep.artifact, dep.version);
            String previous = versions.putIfAbsent(dep.group + ":" + dep.artifact, dep.version);
            if (previous != null && !previous.equals(dep.version))
                throw failure("Conflicting direct Maven versions across packages: " + dep.group + ":" + dep.artifact);
        }
        Path root = cacheRoot();
        try {
            Files.createDirectories(root);
            // Cross-process lock covers model/cache access and content publication.
            try (FileChannel channel = FileChannel.open(root.resolve("resolve.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var ignored = channel.lock()) {
                resolveLocked(graph.lock, declarations, root, offline);
            }
        } catch (IOException e) { throw failure("Maven cache I/O failure: " + e.getMessage()); }
    }
    private static void resolveLocked(Lockfile lock, List<Project.JvmDependency> declarations,
                                      Path root, boolean offline) throws IOException {
        var locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        locator.setErrorHandler(new org.eclipse.aether.impl.DefaultServiceLocator.ErrorHandler() {
            @Override public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
                throw failure("Cannot initialize Apache Resolver: " + type.getName() + ": " + exception);
            }
        });
        RepositorySystem system = locator.getService(RepositorySystem.class);
        if (system == null) throw failure("Apache Resolver service is unavailable");
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        session.setOffline(offline);
        session.setChecksumPolicy(org.eclipse.aether.repository.RepositoryPolicy.CHECKSUM_POLICY_FAIL);
        session.setSystemProperties(System.getProperties());
        session.setArtifactDescriptorPolicy(new org.eclipse.aether.util.repository.SimpleArtifactDescriptorPolicy(false, false));
        session.setIgnoreArtifactDescriptorRepositories(true);
        session.setConfigProperty("aether.connector.connectTimeout", 15000);
        session.setConfigProperty("aether.connector.requestTimeout", 60000);
        String source = repository();
        String repositoryId = Lockfile.digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session,
                new LocalRepository(root.resolve("repository").resolve(repositoryId).toFile())));
        RemoteRepository remote = new RemoteRepository.Builder("sprig-" + repositoryId, "default", source).build();
        Map<String, Artifact> models = new LinkedHashMap<>();
        session.setRepositoryListener(new org.eclipse.aether.AbstractRepositoryListener() {
            @Override public void artifactResolved(org.eclipse.aether.RepositoryEvent event) {
                Artifact a = event.getArtifact();
                if (a != null && "pom".equals(a.getExtension()) && a.getFile() != null)
                    models.put(key(a), a);
            }
        });
        CollectRequest collect = new CollectRequest();
        collect.addRepository(remote);
        Set<String> direct = new LinkedHashSet<>();
        for (Project.JvmDependency dep : declarations) {
            Artifact a = new DefaultArtifact(dep.group, dep.artifact, "jar", dep.version);
            String k = key(a);
            if (direct.add(k)) collect.addDependency(new Dependency(a, "compile"));
        }
        try {
            DependencyResult result = system.resolveDependencies(session,
                    new DependencyRequest(collect, DependencyFilterUtils.classpathFilter("compile", "runtime")));
            Map<String, Artifact> selected = new LinkedHashMap<>();
            for (var r : result.getArtifactResults()) {
                Artifact a = r.getArtifact();
                validate(a.getGroupId(), a.getArtifactId(), a.getVersion());
                if (!a.getExtension().equals("jar")) throw failure("Unsupported runtime Maven artifact type: " + key(a));
                selected.putIfAbsent(key(a), a);
            }
            if (!selected.keySet().containsAll(direct))
                throw failure("A direct Maven coordinate was relocated; direct relocations are unsupported. "
                        + "Declare the repository's new exact coordinate explicitly.");
            lock.jvm.clear();
            int order = 0;
            for (Artifact a : selected.values()) {
                Lockfile.JvmEntry entry = store(a, source);
                entry.direct = direct.contains(key(a));
                entry.classpathOrder = order++;
                lock.jvm.add(entry);
            }
            for (Artifact a : models.values()) {
                validate(a.getGroupId(), a.getArtifactId(), a.getVersion());
                lock.jvm.add(store(a, source));
            }
            lock.jvmEdges.clear();
            collectEdges(result.getRoot(), "root", selected.keySet(), lock, new java.util.HashSet<>());
        } catch (org.eclipse.aether.resolution.DependencyResolutionException e) {
            throw new DepError(offline ? Codes.DEP_OFFLINE : Codes.DEP_MAVEN,
                    "Apache Maven Resolver failed: " + e.getMessage(), null)
                    .with("hint", offline ? "Resolve online once, then reuse sprig.lock offline." : "Check exact coordinates, repository access and dependency POMs.");
        }
    }
    private static void collectEdges(DependencyNode node, String parent, Set<String> selected,
                                     Lockfile lock, Set<DependencyNode> visited) {
        if (node.getData().get(ConflictResolver.NODE_DATA_WINNER) != null) return;
        String current = node.getArtifact() == null ? "root" : key(node.getArtifact());
        if (!current.equals("root") && !selected.contains(current)) return;
        if (!current.equals("root")) lock.jvmEdges.add(new Lockfile.JvmEdge(parent, current));
        if (!visited.add(node)) return;
        for (DependencyNode child : node.getChildren()) collectEdges(child, current, selected, lock, visited);
    }
    /** Resolver validates transfers, but cached bytes need independent integrity checks. */
    private static void verifyStaging(Artifact a, String repository, String sha256) throws IOException {
        String identity = repository + "\n" + key(a);
        Path marker = cacheRoot().resolve("verified").resolve(Lockfile.digest(
                identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)) + ".sha256");
        if (Files.exists(marker)) {
            String expected = Files.readString(marker).strip();
            if (!expected.matches("[0-9a-f]{64}") || !expected.equals(sha256))
                throw new DepError(Codes.DEP_CHECKSUM, "Resolver staging bytes changed: " + key(a), null)
                        .with("expectedSha256", expected).with("actualSha256", sha256)
                        .with("hint", "Remove the corrupted repository cache entry, then resolve again.");
            return;
        }
        Path file = a.getFile().toPath();
        boolean verified = false;
        for (String algorithm : List.of("SHA-512", "SHA-256", "SHA-1")) {
            String extension = algorithm.replace("-", "").toLowerCase(java.util.Locale.ROOT);
            Path sidecar = Path.of(file.toString() + "." + extension);
            if (!Files.isRegularFile(sidecar)) continue;
            String expected = Files.readString(sidecar).strip().split("\\s+")[0];
            try {
                String actual = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance(algorithm)
                        .digest(Files.readAllBytes(file)));
                if (!actual.equalsIgnoreCase(expected)) throw new DepError(Codes.DEP_CHECKSUM,
                        "Resolver staging checksum mismatch: " + key(a), null);
            } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
            verified = true;
            break;
        }
        if (!verified) throw failure("Cached Maven artifact has no source checksum: " + key(a)
                + "; remove its repository cache entry and resolve again.");
        Files.createDirectories(marker.getParent());
        Path temp = Files.createTempFile(marker.getParent(), ".verified-", ".tmp");
        try {
            Files.writeString(temp, sha256 + "\n");
            try { Files.move(temp, marker, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(temp, marker); }
        } finally { Files.deleteIfExists(temp); }
    }
    private static Lockfile.JvmEntry store(Artifact a, String repository) throws IOException {
        Lockfile.JvmEntry entry = new Lockfile.JvmEntry();
        entry.group = a.getGroupId(); entry.artifact = a.getArtifactId(); entry.version = a.getVersion();
        entry.extension = a.getExtension(); entry.classifier = a.getClassifier(); entry.repository = repository;
        Path file = a.getFile().toPath();
        entry.sha256 = Lockfile.digest(file);
        verifyStaging(a, repository, entry.sha256);
        Path destination = contentPath(entry);
        Files.createDirectories(destination.getParent());
        if (Files.exists(destination)) {
            if (!Lockfile.digest(destination).equals(entry.sha256)) throw new DepError(Codes.DEP_CHECKSUM,
                    "Corrupt Maven content cache: " + entry.coordinate(), null);
        } else {
            Path temp = Files.createTempFile(destination.getParent(), ".artifact-", ".tmp");
            try { Files.copy(file, temp, StandardCopyOption.REPLACE_EXISTING);
                if (!Lockfile.digest(temp).equals(entry.sha256)) throw failure("Artifact changed while caching");
                try { Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE); }
                catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(temp, destination); }
            } finally { Files.deleteIfExists(temp); }
        }
        return entry;
    }
}
