package dev.sprig.gradle;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;
import javax.inject.Inject;

/** Shared command execution and declared inputs for Sprig Gradle tasks. */
public abstract class SprigTask extends DefaultTask {
    private final ExecOperations execOperations;
    private File executable;
    private File projectDirectory;
    private FileCollection projectInputs;
    private FileCollection sprigClasspath;
    private FileCollection compilerClasspath;
    private String compilerVersion;
    private String sourceSetName;

    @Inject
    public SprigTask(ExecOperations execOperations) {
        this.execOperations = execOperations;
    }

    @Input
    public String getExecutable() {
        return executable == null ? "" : executable.getAbsolutePath();
    }

    @Internal
    public File getExecutableFile() {
        return executable;
    }

    public void setExecutableFile(File executable) {
        this.executable = executable;
    }

    @Internal
    public File getProjectDirectory() {
        return projectDirectory;
    }

    public void setProjectDirectory(File projectDirectory) {
        this.projectDirectory = projectDirectory;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getProjectInputs() {
        return projectInputs;
    }

    public void setProjectInputs(FileCollection projectInputs) {
        this.projectInputs = projectInputs;
    }

    @Classpath
    public FileCollection getSprigClasspath() {
        return sprigClasspath;
    }

    public void setSprigClasspath(FileCollection sprigClasspath) {
        this.sprigClasspath = sprigClasspath;
    }

    /** The ordered, platform-separated classpath is an input as order affects JVM lookup. */
    @Input
    public String getSprigClasspathArgument() {
        if (sprigClasspath == null) {
            return "";
        }
        // Java source sets include their output directories on their own
        // classpath. A clean NO-SOURCE output may not exist yet; javac treats
        // it as empty, but Sprig correctly rejects nonexistent entries. Keep
        // host dependencies and any output directory already built by Gradle.
        return sprigClasspath.getFiles().stream().filter(File::exists)
                .map(File::getAbsolutePath)
                .reduce((left, right) -> left + File.pathSeparator + right).orElse("");
    }

    @Classpath
    public FileCollection getCompilerClasspath() {
        return compilerClasspath;
    }

    public void setCompilerClasspath(FileCollection compilerClasspath) {
        this.compilerClasspath = compilerClasspath;
    }

    @Input
    public String getCompilerVersion() {
        return compilerVersion == null ? "" : compilerVersion;
    }

    public void setCompilerVersion(String compilerVersion) {
        this.compilerVersion = compilerVersion;
    }

    @Input
    public String getSourceSetName() {
        return sourceSetName == null ? "main" : sourceSetName;
    }

    public void setSourceSetName(String sourceSetName) {
        this.sourceSetName = sourceSetName;
    }

    protected ExecResult executeSprig(List<String> arguments) {
        List<String> argv = command(arguments);
        try {
            ExecResult result = execOperations.exec(spec -> configure(spec, argv,
                    projectDirectory, System.out, System.err));
            if (result.getExitValue() != 0) {
                throw new GradleException("Sprig command `" + display(arguments) + "` failed with exit code "
                        + result.getExitValue() + ". The compiler diagnostics above identify the source error.");
            }
            return result;
        } catch (GradleException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new GradleException("Could not run Sprig command `" + display(arguments) + "`: "
                    + e.getMessage(), e);
        }
    }

    protected CapturedCommand executeSprigCaptured(List<String> arguments) {
        List<String> argv = command(arguments);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        try {
            ExecResult result = execOperations.exec(spec -> configure(spec, argv,
                    projectDirectory, stdout, stderr));
            return new CapturedCommand(result.getExitValue(),
                    stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw new GradleException("Could not run Sprig command `" + display(arguments) + "`: "
                    + e.getMessage(), e);
        }
    }

    protected List<String> command(String... arguments) {
        return command(List.of(arguments));
    }

    protected List<String> command(List<String> arguments) {
        List<String> argv = new ArrayList<>();
        argv.add(executable.getAbsolutePath());
        argv.addAll(arguments);
        return argv;
    }

    private String display(List<String> arguments) {
        List<String> values = new ArrayList<>();
        values.add("sprig");
        values.addAll(arguments);
        return String.join(" ", values);
    }

    private static void configure(ExecSpec spec, List<String> argv, File workingDirectory,
                                  java.io.OutputStream stdout, java.io.OutputStream stderr) {
        spec.setExecutable(argv.get(0));
        spec.args(argv.subList(1, argv.size()));
        spec.setWorkingDir(workingDirectory);
        spec.setIgnoreExitValue(true);
        spec.setStandardOutput(stdout);
        spec.setErrorOutput(stderr);
    }

    protected record CapturedCommand(int exitCode, String stdout, String stderr) {
    }
}
