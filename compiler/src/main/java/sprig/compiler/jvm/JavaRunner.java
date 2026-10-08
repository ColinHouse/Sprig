package sprig.compiler.jvm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Runs a compiled Sprig program in a child JVM, capturing output. */
public final class JavaRunner {
    public static final class Result {
        public int exitCode;
        public String stdout = "";
        public String stderr = "";
        public boolean timedOut;
        public boolean outputWritten;
    }

    /** Asks the program to exit, waits for it, and forces it (and its children) after 5 s. */
    private static void stop(Process process) {
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                for (ProcessHandle child : process.descendants().toList()) child.destroyForcibly();
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * The program's process as the CLI's shutdown hook sees it. The hook is
     * registered before the program starts and meets it under one lock: a
     * termination that comes first keeps the program from starting, and one that
     * comes while it starts waits for start() and then stops it. Registered after
     * start(), the hook missed a termination that came in between, and the
     * program went on running without the CLI.
     */
    private static final class Child {
        private Process process;
        private boolean terminated;

        synchronized Process start(ProcessBuilder builder) throws IOException, InterruptedException {
            if (terminated) throw new InterruptedException("sprig was terminated");
            process = builder.start();
            return process;
        }

        void terminate() {
            Process started;
            synchronized (this) {
                terminated = true;
                started = process;
            }
            if (started != null) stop(started);
        }
    }

    /**
     * Removes the hook once the program has ended. While the CLI is being
     * terminated, the JVM runs the hook instead, and that hook stops the program:
     * not an error to report.
     */
    private static void removeHook(Thread hook) {
        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException terminating) {
            // Shutdown in progress; the hook is already stopping the program.
        }
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
            builder.redirectOutput(ProcessBuilder.Redirect.PIPE);
            builder.redirectInput(ProcessBuilder.Redirect.INHERIT);
        } else builder.redirectOutput(outFile.toFile());
        builder.redirectError(errFile.toFile());
        // Terminating the CLI terminates the program and waits for it, so a port or
        // file the program holds is released by the time the CLI has exited.
        Child program = new Child();
        Thread cleanup = new Thread(program::terminate, "sprig-child-cleanup");
        try {
            Runtime.getRuntime().addShutdownHook(cleanup);
        } catch (IllegalStateException terminating) {
            throw new InterruptedException("sprig was terminated");
        }
        Process process;
        try {
            process = program.start(builder);
        } catch (IOException | InterruptedException | RuntimeException failure) {
            removeHook(cleanup);
            throw failure;
        }
        AtomicBoolean outputWritten = new AtomicBoolean();
        AtomicReference<IOException> outputFailure = new AtomicReference<>();
        Thread outputForwarder = null;
        if (streamOutput) {
            outputForwarder = new Thread(() -> {
                try (var input = process.getInputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) >= 0) {
                        if (count == 0) continue;
                        outputWritten.set(true);
                        System.out.write(buffer, 0, count);
                        System.out.flush();
                    }
                } catch (IOException exception) {
                    outputFailure.set(exception);
                }
            }, "sprig-child-stdout");
            outputForwarder.setDaemon(true);
            outputForwarder.start();
        }
        if (!streamOutput) process.getOutputStream().close();
        Result result = new Result();
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
            // destroy() closes the program's streams. Once the program has ended,
            // let the forwarder pass on all it wrote and reach the end of the
            // stream first. A grandchild that keeps the pipe open is not waited
            // for longer than two seconds.
            if (outputForwarder != null && !process.isAlive()) {
                outputForwarder.join(2000);
            }
            process.destroy();
            removeHook(cleanup);
        }
        if (outputForwarder != null) {
            outputForwarder.join();
            // A read failure on the program's stdout pipe is not the run's failure:
            // the program has ended, its exit status and stderr are the outcome, and
            // every byte read before the failure was forwarded. The JDK closes the
            // pipe under a reader when the process exits or destroy() runs, which
            // surfaced as a spurious "sprig: i/o error: Stream closed" on macOS.
            IOException failure = outputFailure.get();
            if (failure != null && System.getenv("SPRIG_STACKTRACE") != null) {
                System.err.println("sprig: note: the program's output stream failed after it ended: " + failure);
            }
        }
        result.outputWritten = outputWritten.get();
        // A program (or a Java library it calls) may write bytes that are not
        // UTF-8; report them as replacement characters instead of failing.
        if (!streamOutput && Files.exists(outFile)) {
            result.stdout = new String(Files.readAllBytes(outFile), StandardCharsets.UTF_8);
            result.outputWritten = !result.stdout.isEmpty();
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
