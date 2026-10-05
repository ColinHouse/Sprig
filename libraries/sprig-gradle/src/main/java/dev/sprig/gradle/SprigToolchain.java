package dev.sprig.gradle;

import groovy.json.JsonSlurper;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;

/** Resolved Sprig executable and the machine-readable toolchain facts it reports. */
public final class SprigToolchain {
    private final File executable;
    private final String compilerVersion;
    private final String compilerHome;
    private final File runtimeSource;
    private final List<File> compilerClasspath;

    private SprigToolchain(File executable, String compilerVersion, String compilerHome,
                           File runtimeSource, List<File> compilerClasspath) {
        this.executable = executable;
        this.compilerVersion = compilerVersion;
        this.compilerHome = compilerHome;
        this.runtimeSource = runtimeSource;
        this.compilerClasspath = List.copyOf(compilerClasspath);
    }

    public static SprigToolchain discover(Project project, SprigExtension extension,
                                          ExecOperations execOperations) {
        File executable = resolveExecutable(project, extension);
        Path probeDirectory = null;
        try {
            probeDirectory = Files.createTempDirectory("sprig-gradle-doctor-");
            Map<String, Object> doctor = jsonCommand(execOperations, executable,
                    List.of("doctor", "--json"), probeDirectory.toFile(), "doctor");
            Map<String, Object> capabilities = jsonCommand(execOperations, executable,
                    List.of("capabilities", "--json"), probeDirectory.toFile(), "capabilities");

            String version = string(doctor.get("compilerVersion"));
            String home = string(doctor.get("compilerHome"));
            String runtime = string(doctor.get("runtimeSource"));
            if (version == null || version.isBlank() || home == null || home.isBlank()
                    || runtime == null || runtime.isBlank()) {
                throw new GradleException("Sprig doctor --json did not report compilerVersion, "
                        + "compilerHome, and runtimeSource. Install a packaged Sprig SDK or use a supported compiler.");
            }
            if (!Boolean.TRUE.equals(doctor.get("javacAvailable"))) {
                throw new GradleException("The Java compiler (javac) is not available to the Sprig Gradle build. "
                        + "Run Gradle with a JDK, not a JRE.");
            }
            File runtimeDirectory = new File(runtime);
            if (!runtimeDirectory.isDirectory()) {
                throw new GradleException("Sprig reported a missing runtime source directory: " + runtime
                        + ". Reinstall the SDK or configure the executable from a complete Sprig checkout.");
            }
            Map<?, ?> features = map(capabilities.get("features"));
            if (!Boolean.TRUE.equals(features.get("emitJavaOnly"))
                    || !Boolean.TRUE.equals(features.get("testRunner"))) {
                throw new GradleException("Sprig " + version + " is not supported by dev.sprig: it must provide "
                        + "build --emit-java-only and sprig test. Install a compatible Sprig SDK.");
            }

            List<File> compilerClasspath = compilerClasspath(doctor.get("compilerClasspath"));
            if (compilerClasspath.isEmpty()) {
                throw new GradleException("Sprig doctor --json reported an empty compilerClasspath; "
                        + "use a complete SDK launcher.");
            }
            return new SprigToolchain(executable, version, home, runtimeDirectory, compilerClasspath);
        } catch (IOException e) {
            throw new GradleException("Could not inspect the Sprig toolchain: " + e.getMessage(), e);
        } finally {
            if (probeDirectory != null) {
                try {
                    deleteTree(probeDirectory);
                } catch (IOException ignored) {
                    // The temporary doctor directory contains only probe output.
                }
            }
        }
    }

    public File getExecutable() {
        return executable;
    }

    public String getCompilerVersion() {
        return compilerVersion;
    }

    public String getCompilerHome() {
        return compilerHome;
    }

    public File getRuntimeSource() {
        return runtimeSource;
    }

    public List<File> getCompilerClasspath() {
        return compilerClasspath;
    }

    private static File resolveExecutable(Project project, SprigExtension extension) {
        String extensionExecutable = extension.getExecutable();
        String propertyExecutable = value(project.findProperty("sprig.executable"));
        String environmentExecutable = System.getenv("SPRIG_EXECUTABLE");
        String configured = firstNonBlank(extensionExecutable, propertyExecutable, environmentExecutable);
        if (configured != null) {
            File explicit = project.file(configured).getAbsoluteFile();
            if (!explicit.isFile()) {
                String configuredBy = extensionExecutable != null ? "sprig.executable"
                        : propertyExecutable != null ? "-Psprig.executable" : "SPRIG_EXECUTABLE";
                throw missingExecutable("Explicit Sprig executable from " + configuredBy
                        + " does not exist: " + explicit);
            }
            return explicit;
        }

        String sdkHome = firstNonBlank(value(project.findProperty("sprigHome")),
                System.getenv("SPRIG_HOME"));
        if (sdkHome != null) {
            File packaged = launcher(new File(sdkHome));
            if (packaged.isFile()) {
                return packaged;
            }
            throw missingExecutable("SPRIG_HOME does not contain a Sprig launcher: " + packaged);
        }

        // An SDK bin directory holds both launchers; Windows cannot run the POSIX one.
        File onPath = findOnPath(launcherName());
        if (onPath != null) {
            return onPath;
        }
        File managed = launcher(new File(System.getProperty("user.home"), ".sprig/current"));
        if (managed.isFile()) {
            return managed;
        }
        File managedPath = new File(System.getProperty("user.home"), ".sprig/bin/" + launcherName());
        if (managedPath.isFile()) {
            return managedPath;
        }
        throw missingExecutable("No Sprig executable was found on PATH or in the SDK directory.");
    }

    private static File launcher(File sdkHome) {
        return new File(new File(sdkHome, "bin"), launcherName()).getAbsoluteFile();
    }

    private static String launcherName() {
        return isWindows() ? "sprig.cmd" : "sprig";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static File findOnPath(String name) {
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        for (String entry : path.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (entry.isBlank()) {
                continue;
            }
            File candidate = new File(entry, name);
            if (candidate.isFile() && (isWindows() || candidate.canExecute())) {
                return candidate.getAbsoluteFile();
            }
        }
        return null;
    }

    private static Map<String, Object> jsonCommand(ExecOperations execOperations, File executable,
                                                    List<String> arguments, File workingDirectory,
                                                    String command) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        List<String> argv = new ArrayList<>();
        argv.add(executable.getAbsolutePath());
        argv.addAll(arguments);
        ExecResult result;
        try {
            result = execOperations.exec(spec -> configure(spec, argv, workingDirectory, stdout, stderr));
        } catch (RuntimeException e) {
            throw new GradleException("Could not run " + executable + " " + String.join(" ", arguments)
                    + ". Check the executable and JDK.", e);
        }
        String output = stdout.toString(StandardCharsets.UTF_8);
        if (result.getExitValue() != 0) {
            throw new GradleException("Sprig " + command + " --json failed (exit " + result.getExitValue()
                    + "): " + nonEmpty(stderr.toString(StandardCharsets.UTF_8), output));
        }
        try {
            Object parsed = new JsonSlurper().parseText(output);
            if (!(parsed instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("expected a JSON object");
            }
            Map<String, Object> value = new LinkedHashMap<>();
            raw.forEach((key, item) -> value.put(String.valueOf(key), item));
            return value;
        } catch (RuntimeException e) {
            throw new GradleException("Sprig " + command + " --json returned invalid JSON: " + e.getMessage(), e);
        }
    }

    static void configure(ExecSpec spec, List<String> argv, File workingDirectory,
                          ByteArrayOutputStream stdout, ByteArrayOutputStream stderr) {
        spec.setExecutable(argv.get(0));
        spec.args(argv.subList(1, argv.size()));
        spec.setWorkingDir(workingDirectory);
        spec.setIgnoreExitValue(true);
        spec.setStandardOutput(stdout);
        spec.setErrorOutput(stderr);
    }

    private static List<File> compilerClasspath(Object raw) {
        if (!(raw instanceof String value) || value.isBlank()) {
            return List.of();
        }
        List<File> files = new ArrayList<>();
        for (String entry : value.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (!entry.isBlank()) {
                File file = new File(entry).getAbsoluteFile();
                if (file.exists()) {
                    files.add(file);
                }
            }
        }
        return files;
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }

    private static String value(Object object) {
        return object == null ? null : object.toString();
    }

    private static String string(Object object) {
        return object instanceof String value ? value : null;
    }

    private static Map<?, ?> map(Object object) {
        return object instanceof Map<?, ?> value ? value : Map.of();
    }

    private static String nonEmpty(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private static GradleException missingExecutable(String detail) {
        return new GradleException(detail + " Install Sprig and add its bin directory to PATH, set SPRIG_HOME, "
                + "or configure `sprig { executable = file(...) }`.");
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
