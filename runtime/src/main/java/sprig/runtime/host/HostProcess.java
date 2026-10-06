package sprig.runtime.host;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import sprig.runtime.SprigError;

/**
 * Child processes for {@code @std/process.spr}: an argv vector run directly,
 * with no shell, UTF-8 output captured to files and a fixed timeout. A
 * nonzero exit status is an ordinary result; only a host failure is an error.
 */
public final class HostProcess {
    private static final long PROCESS_TIMEOUT_SECONDS = 30;

    private HostProcess() {}

    public static final class ProcessResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        ProcessResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

    /** A narrow Java adapter: Sprig copies its typed List[String] into this argv. */
    public static final class Command {
        private final List<String> argv = new ArrayList<>();

        public void add(String argument) {
            if (argument == null) throw new SprigError("run command contains null");
            argv.add(argument);
        }

        public ProcessResult run() { return runProcess(argv); }
    }

    public static Command command() { return new Command(); }

    /** Executes an argv vector directly. Nonzero exits remain ordinary result values. */
    public static ProcessResult runProcess(List<String> command) {
        if (command.isEmpty())
            throw new SprigError("run requires a nonempty command");
        if (command.get(0).isBlank()) throw new SprigError("run executable is empty");
        Path stdout = null;
        Path stderr = null;
        Process process = null;
        try {
            String testTemp = System.getenv("SPRIG_TEST_TMPDIR");
            Path captureDirectory = Path.of(testTemp == null || testTemp.isBlank()
                    ? System.getProperty("java.io.tmpdir") : testTemp);
            stdout = Files.createTempFile(captureDirectory, "sprig-process-stdout-", ".txt");
            stderr = Files.createTempFile(captureDirectory, "sprig-process-stderr-", ".txt");
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectOutput(stdout.toFile());
            builder.redirectError(stderr.toFile());
            process = builder.start();
            process.getOutputStream().close();
            if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                for (ProcessHandle child : process.descendants().toList()) child.destroyForcibly();
                process.destroyForcibly();
                process.waitFor();
                throw new SprigError("run timed out after " + PROCESS_TIMEOUT_SECONDS + " seconds");
            }
            return new ProcessResult(process.exitValue(),
                    readUtf8(stdout), readUtf8(stderr));
        } catch (IOException e) {
            throw new SprigError("run failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) process.destroyForcibly();
            throw new SprigError("run was interrupted", e);
        } finally {
            if (stdout != null) try { Files.deleteIfExists(stdout); } catch (IOException ignored) { }
            if (stderr != null) try { Files.deleteIfExists(stderr); } catch (IOException ignored) { }
        }
    }

    private static String readUtf8(Path path) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(Files.readAllBytes(path))).toString();
        } catch (CharacterCodingException e) {
            throw new IOException("child output is not valid UTF-8", e);
        }
    }
}
