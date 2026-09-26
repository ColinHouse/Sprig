package sprig.compiler.jvm;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import sprig.compiler.diag.Codes;
import sprig.compiler.diag.Diagnostics;
import sprig.compiler.diag.Phase;

/** One resolved classpath for reflection, javac, the child JVM and CLI metadata. */
public final class JvmClasspath {
    private static List<Path> entries = List.of();
    private static ClassLoader loader = new ApplicationLoader(new URL[0]);

    private JvmClasspath() {}

    public static boolean configure(List<String> rawValues, Diagnostics diagnostics) {
        List<Path> resolved = new ArrayList<>();
        for (String value : rawValues) {
            for (String part : value.split(java.util.regex.Pattern.quote(File.pathSeparator), -1)) {
                if (part.isBlank()) {
                    diagnostics.error(Codes.JVM_CLASSPATH, Phase.JVM,
                            "Empty classpath entry", null, null);
                    return false;
                }
                Path path = Path.of(part).toAbsolutePath().normalize();
                if (!Files.isRegularFile(path) && !Files.isDirectory(path)) {
                    diagnostics.error(Codes.JVM_CLASSPATH, Phase.JVM,
                            "Classpath entry does not exist: " + path, null, null);
                    return false;
                }
                if (Files.isRegularFile(path) && !path.toString().endsWith(".jar")) {
                    diagnostics.error(Codes.JVM_CLASSPATH, Phase.JVM,
                            "Classpath file is not a JAR: " + path, null, null);
                    return false;
                }
                if (!resolved.contains(path)) resolved.add(path);
            }
        }
        try {
            URL[] urls = new URL[resolved.size()];
            for (int i = 0; i < resolved.size(); i++) urls[i] = resolved.get(i).toUri().toURL();
            if (loader instanceof URLClassLoader old) old.close();
            loader = new ApplicationLoader(urls);
            entries = List.copyOf(resolved);
            return true;
        } catch (IOException e) {
            diagnostics.error(Codes.JVM_CLASSPATH, Phase.JVM,
                    "Cannot resolve classpath: " + e.getMessage(), null, null);
            return false;
        }
    }

    /** Tool implementation JARs must never become implicit application dependencies. */
    private static final class ApplicationLoader extends URLClassLoader {
        ApplicationLoader(URL[] urls) { super(urls, ClassLoader.getPlatformClassLoader()); }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("sprig.runtime.")) return JvmClasspath.class.getClassLoader().loadClass(name);
            return super.loadClass(name, resolve);
        }
    }

    public static ClassLoader loader() { return loader; }
    public static List<Path> entries() { return entries; }

    public static String forProcess() {
        return String.join(File.pathSeparator, entries.stream().map(Path::toString).toList());
    }
}
