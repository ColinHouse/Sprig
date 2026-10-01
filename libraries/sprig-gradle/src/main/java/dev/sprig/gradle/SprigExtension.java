package dev.sprig.gradle;

import java.io.File;
import org.gradle.api.Project;

/** Small consumer-facing configuration for the Sprig/Java boundary. */
public class SprigExtension {
    private final Project project;
    private String targetSourceSet = "main";
    private String executable;
    private File projectDirectory;
    private File testDirectory;
    private boolean testDirectoryConfigured;

    public SprigExtension(Project project) {
        this.project = project;
        this.projectDirectory = project.getProjectDir();
        this.testDirectory = project.file("tests");
    }

    public String getTargetSourceSet() {
        return targetSourceSet;
    }

    public void setTargetSourceSet(String targetSourceSet) {
        if (targetSourceSet == null || targetSourceSet.isBlank()) {
            throw new IllegalArgumentException("sprig.targetSourceSet must be a source-set name");
        }
        this.targetSourceSet = targetSourceSet.trim();
    }

    public String getExecutable() {
        return executable;
    }

    public void setExecutable(Object executable) {
        this.executable = executable == null ? null : executable.toString();
    }

    public File getProjectDirectory() {
        return projectDirectory;
    }

    public void setProjectDirectory(Object projectDirectory) {
        this.projectDirectory = project.file(projectDirectory);
        if (!testDirectoryConfigured) {
            this.testDirectory = new File(this.projectDirectory, "tests");
        }
    }

    public File getTestDirectory() {
        return testDirectory;
    }

    public void setTestDirectory(Object testDirectory) {
        File configured = testDirectory instanceof File file
                ? file : new File(String.valueOf(testDirectory));
        this.testDirectory = configured.isAbsolute() ? configured : new File(projectDirectory, configured.getPath());
        this.testDirectoryConfigured = true;
    }
}
