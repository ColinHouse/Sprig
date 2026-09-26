package sprig.runtime.host;

import java.time.Instant;

/** Process/time boundary. The generated JVM entry point installs a copied argument vector. */
public final class HostSystem {
    private static volatile String[] arguments = new String[0];
    private HostSystem() {}
    public static void setArguments(String[] values) { arguments = values.clone(); }
    public static long argumentCount() { return arguments.length; }
    public static String argument(long index) { return arguments[Math.toIntExact(index)]; }
    public static String environment(String name) { return System.getenv(name); }
    public static long epochMillis() { return System.currentTimeMillis(); }
    public static String utcNow() { return Instant.now().toString(); }
}
