package sprig.compiler.jvm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Runs a compiled Sprig program in a child JVM, capturing output. */
public final class JavaRunner {
    public static final class Result {
        public int exitCode;
        public String stdout = "";
        public String stderr = "";
    }

    private JavaRunner() {
    }

    public static Result run(Path classesDir, String mainClass, List<String> args, Path workDir)
            throws IOException, InterruptedException {
        return run(classesDir, mainClass, args, workDir, false);
    }

    /** Text CLI output streams immediately; JSON mode retains a complete captured envelope. */
    public static Result run(Path classesDir, String mainClass, List<String> args, Path workDir,
                             boolean streamOutput) throws IOException, InterruptedException {
        return run(classesDir, mainClass, args, workDir, streamOutput, false);
    }

    /** {@code stacktrace} exposes the raw JVM stack of an uncaught program failure. */
    public static Result run(Path classesDir, String mainClass, List<String> args, Path workDir,
                             boolean streamOutput, boolean stacktrace)
            throws IOException, InterruptedException {
        Path outFile = workDir.resolve("program.out");
        Path errFile = workDir.resolve("program.err");
        List<String> command = new ArrayList<>();
        command.add(javaBinary());
        command.add("-Dfile.encoding=UTF-8");
        command.add("-cp");
        command.add(classesDir + (JvmClasspath.entries().isEmpty() ? ""
                : java.io.File.pathSeparator + JvmClasspath.forProcess()));
        command.add(mainClass);
        command.addAll(args);
        ProcessBuilder builder = new ProcessBuilder(command);
        if (stacktrace) {
            builder.environment().put("SPRIG_STACKTRACE", "1");
        }
        if (streamOutput) {
            builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            builder.redirectInput(ProcessBuilder.Redirect.INHERIT);
        } else builder.redirectOutput(outFile.toFile());
        builder.redirectError(errFile.toFile());
        Process process = builder.start();
        Result result = new Result();
        Thread cleanup = new Thread(process::destroy, "sprig-child-cleanup");
        Runtime.getRuntime().addShutdownHook(cleanup);
        try { result.exitCode = process.waitFor(); }
        finally {
            process.destroy();
            Runtime.getRuntime().removeShutdownHook(cleanup);
        }
        if (!streamOutput && Files.exists(outFile)) {
            result.stdout = Files.readString(outFile, StandardCharsets.UTF_8);
        }
        if (Files.exists(errFile)) {
            result.stderr = Files.readString(errFile, StandardCharsets.UTF_8);
        }
        return result;
    }

    public static String javaBinary() {
        String executable = System.getProperty("os.name").toLowerCase().contains("win")
                ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }
}
