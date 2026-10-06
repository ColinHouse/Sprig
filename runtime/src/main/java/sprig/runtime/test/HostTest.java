package sprig.runtime.test;

import java.nio.file.Files;
import java.nio.file.Path;
import sprig.runtime.SprigError;
import sprig.runtime.host.HostProcess;

/** Small host boundary for project test programs; no test discovery lives here. */
public final class HostTest {
    private HostTest() {}

    public static String tempDir() {
        String path = System.getenv("SPRIG_TEST_TMPDIR");
        if (path == null || path.isBlank() || !Files.isDirectory(Path.of(path)))
            throw new SprigError("temp_dir() is only available inside sprig test");
        return path;
    }

    /** Kept for programs that bound the earlier test-owned process helper; child processes live in HostProcess. */
    public static HostProcess.Command command() { return HostProcess.command(); }
}
