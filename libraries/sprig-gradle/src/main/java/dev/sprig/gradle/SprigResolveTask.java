package dev.sprig.gradle;

import java.util.ArrayList;
import java.util.List;
import org.gradle.api.tasks.TaskAction;
import javax.inject.Inject;

/** Explicit lock update task. It is deliberately not attached to build/check. */
public abstract class SprigResolveTask extends SprigTask {
    @Inject
    public SprigResolveTask(org.gradle.process.ExecOperations execOperations) {
        super(execOperations);
    }

    @TaskAction
    public void resolve() {
        List<String> arguments = new ArrayList<>(List.of("resolve", "--json"));
        if (getProject().getGradle().getStartParameter().isOffline()) {
            arguments.add("--offline");
        }
        executeSprig(arguments);
    }
}
