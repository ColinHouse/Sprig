package sprig.compiler.cli;

import sprig.compiler.diag.Codes;
import sprig.compiler.diag.Diagnostic;
import sprig.compiler.diag.Diagnostics;
import sprig.compiler.diag.Phase;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * {@code sprig build --bundle}: a directory that runs the program on a machine
 * without a JDK. {@code lib/} holds the program's classes, the Sprig runtime
 * and every JAR of the locked classpath; {@code runtime/} is a jlink image of
 * the modules those JARs need, with the JDK's {@code legal/} notices intact;
 * {@code bin/} holds a POSIX sh launcher and a Windows launcher. The image runs
 * only on the operating system and architecture that built it. The user's
 * program is never executed while bundling.
 */
final class BundleCommand {
    private static final int MULTI_RELEASE = 21;

    private BundleCommand() {
    }

    /** What the bundle produced, for the text and JSON reports. */
    static final class Result {
        Path directory;
        Path unixLauncher;
        Path windowsLauncher;
        Path archive;
        List<String> modules = new ArrayList<>();
        long runtimeBytes;
        long libBytes;
        boolean cdsArchive;
        boolean launcherCdsArchive;
        String compression;

        Map<String, Object> details() {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("bundle", directory.toAbsolutePath().toString());
            details.put("launchers", List.of(unixLauncher.toAbsolutePath().toString(),
                    windowsLauncher.toAbsolutePath().toString()));
            details.put("modules", modules);
            details.put("runtimeBytes", runtimeBytes);
            details.put("libBytes", libBytes);
            details.put("jlinkCdsArchive", cdsArchive);
            details.put("launcherCdsArchive", launcherCdsArchive);
            details.put("compression", compression);
            details.put("platform", platform());
            details.put("javaVersion", Runtime.version().toString());
            details.put("archive", archive == null ? null : archive.toAbsolutePath().toString());
            return details;
        }
    }

    static Map<String, String> platform() {
        Map<String, String> platform = new LinkedHashMap<>();
        platform.put("os", System.getProperty("os.name"));
        platform.put("arch", System.getProperty("os.arch"));
        return platform;
    }

    static String platformNote() {
        Map<String, String> platform = platform();
        return "runs only on " + platform.get("os") + " " + platform.get("arch")
                + ", the platform that built it";
    }

    /** A file-system-safe bundle name from a project, bin or file name. */
    static String bundleName(String raw) {
        String name = raw.replaceAll("[^A-Za-z0-9._-]", "-").replaceAll("-+", "-");
        name = name.replaceAll("^[-.]+|[-.]+$", "");
        return name.isEmpty() ? "program" : name;
    }

    /**
     * Writes {@code outDir/name/}. Returns null after reporting a diagnostic.
     *
     * @param classesDir the classes {@code build} compiled: the program's and the runtime's
     * @param classpath  the locked classpath, JARs and directories, in order
     */
    static Result bundle(Path outDir, String name, Path classesDir, String mainClass, List<Path> classpath,
                         Map<Path, String> jarNames, boolean archive, Diagnostics diagnostics) throws IOException {
        Path javaHome = Path.of(System.getProperty("java.home"));
        Path jdeps = tool(javaHome, "jdeps");
        Path jlink = tool(javaHome, "jlink");
        if (jdeps == null || jlink == null) {
            diagnostics.add(Diagnostic.error(Codes.BUNDLE_TOOLS, Phase.CLI,
                    "The Java installation at " + javaHome + " has no "
                            + (jdeps == null ? "jdeps" : "jlink") + "; a bundle needs a full JDK, not a JRE",
                    null, null)
                    .withHint("Install a JDK (21 or newer) and run sprig with it on PATH or JAVA_HOME; "
                            + "sprig doctor shows the Java installation in use.")
                    .withRelatedHelp("build"));
            return null;
        }
        // No jmods/ precheck: since JDK 24 a JDK built as a linkable runtime
        // ships without jmods and jlink still images it; jlink itself reports
        // an installation it cannot link from.

        Result result = new Result();
        // The JDK tools run with their own working directory, so every path they see is absolute.
        Path bundleDir = outDir.toAbsolutePath().normalize().resolve(name);
        deleteBundle(bundleDir);
        if (Files.exists(bundleDir)) {
            diagnostics.add(Diagnostic.error(Codes.BUNDLE_LAYOUT, Phase.CLI,
                    "The previous bundle at " + bundleDir + " could not be removed", null, null)
                    .withHint("Close programs running from it, or delete the directory by hand, then build again.")
                    .withRelatedHelp("build"));
            return null;
        }
        Path lib = bundleDir.resolve("lib");
        Path bin = bundleDir.resolve("bin");
        Files.createDirectories(lib);
        Files.createDirectories(bin);
        result.directory = bundleDir;

        // lib/: the program, the runtime and the locked classpath.
        List<Path> jars = new ArrayList<>();
        Path programJar = lib.resolve(name + ".jar");
        Path runtimeJar = lib.resolve("sprig-runtime.jar");
        writeJar(programJar, classesDir, false, mainClass);
        writeJar(runtimeJar, classesDir, true, null);
        jars.add(programJar);
        jars.add(runtimeJar);
        Set<String> used = new HashSet<>(List.of(programJar.getFileName().toString(),
                runtimeJar.getFileName().toString()));
        int index = 0;
        for (Path entry : classpath) {
            index++;
            if (Files.isRegularFile(entry)) {
                // JvmClasspath already refused anything that is not a JAR or a directory.
                String fileName = jarNames.getOrDefault(entry.toAbsolutePath().normalize(), entry.getFileName().toString());
                if (!fileName.toLowerCase(Locale.ROOT).endsWith(".jar")) fileName = fileName + ".jar";
                Path target = lib.resolve(unique(fileName, used, index));
                Files.copy(entry, target);
                jars.add(target);
            } else if (Files.isDirectory(entry)) {
                String base = entry.toAbsolutePath().normalize().getFileName() == null ? "classes"
                        : entry.toAbsolutePath().normalize().getFileName().toString();
                Path target = lib.resolve(unique(bundleName(base) + ".jar", used, index));
                writeJar(target, entry, null, null);
                jars.add(target);
            } else {
                diagnostics.add(Diagnostic.error(Codes.BUNDLE_LAYOUT, Phase.CLI,
                        "Classpath entry " + entry + " does not exist", null, null)
                        .withHint("Run sprig resolve in the project, or fix the --classpath entry.")
                        .withRelatedHelp("build"));
                return null;
            }
        }
        result.libBytes = sizeOf(lib);

        // runtime/: the modules the JARs use, from jdeps, imaged by jlink. Every JAR is
        // analyzed as a class-path archive: a dependency JAR that declares a module
        // (sqlite-jdbc requires org.slf4j, which Maven marked optional) would otherwise
        // make jdeps resolve its requires and fail, so module descriptors are left out of
        // the copies jdeps reads. The copies are deleted afterwards.
        Path analysis = Files.createTempDirectory(bundleDir, "jdeps-");
        List<String> jdepsCommand = new ArrayList<>(List.of(jdeps.toString(), "--print-module-deps",
                "--ignore-missing-deps", "--multi-release", Integer.toString(MULTI_RELEASE)));
        List<Path> analyzed = new ArrayList<>();
        for (Path jar : jars) analyzed.add(withoutModuleDescriptor(jar, analysis));
        jdepsCommand.add("--class-path");
        jdepsCommand.add(String.join(java.io.File.pathSeparator, analyzed.stream().map(Path::toString).toList()));
        for (Path jar : analyzed) jdepsCommand.add(jar.toString());
        ToolRun deps = run(jdepsCommand, bundleDir);
        Main.deleteRecursively(analysis);
        if (deps.exitCode != 0) {
            diagnostics.add(Diagnostic.error(Codes.BUNDLE_JDEPS, Phase.CLI,
                    "jdeps could not analyze the bundle's JARs (exit " + deps.exitCode + "): "
                            + firstLine(deps.stderr.isBlank() ? deps.stdout : deps.stderr),
                    null, null)
                    .withHint("Run the printed jdeps command by hand to see the full report; a JAR that is "
                            + "not a valid ZIP, or a multi-release JAR newer than the JDK, is the usual cause.")
                    .withData(Map.of("command", String.join(" ", jdepsCommand), "stderr", deps.stderr))
                    .withRelatedHelp("build"));
            return null;
        }
        Set<String> modules = new TreeSet<>();
        modules.add("java.base");
        for (String line : deps.stdout.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("Warning") || trimmed.contains(" ")) continue;
            for (String module : trimmed.split(",")) {
                if (!module.isBlank()) modules.add(module.trim());
            }
        }
        result.modules = new ArrayList<>(modules);

        int feature = Runtime.version().feature();
        result.compression = feature >= 21 ? "zip-6" : "2";
        Path runtime = bundleDir.resolve("runtime");
        List<String> jlinkBase = new ArrayList<>(List.of(jlink.toString(), "--add-modules",
                String.join(",", modules), "--strip-debug", "--no-header-files", "--no-man-pages"));
        if (feature >= 21) {
            jlinkBase.add("--compress");
            jlinkBase.add("zip-6");
        } else {
            jlinkBase.add("--compress=2");
        }
        boolean cds = feature >= 19;
        // -XX:+AutoCreateSharedArchive exists since JDK 19; an older runtime image
        // rejects the option, so the launchers then run without an archive. The
        // launchers keep JVM errors on stderr but not class-data-sharing ones:
        // JDK 26 reports the archive a first run is about to create as an error.
        result.launcherCdsArchive = cds;
        List<String> jlinkCommand = new ArrayList<>(jlinkBase);
        if (cds) jlinkCommand.add("--generate-cds-archive");
        jlinkCommand.add("--output");
        jlinkCommand.add(runtime.toString());
        ToolRun image = run(jlinkCommand, bundleDir);
        if (image.exitCode != 0 && cds && mentionsCds(image.stderr + image.stdout)) {
            // The JDK's class-data-sharing archive cannot be generated on this
            // platform; the image is still complete without it.
            deleteBundle(runtime);
            cds = false;
            jlinkCommand = new ArrayList<>(jlinkBase);
            jlinkCommand.add("--output");
            jlinkCommand.add(runtime.toString());
            image = run(jlinkCommand, bundleDir);
        }
        if (image.exitCode != 0) {
            diagnostics.add(Diagnostic.error(Codes.BUNDLE_LAYOUT, Phase.CLI,
                    "jlink could not build the runtime image (exit " + image.exitCode + "): "
                            + firstLine(image.stderr.isBlank() ? image.stdout : image.stderr),
                    null, null)
                    .withHint("The JDK at " + javaHome + " must ship jmods/ for every module jdeps found ("
                            + String.join(", ", modules) + "), or be a linkable runtime (JDK 24+); a JRE or an image "
                            + "jlinked without --generate-linkable-runtime cannot build images. Run the printed jlink "
                            + "command by hand for the full report.")
                    .withData(Map.of("command", String.join(" ", jlinkCommand), "stderr", image.stderr))
                    .withRelatedHelp("build"));
            return null;
        }
        result.cdsArchive = cds;
        result.runtimeBytes = sizeOf(runtime);
        if (!Files.isDirectory(runtime.resolve("legal"))) {
            diagnostics.add(Diagnostic.error(Codes.BUNDLE_LAYOUT, Phase.CLI,
                    "The runtime image has no legal/ directory; the JDK's license notices must travel with the image",
                    null, null)
                    .withHint("Use a JDK distribution whose jmods carry legal notices (every OpenJDK build does).")
                    .withRelatedHelp("build"));
            return null;
        }

        // bin/: launchers that find the bundle from their own path.
        String stamp = stamp(mainClass, jars);
        result.unixLauncher = bin.resolve(name);
        result.windowsLauncher = bin.resolve(name + ".cmd");
        Files.writeString(result.unixLauncher, unixLauncher(name, mainClass, stamp, result.launcherCdsArchive),
                StandardCharsets.UTF_8);
        setExecutable(result.unixLauncher);
        Files.write(result.windowsLauncher,
                windowsLauncher(name, mainClass, stamp, result.launcherCdsArchive).getBytes(StandardCharsets.UTF_8));
        Files.writeString(bundleDir.resolve("README.txt"), readme(name, mainClass, result), StandardCharsets.UTF_8);

        if (archive) {
            result.archive = outDir.toAbsolutePath().normalize().resolve(name + ".zip");
            Files.deleteIfExists(result.archive);
            writeZip(result.archive, bundleDir, name);
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Launchers
    // ------------------------------------------------------------------

    private static String unixLauncher(String name, String mainClass, String stamp, boolean cdsArchive) {
        // The main class is single-quoted: generated class names contain '$'.
        // The class-data-sharing archive of the program's own classes lives in the
        // user's cache directory, keyed by the bundle's location (the JVM ties an
        // archive to the exact JAR paths), and JDK 19+ creates and refreshes it;
        // JVM logging is off so a stale archive is rebuilt silently. Without a
        // writable cache the program simply runs without an archive.
        String plain = "exec \"$HERE/runtime/bin/java\" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 \\\n"
                + "  -cp \"$HERE/lib/*\" '" + mainClass + "' \"$@\"\n";
        String head = "#!/bin/sh\n"
                + "set -eu\n"
                + "HERE=\"$(CDPATH= cd -- \"$(dirname -- \"$0\")/..\" && pwd)\"\n";
        if (!cdsArchive) return head + plain;
        return head
                + "KEY=; REST=\"$HERE\"\n"
                + "while :; do case $REST in */*) KEY=\"$KEY${REST%%/*}_\"; REST=${REST#*/};; *) KEY=\"$KEY$REST\"; break;; esac; done\n"
                + "CACHE=\"${XDG_CACHE_HOME:-${HOME:-}/.cache}/sprig/bundles/" + name + "-" + stamp + "/$KEY\"\n"
                + "if [ -n \"${HOME:-}${XDG_CACHE_HOME:-}\" ] && mkdir -p \"$CACHE\" 2>/dev/null; then\n"
                + "  exec \"$HERE/runtime/bin/java\" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 \\\n"
                + "    -XX:+AutoCreateSharedArchive \"-XX:SharedArchiveFile=$CACHE/app.jsa\" -Xshare:auto -Xlog:disable '-Xlog:all=error,cds*=off:stderr' \\\n"
                + "    -cp \"$HERE/lib/*\" '" + mainClass + "' \"$@\"\n"
                + "fi\n"
                + plain;
    }

    private static String windowsLauncher(String name, String mainClass, String stamp, boolean cdsArchive) {
        // CRLF, delayed expansion off: an inherited delayed-expansion state would
        // strip '!' from forwarded arguments and the bundle path.
        String plain = "\"%HERE%\\runtime\\bin\\java.exe\" -Dfile.encoding=UTF-8 -cp \"%HERE%\\lib\\*\" " + mainClass + " %*\r\n";
        String head = "@echo off\r\n"
                + "setlocal DisableDelayedExpansion\r\n"
                + "for %%I in (\"%~dp0..\") do set \"HERE=%%~fI\"\r\n";
        if (!cdsArchive) return head + plain + "exit /b %errorlevel%\r\n";
        return head
                + "set \"KEY=%HERE:\\=_%\"\r\n"
                + "set \"KEY=%KEY::=%\"\r\n"
                + "set \"CACHE=%LOCALAPPDATA%\\sprig\\bundles\\" + name + "-" + stamp + "\\%KEY%\"\r\n"
                + "if not \"%LOCALAPPDATA%\"==\"\" if not exist \"%CACHE%\" mkdir \"%CACHE%\" >nul 2>&1\r\n"
                + "if exist \"%CACHE%\" (\r\n"
                + "  \"%HERE%\\runtime\\bin\\java.exe\" -Dfile.encoding=UTF-8 -XX:+AutoCreateSharedArchive \"-XX:SharedArchiveFile=%CACHE%\\app.jsa\" -Xshare:auto -Xlog:disable \"-Xlog:all=error,cds*=off:stderr\" -cp \"%HERE%\\lib\\*\" " + mainClass + " %*\r\n"
                + ") else (\r\n"
                + "  " + plain
                + ")\r\n"
                + "exit /b %errorlevel%\r\n";
    }

    private static String readme(String name, String mainClass, Result result) {
        return name + ": a self-contained Sprig program.\n\n"
                + "Run bin/" + name + " (Linux, macOS) or bin\\" + name + ".cmd (Windows). No Java installation is needed.\n"
                + "This bundle " + platformNote() + ".\n\n"
                + "lib/      the program (" + name + ".jar, main class " + mainClass + "), the Sprig runtime and the program's JARs\n"
                + "runtime/  a Java runtime image (jlink) with the modules " + String.join(", ", result.modules) + ";\n"
                + "          runtime/legal/ holds the JDK's license notices (GPLv2 with the Classpath Exception) and must stay with it\n"
                + "bin/      the launchers" + (result.launcherCdsArchive
                        ? "; they keep a class-data-sharing archive under the user's cache directory for faster starts\n"
                        : " (built by a JDK older than 19: no class-data-sharing archive of the program)\n");
    }

    // ------------------------------------------------------------------
    // JARs and the archive
    // ------------------------------------------------------------------

    /**
     * Writes the classes under {@code root} into a JAR. {@code runtimeOnly} true
     * takes {@code sprig/runtime/**} only, false everything else, null all.
     */
    private static void writeJar(Path jar, Path root, Boolean runtimeOnly, String mainClass) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Created-By", "sprig build --bundle");
        if (mainClass != null) manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass);
        List<Path> files;
        try (Stream<Path> stream = Files.walk(root)) {
            files = stream.filter(Files::isRegularFile).sorted().toList();
        }
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            Set<String> directories = new LinkedHashSet<>();
            for (Path file : files) {
                String entryName = root.relativize(file).toString().replace('\\', '/');
                if (entryName.equals("META-INF/MANIFEST.MF")) continue;
                boolean runtimeClass = entryName.startsWith("sprig/runtime/");
                if (runtimeOnly != null && runtimeOnly != runtimeClass) continue;
                int slash = entryName.lastIndexOf('/');
                if (slash > 0) {
                    String directory = "";
                    for (String part : entryName.substring(0, slash).split("/")) {
                        directory = directory + part + "/";
                        if (directories.add(directory)) {
                            JarEntry dir = new JarEntry(directory);
                            dir.setTime(0L);
                            out.putNextEntry(dir);
                            out.closeEntry();
                        }
                    }
                }
                JarEntry entry = new JarEntry(entryName);
                entry.setTime(0L); // reproducible: the same classes give the same JAR
                out.putNextEntry(entry);
                Files.copy(file, out);
                out.closeEntry();
            }
        }
    }

    /**
     * A ZIP of the bundle directory with {@code name/} as the top-level entry.
     * java.util.zip stores no file modes, so the central directory is rewritten
     * afterwards with Unix attributes: unzip then restores the executable bits
     * of the launchers and of {@code runtime/bin/java}.
     */
    private static void writeZip(Path archive, Path bundleDir, String name) throws IOException {
        List<Path> files;
        try (Stream<Path> stream = Files.walk(bundleDir)) {
            files = stream.filter(p -> !p.equals(bundleDir)).sorted().toList();
        }
        List<Integer> modes = new ArrayList<>();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(buffer)) {
            for (Path file : files) {
                String relative = name + "/" + bundleDir.relativize(file).toString().replace('\\', '/');
                boolean directory = Files.isDirectory(file);
                ZipEntry entry = new ZipEntry(directory ? relative + "/" : relative);
                int mode = directory ? 0755 : mode(file, bundleDir);
                modes.add(mode | (directory ? 0040000 : 0100000));
                out.putNextEntry(entry);
                if (!directory) Files.copy(file, out);
                out.closeEntry();
            }
        }
        byte[] bytes = buffer.toByteArray();
        applyUnixModes(bytes, modes);
        Files.write(archive, bytes);
    }

    /** The file's mode: its POSIX permissions, or 0755 for launchers and runtime binaries elsewhere. */
    private static int mode(Path file, Path bundleDir) {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
            int mode = 0;
            for (PosixFilePermission permission : permissions) {
                mode |= switch (permission) {
                    case OWNER_READ -> 0400; case OWNER_WRITE -> 0200; case OWNER_EXECUTE -> 0100;
                    case GROUP_READ -> 0040; case GROUP_WRITE -> 0020; case GROUP_EXECUTE -> 0010;
                    case OTHERS_READ -> 0004; case OTHERS_WRITE -> 0002; case OTHERS_EXECUTE -> 0001;
                };
            }
            return mode;
        } catch (UnsupportedOperationException | IOException e) {
            String relative = bundleDir.relativize(file).toString().replace('\\', '/');
            boolean executable = relative.startsWith("bin/") || relative.startsWith("runtime/bin/")
                    || relative.startsWith("runtime/lib/") && (relative.endsWith(".so") || relative.endsWith(".dylib"));
            return executable ? 0755 : 0644;
        }
    }

    /**
     * Rewrites every central-directory header: "version made by" to Unix (3)
     * and the external attributes to {@code mode << 16}, in entry order.
     */
    private static void applyUnixModes(byte[] zip, List<Integer> modes) {
        ByteBuffer view = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN);
        int end = zip.length - 22;
        while (end >= 0 && view.getInt(end) != 0x06054b50) end--;
        if (end < 0) return;
        int count = view.getShort(end + 10) & 0xffff;
        int offset = view.getInt(end + 16);
        for (int i = 0; i < count && i < modes.size(); i++) {
            if (view.getInt(offset) != 0x02014b50) return;
            view.put(offset + 5, (byte) 3); // made by: Unix
            view.putInt(offset + 38, modes.get(i) << 16);
            int nameLength = view.getShort(offset + 28) & 0xffff;
            int extraLength = view.getShort(offset + 30) & 0xffff;
            int commentLength = view.getShort(offset + 32) & 0xffff;
            offset += 46 + nameLength + extraLength + commentLength;
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** The JAR itself when it declares no module; otherwise a copy without its module descriptors. */
    private static Path withoutModuleDescriptor(Path jar, Path directory) throws IOException {
        boolean modular = false;
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jar.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                String entryName = entries.nextElement().getName();
                if (entryName.equals("module-info.class")
                        || entryName.startsWith("META-INF/versions/") && entryName.endsWith("/module-info.class")) {
                    modular = true;
                    break;
                }
            }
            if (!modular) return jar;
            Path copy = directory.resolve(jar.getFileName());
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(copy))) {
                var all = zip.entries();
                while (all.hasMoreElements()) {
                    java.util.zip.ZipEntry entry = all.nextElement();
                    String entryName = entry.getName();
                    if (entryName.equals("module-info.class")
                            || entryName.startsWith("META-INF/versions/") && entryName.endsWith("/module-info.class")) continue;
                    out.putNextEntry(new ZipEntry(entryName));
                    if (!entry.isDirectory()) {
                        try (InputStream in = zip.getInputStream(entry)) {
                            in.transferTo(out);
                        }
                    }
                    out.closeEntry();
                }
            }
            return copy;
        }
    }

    /** Removes a previous bundle; jlink marks its files read-only on Windows, so the attribute is cleared first. */
    private static void deleteBundle(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (Stream<Path> stream = Files.walk(directory)) {
            for (Path path : stream.sorted(java.util.Comparator.reverseOrder()).toList()) {
                java.io.File file = path.toFile();
                if (!file.canWrite()) file.setWritable(true);
                Files.deleteIfExists(path);
            }
        }
    }

    private static Path tool(Path javaHome, String name) {
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        Path tool = javaHome.resolve("bin").resolve(windows ? name + ".exe" : name);
        return Files.isRegularFile(tool) ? tool : null;
    }

    private static String unique(String fileName, Set<String> used, int index) {
        if (used.add(fileName)) return fileName;
        String base = fileName.endsWith(".jar") ? fileName.substring(0, fileName.length() - 4) : fileName;
        String candidate = base + "-" + index + ".jar";
        used.add(candidate);
        return candidate;
    }

    private static long sizeOf(Path directory) throws IOException {
        try (Stream<Path> stream = Files.walk(directory)) {
            return stream.filter(Files::isRegularFile).mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException e) {
                    return 0L;
                }
            }).sum();
        }
    }

    private static void setExecutable(Path file) {
        try {
            Set<PosixFilePermission> permissions = new HashSet<>(Files.getPosixFilePermissions(file));
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            permissions.add(PosixFilePermission.GROUP_EXECUTE);
            permissions.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(file, permissions);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows: the .cmd launcher is the one that runs there.
        }
    }

    private static String stamp(String mainClass, List<Path> jars) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(mainClass.getBytes(StandardCharsets.UTF_8));
            for (Path jar : jars) {
                digest.update(jar.getFileName().toString().getBytes(StandardCharsets.UTF_8));
                digest.update(Long.toString(Files.size(jar)).getBytes(StandardCharsets.UTF_8));
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format("%02x", b));
            return hex.substring(0, 12);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private static boolean mentionsCds(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("cds") || lower.contains("shared archive") || lower.contains("class data sharing");
    }

    private static String firstLine(String text) {
        for (String line : text.split("\\R")) {
            if (!line.isBlank()) return line.trim();
        }
        return "(no output)";
    }

    private static final class ToolRun {
        int exitCode;
        String stdout = "";
        String stderr = "";
    }

    private static ToolRun run(List<String> command, Path workDir) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workDir.toFile());
        // The JDK tools would otherwise print "Picked up JAVA_TOOL_OPTIONS" into the report.
        builder.environment().remove("JAVA_TOOL_OPTIONS");
        builder.environment().remove("JDK_JAVA_OPTIONS");
        Process process = builder.start();
        process.getOutputStream().close();
        byte[][] captured = new byte[2][];
        Thread errReader = new Thread(() -> captured[1] = readAll(process.getErrorStream()));
        errReader.start();
        captured[0] = readAll(process.getInputStream());
        ToolRun result = new ToolRun();
        try {
            if (!process.waitFor(10, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                result.exitCode = 124;
            } else {
                result.exitCode = process.exitValue();
            }
            errReader.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            result.exitCode = 130;
        }
        result.stdout = new String(captured[0] == null ? new byte[0] : captured[0], StandardCharsets.UTF_8);
        result.stderr = new String(captured[1] == null ? new byte[0] : captured[1], StandardCharsets.UTF_8);
        return result;
    }

    private static byte[] readAll(InputStream stream) {
        try (stream) {
            return stream.readAllBytes();
        } catch (IOException e) {
            return new byte[0];
        }
    }
}
