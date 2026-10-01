package dev.sprig.gradle;

import java.util.ArrayList;
import java.util.List;
import org.gradle.api.tasks.TaskAction;
import javax.inject.Inject;

/** Runs the project's static Sprig checking against the existing lock, offline. */
public abstract class SprigCheckTask extends SprigTask {
    @Inject
    public SprigCheckTask(org.gradle.process.ExecOperations execOperations) {
        super(execOperations);
    }

    @TaskAction
    public void check() {
        List<String> arguments = new ArrayList<>(List.of("check", "--offline", "--json"));
        String classpath = getSprigClasspathArgument();
        if (!classpath.isBlank()) {
            arguments.add("--classpath");
            arguments.add(classpath);
        }
        executeSprig(arguments);
    }
}
