package sprig.compiler.lsp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import sprig.compiler.Compilation;
import sprig.compiler.Compiler;
import sprig.compiler.diag.Codes;
import sprig.compiler.diag.Diagnostic;
import sprig.compiler.diag.Diagnostics;
import sprig.compiler.diag.Phase;
import sprig.compiler.diag.Span;
import sprig.compiler.jvm.JvmClasspath;
import sprig.compiler.project.DepError;
import sprig.compiler.project.DependencyResolver;
import sprig.compiler.project.MavenResolver;
import sprig.compiler.project.Project;
import sprig.compiler.project.Toml;

/**
 * Runs the compiler for the editor. A file inside a {@code sprig.toml}
 * project is checked with that project's locked dependencies and classpath,
 * as {@code sprig check} and {@code sprig test} do; other files are checked
 * on their own. Nothing is resolved or downloaded: a missing or stale lock is
 * reported like on the command line.
 */
final class Analyzer {
    private final List<String> extraClasspath;
    private final Map<Path, LoadedProject> projects = new HashMap<>();
    private List<String> configuredClasspath;

    private record LoadedProject(String stamp, DependencyResolver.Result graph, List<String> classpath) {
    }

    Analyzer(List<String> extraClasspath) {
        this.extraClasspath = List.copyOf(extraClasspath);
    }

    Analysis analyze(Path root, Map<Path, String> overlays) {
        Diagnostics diagnostics = new Diagnostics();
        DependencyResolver.Result graph = null;
        List<String> classpath = new ArrayList<>();
        Path parent = root.getParent() == null ? root : root.getParent();
        Path manifest = Project.findManifest(parent);
        if (manifest != null) {
            try {
                LoadedProject loaded = project(manifest);
                graph = loaded.graph;
                classpath.addAll(loaded.classpath);
            } catch (Toml.TomlException e) {
                diagnostics.add(Diagnostic.error(Codes.PROJECT_MANIFEST, Phase.CLI,
                        "Invalid sprig.toml (line " + e.line + "): " + e.getMessage(),
                        manifest.toUri().toString(), Span.point(Math.max(0, e.line - 1), 0)));
            } catch (DepError e) {
                Diagnostic diagnostic = Diagnostic.error(e.code, Phase.CLI, e.getMessage(),
                        manifest.toUri().toString(), null);
                if (!e.data.isEmpty()) {
                    diagnostic.withData(e.data);
                }
                if (e.data.get("hint") instanceof String hint) {
                    diagnostic.withHint(hint);
                }
                diagnostics.add(diagnostic);
            } catch (IOException e) {
                diagnostics.add(Diagnostic.error(Codes.PROJECT_MANIFEST, Phase.CLI,
                        "Project I/O failure: " + e.getMessage(), manifest.toUri().toString(), null));
            }
            if (diagnostics.hasErrors()) {
                return new Analysis(root, null, diagnostics.all(), overlays);
            }
        }
        classpath.addAll(extraClasspath);
        if (!classpath.equals(configuredClasspath)) {
            if (!JvmClasspath.configure(classpath, diagnostics)) {
                configuredClasspath = null;
                return new Analysis(root, null, diagnostics.all(), overlays);
            }
            configuredClasspath = List.copyOf(classpath);
        }
        Compiler compiler = new Compiler(diagnostics).setOverlays(overlays);
        if (graph != null) {
            compiler.setImportResolver(graph);
        }
        Compilation compilation = null;
        try {
            compilation = compiler.compile(root);
        } catch (IOException e) {
            diagnostics.add(Diagnostic.error(Codes.JVM_INTERNAL, Phase.CLI,
                    "I/O error while reading sources: " + e.getMessage(), root.toUri().toString(), null));
        } catch (RuntimeException | LinkageError | StackOverflowError e) {
            System.err.println("sprig lsp: internal compiler error while checking " + root);
            e.printStackTrace(System.err);
            diagnostics.add(Diagnostic.error(Codes.JVM_INTERNAL, Phase.CLI,
                    "Internal compiler error: " + e, root.toUri().toString(), null));
        }
        return new Analysis(root, compilation, diagnostics.all(), overlays);
    }

    /** Loads the locked project graph, reusing it while the manifest and lock are unchanged. */
    private LoadedProject project(Path manifest) throws IOException {
        Project project = Project.load(manifest);
        String stamp = stamp(manifest) + "|" + stamp(project.lockPath());
        LoadedProject cached = projects.get(project.root);
        if (cached != null && cached.stamp.equals(stamp)) {
            return cached;
        }
        DependencyResolver.Result graph = DependencyResolver.loadLocked(project, true);
        List<String> classpath = new ArrayList<>();
        for (Path path : MavenResolver.load(graph.lock)) {
            classpath.add(path.toString());
        }
        LoadedProject loaded = new LoadedProject(stamp, graph, List.copyOf(classpath));
        projects.put(project.root, loaded);
        return loaded;
    }

    private static String stamp(Path path) {
        try {
            return Files.isRegularFile(path)
                    ? Files.getLastModifiedTime(path).toMillis() + ":" + Files.size(path) : "-";
        } catch (IOException e) {
            return "?";
        }
    }
}
