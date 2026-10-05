package sprig.compiler.jvm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Runs a compiled Sprig program in a child JVM, capturing output. */
public final class JavaRunner {
    public static final class Result {
        public int exitCode;
        public String stdout = "";
        public String stderr = "";
        public boolean timedOut;
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
        return run(classesDir, mainClass, args, workDir, streamOutput, stacktrace,
                Map.of(), 0, null);
    }

    /** Runs one test in a fresh JVM with a bounded lifetime and explicit environment. */
    public static Result run(Path classesDir, String mainClass, List<String> args, Path workDir,
                             boolean streamOutput, boolean stacktrace,
                             Map<String, String> environment, long timeoutMillis, Path processDirectory)
            throws IOException, InterruptedException {
        Path outFile = workDir.resolve("program.out");
        Path errFile = workDir.resolve("program.err");
        List<String> command = new ArrayList<>();
        command.add(javaBinary());
        command.add("-Dfile.encoding=UTF-8");
        // Captured streams are decoded as UTF-8 below; JDK 19+ would otherwise
        // encode them with the platform code page (JDK 17 ignores these names).
        command.add("-Dstderr.encoding=UTF-8");
        if (!streamOutput || StandardStreams.utf8Stdout()) command.add("-Dstdout.encoding=UTF-8");
        command.add("-cp");
        command.add(classesDir + (JvmClasspath.entries().isEmpty() ? ""
                : java.io.File.pathSeparator + JvmClasspath.forProcess()));
        command.add(mainClass);
        command.addAll(args);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().putAll(environment);
        if (processDirectory != null) builder.directory(processDirectory.toFile());
        if (stacktrace) {
            builder.environment().put("SPRIG_STACKTRACE", "1");
        }
        if (streamOutput) {
            builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            builder.redirectInput(ProcessBuilder.Redirect.INHERIT);
        } else builder.redirectOutput(outFile.toFile());
        builder.redirectError(errFile.toFile());
        Process process = builder.start();
        if (!streamOutput) process.getOutputStream().close();
        Result result = new Result();
        Thread cleanup = new Thread(process::destroy, "sprig-child-cleanup");
        Runtime.getRuntime().addShutdownHook(cleanup);
        try {
            if (timeoutMillis <= 0) result.exitCode = process.waitFor();
            else if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                for (ProcessHandle child : process.descendants().toList()) child.destroyForcibly();
                process.destroyForcibly();
                process.waitFor();
                result.timedOut = true;
                result.exitCode = 124;
            } else result.exitCode = process.exitValue();
        }
        finally {
            process.destroy();
            Runtime.getRuntime().removeShutdownHook(cleanup);
        }
        // A program (or a Java library it calls) may write bytes that are not
        // UTF-8; report them as replacement characters instead of failing.
        if (!streamOutput && Files.exists(outFile)) {
            result.stdout = new String(Files.readAllBytes(outFile), StandardCharsets.UTF_8);
        }
        if (Files.exists(errFile)) {
            result.stderr = new String(Files.readAllBytes(errFile), StandardCharsets.UTF_8);
        }
        return result;
    }

    public static String javaBinary() {
        String executable = System.getProperty("os.name").toLowerCase().contains("win")
                ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }
}
