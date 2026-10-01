package dev.sprig.gradle;

import java.io.File;
import java.util.Map;
import javax.inject.Inject;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.process.ExecOperations;

/** Connects Sprig sources to the selected Java source set without a consumer-owned task graph. */
public final class SprigPlugin implements Plugin<Project> {
    private final ExecOperations execOperations;

    @Inject
    public SprigPlugin(ExecOperations execOperations) {
        this.execOperations = execOperations;
    }

    @Override
    public void apply(Project project) {
        SprigExtension extension = project.getExtensions().create("sprig", SprigExtension.class, project);
        project.afterEvaluate(evaluated -> configure(evaluated, extension));
    }

    private void configure(Project project, SprigExtension extension) {
        if (!project.getPluginManager().hasPlugin("java")) {
            throw new GradleException("The dev.sprig plugin requires the Gradle Java plugin (or a host plugin "
                    + "such as Fabric Loom that applies it). Apply `java` before configuring Sprig.");
        }

        File projectDirectory = extension.getProjectDirectory().getAbsoluteFile();
        File manifest = new File(projectDirectory, "sprig.toml");
        if (!manifest.isFile()) {
            throw new GradleException("Sprig project manifest not found: " + manifest
                    + ". Set `sprig.projectDirectory` to the directory containing sprig.toml.");
        }

        SprigToolchain toolchain = SprigToolchain.discover(project, extension, execOperations);
        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        SourceSetContainer sourceSets = java.getSourceSets();
        SourceSet sourceSet = sourceSets.findByName(extension.getTargetSourceSet());
        if (sourceSet == null) {
            throw new GradleException("Sprig target source set `" + extension.getTargetSourceSet()
                    + "` does not exist. Available source sets: " + sourceSets.stream()
                    .map(SourceSet::getName).sorted().toList());
        }

        TaskProvider<JavaCompile> targetCompile = project.getTasks().named(
                sourceSet.getCompileJavaTaskName(), JavaCompile.class);
        File bridgeSourcesDirectory = new File(project.getProjectDir(), "src/sprigBridge/java");
        FileCollection bridgeSources = project.fileTree(bridgeSourcesDirectory, files -> files.include("**/*.java"));
        File buildDirectory = project.getLayout().getBuildDirectory().get().getAsFile();
        File bridgeOutput = new File(buildDirectory, "classes/java/sprigBridge");
        TaskProvider<JavaCompile> bridgeCompile = project.getTasks().register(
                "compileSprigBridge", JavaCompile.class, task -> {
                    task.setDescription("Compiles Java boundary sources for Sprig");
                    task.setGroup("build");
                    task.setSource(bridgeSources);
                    task.setClasspath(sourceSet.getCompileClasspath());
                    task.getDestinationDirectory().set(bridgeOutput);
                    task.getOptions().setEncoding("UTF-8");
                    task.getOptions().getRelease().set(targetCompile.flatMap(
                            compile -> compile.getOptions().getRelease()));
                });

        // The output contributes bridge bytecode to the selected source set's runtime and jar.
        sourceSet.getOutput().dir(Map.of("builtBy", bridgeCompile), bridgeCompile.flatMap(
                JavaCompile::getDestinationDirectory));
        // Some host plugins (notably Loom's remapped mod jar) snapshot source
        // set outputs while configuring their archive task. Keep the bridge
        // as a regular source-set output and also wire it into binary jars so
        // the final host artifact cannot omit the Java boundary classes.
        project.getTasks().withType(Jar.class).configureEach(task -> {
            String classifier = task.getArchiveClassifier().getOrNull();
            if (classifier == null || (!classifier.contains("sources") && !classifier.contains("javadoc"))) {
                task.from(bridgeCompile.flatMap(JavaCompile::getDestinationDirectory));
                task.dependsOn(bridgeCompile);
            }
        });

        FileCollection sprigClasspath = project.files(sourceSet.getCompileClasspath(),
                bridgeCompile.flatMap(JavaCompile::getDestinationDirectory));
        FileCollection projectInputs = project.files(
                project.fileTree(projectDirectory, files -> {
                    files.include("**/*.spr");
                    files.exclude("build/**", ".gradle/**", ".git/**");
                }), manifest, new File(projectDirectory, "sprig.lock"), bridgeSources);

        TaskProvider<SprigCheckTask> check = project.getTasks().register("sprigCheck", SprigCheckTask.class,
                task -> configureTask(task, projectDirectory, projectInputs, sprigClasspath, toolchain,
                        sourceSet.getName()));
        check.configure(task -> task.dependsOn(bridgeCompile));

        File generatedRoot = new File(buildDirectory, "generated/sprig/" + sourceSet.getName());
        TaskProvider<SprigGenerateTask> generate = project.getTasks().register(
                "sprigGenerate", SprigGenerateTask.class, task -> {
                    configureTask(task, projectDirectory, projectInputs, sprigClasspath, toolchain,
                            sourceSet.getName());
                    task.getOutputDirectory().set(generatedRoot);
                    task.dependsOn(bridgeCompile);
                });

        // The task-backed provider propagates the generation dependency into compileJava.
        sourceSet.getJava().srcDir(generate.flatMap(task -> task.getOutputDirectory().dir("java")));
        sourceSet.getJava().srcDir(toolchain.getRuntimeSource());

        // javac must see the conventionally compiled bridge classes while compiling generated sources.
        targetCompile.configure(task -> {
            task.setClasspath(project.files(task.getClasspath(), bridgeCompile.flatMap(
                    JavaCompile::getDestinationDirectory)));
            task.dependsOn(bridgeCompile, generate);
        });

        TaskProvider<SprigTestTask> test = project.getTasks().register("sprigTest", SprigTestTask.class,
                task -> {
                    configureTask(task, projectDirectory, projectInputs, sprigClasspath, toolchain,
                            sourceSet.getName());
                    File testDirectory = extension.getTestDirectory().getAbsoluteFile();
                    task.setTestDirectory(testDirectory);
                    task.setTestSources(project.fileTree(testDirectory, files -> files.include("**/*.spr")));
                    task.dependsOn(bridgeCompile);
                });

        project.getTasks().register("sprigResolve", SprigResolveTask.class, task ->
                configureTask(task, projectDirectory, projectInputs, sprigClasspath, toolchain,
                        sourceSet.getName()));

        project.getTasks().register("sprigInfo", SprigInfoTask.class, task -> {
            configureTask(task, projectDirectory, projectInputs, sprigClasspath, toolchain,
                    sourceSet.getName());
            task.setRuntimeSource(toolchain.getRuntimeSource());
            task.setBridgeSources(bridgeSourcesDirectory);
            task.setGeneratedJava(new File(generatedRoot, "java"));
            task.setTestDirectory(extension.getTestDirectory().getAbsoluteFile());
            task.setCompilerHome(toolchain.getCompilerHome());
        });

        project.getTasks().named("check").configure(task -> task.dependsOn(check, test));
    }

    private static void configureTask(SprigTask task, File projectDirectory, FileCollection projectInputs,
                                      FileCollection sprigClasspath, SprigToolchain toolchain,
                                      String sourceSetName) {
        task.setExecutableFile(toolchain.getExecutable());
        task.setProjectDirectory(projectDirectory);
        task.setProjectInputs(projectInputs);
        task.setSprigClasspath(sprigClasspath);
        task.setCompilerClasspath(task.getProject().files(toolchain.getCompilerClasspath()));
        task.setCompilerVersion(toolchain.getCompilerVersion());
        task.setSourceSetName(sourceSetName);
    }
}
