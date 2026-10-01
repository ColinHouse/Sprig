package dev.sprig.gradle;

import groovy.json.JsonSlurper;
import java.io.File;
import java.util.List;
import java.util.Map;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import javax.inject.Inject;

/** Concise, machine-useful summary of the resolved Sprig/Gradle boundary. */
public abstract class SprigInfoTask extends SprigTask {
    private File runtimeSource;
    private File bridgeSources;
    private File generatedJava;
    private File testDirectory;

    @Inject
    public SprigInfoTask(org.gradle.process.ExecOperations execOperations) {
        super(execOperations);
    }

    public void setRuntimeSource(File runtimeSource) {
        this.runtimeSource = runtimeSource;
    }

    public void setBridgeSources(File bridgeSources) {
        this.bridgeSources = bridgeSources;
    }

    public void setGeneratedJava(File generatedJava) {
        this.generatedJava = generatedJava;
    }

    public void setTestDirectory(File testDirectory) {
        this.testDirectory = testDirectory;
    }

    @TaskAction
    public void report() {
        CapturedCommand result = executeSprigCaptured(List.of("project", "--json"));
        if (result.exitCode() != 0) {
            throw new GradleException("Could not inspect the Sprig project. Keep sprig.toml in the project root.\n"
                    + result.stderr() + result.stdout());
        }
        String lockStatus = "unknown";
        String manifest = new File(getProjectDirectory(), "sprig.toml").getAbsolutePath();
        try {
            Object parsed = new JsonSlurper().parseText(result.stdout());
            Map<?, ?> root = (Map<?, ?>) parsed;
            Map<?, ?> project = (Map<?, ?>) root.get("project");
            if (project != null) {
                lockStatus = String.valueOf(project.get("lockStatus"));
                manifest = String.valueOf(project.get("manifest"));
            }
        } catch (RuntimeException e) {
            throw new GradleException("Sprig project --json returned invalid project metadata: " + e.getMessage(), e);
        }
        getLogger().lifecycle("Sprig executable: {}", getExecutableFile());
        getLogger().lifecycle("Compiler version: {}", getCompilerVersion());
        getLogger().lifecycle("Compiler home: {}", getCompilerHome());
        getLogger().lifecycle("Project manifest: {}", manifest);
        getLogger().lifecycle("Target source set: {}", getSourceSetName());
        getLogger().lifecycle("Bridge sources: {}", bridgeSources);
        getLogger().lifecycle("Generated Java: {}", generatedJava);
        getLogger().lifecycle("Runtime source: {}", runtimeSource);
        getLogger().lifecycle("Test directory: {}", testDirectory);
        getLogger().lifecycle("Lock status: {}", lockStatus);
        getLogger().lifecycle("Compile classpath entries: {}", getSprigClasspath().getFiles().stream()
                .filter(File::exists).count());
        if (getLogger().isInfoEnabled()) {
            getLogger().info("Sprig compile classpath: {}", getSprigClasspathArgument());
        }
    }

    private String compilerHome;

    @Input
    public String getCompilerHome() {
        return compilerHome;
    }

    public void setCompilerHome(String compilerHome) {
        this.compilerHome = compilerHome;
    }
}
