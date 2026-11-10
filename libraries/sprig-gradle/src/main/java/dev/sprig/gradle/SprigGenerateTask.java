package dev.sprig.gradle;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.model.ObjectFactory;
import javax.inject.Inject;

/**
 * Emits checked Java into an owned build directory; the host javac compiles it.
 *
 * <p>The output is the project's generated classes and its own copy of the
 * Sprig runtime, both in the project's package ({@code sprig.user.<project>}
 * and {@code sprig.user.<project>.runtime}). The jar the host builds from them
 * shares no class name with another Sprig-built jar, so two of them work in
 * one class loader, as two mods in one game must.
 */
public abstract class SprigGenerateTask extends SprigTask {
    private final DirectoryProperty outputDirectory;
    private File runtimeSource;

    @Inject
    public SprigGenerateTask(org.gradle.process.ExecOperations execOperations, ObjectFactory objects) {
        super(execOperations);
        outputDirectory = objects.directoryProperty();
    }

    @OutputDirectory
    public DirectoryProperty getOutputDirectory() {
        return outputDirectory;
    }

    /** The runtime sources the compiler copies into the output; a changed runtime regenerates. */
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public File getRuntimeSource() {
        return runtimeSource;
    }

    public void setRuntimeSource(File runtimeSource) {
        this.runtimeSource = runtimeSource;
    }

    @TaskAction
    public void generate() {
        File output = outputDirectory.get().getAsFile();
        getProject().delete(output);
        output.mkdirs();
        List<String> arguments = new ArrayList<>(List.of("build", "--emit-java-only", "--with-runtime",
                "--offline", "--json", "-d", output.getAbsolutePath()));
        addClasspathArguments(arguments);
        executeSprig(arguments);
    }
}
