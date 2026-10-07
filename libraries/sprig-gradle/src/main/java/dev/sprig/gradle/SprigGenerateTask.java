package dev.sprig.gradle;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.model.ObjectFactory;
import javax.inject.Inject;

/** Emits checked Java into an owned build directory; the host javac compiles it. */
public abstract class SprigGenerateTask extends SprigTask {
    private final DirectoryProperty outputDirectory;

    @Inject
    public SprigGenerateTask(org.gradle.process.ExecOperations execOperations, ObjectFactory objects) {
        super(execOperations);
        outputDirectory = objects.directoryProperty();
    }

    @OutputDirectory
    public DirectoryProperty getOutputDirectory() {
        return outputDirectory;
    }

    @TaskAction
    public void generate() {
        File output = outputDirectory.get().getAsFile();
        getProject().delete(output);
        output.mkdirs();
        List<String> arguments = new ArrayList<>(List.of("build", "--emit-java-only", "--offline", "--json",
                "-d", output.getAbsolutePath()));
        addClasspathArguments(arguments);
        executeSprig(arguments);
    }
}
