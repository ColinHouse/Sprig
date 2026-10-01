package dev.sprig.gradle;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import javax.inject.Inject;

/** Runs Sprig tests without updating or resolving the project lock. */
public abstract class SprigTestTask extends SprigTask {
    private File testDirectory;
    private FileCollection testSources;

    @Inject
    public SprigTestTask(org.gradle.process.ExecOperations execOperations) {
        super(execOperations);
    }

    @Input
    public String getTestDirectoryPath() {
        return testDirectory == null ? "tests" : testDirectory.getAbsolutePath();
    }

    public void setTestDirectory(File testDirectory) {
        this.testDirectory = testDirectory;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getTestSources() {
        return testSources;
    }

    public void setTestSources(FileCollection testSources) {
        this.testSources = testSources;
    }

    @TaskAction
    public void test() {
        if (testSources == null || testSources.isEmpty()) {
            getLogger().lifecycle("No Sprig tests found in {}", testDirectory);
            return;
        }
        List<String> arguments = new ArrayList<>(List.of("test", testDirectory.getAbsolutePath(),
                "--offline", "--json"));
        String classpath = getSprigClasspathArgument();
        if (!classpath.isBlank()) {
            arguments.add("--classpath");
            arguments.add(classpath);
        }
        executeSprig(arguments);
    }
}
