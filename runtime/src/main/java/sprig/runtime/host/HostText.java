package sprig.runtime.host;

/** Explicit UTF-16/JSON escape boundary; JSON structure and parsing stay in Sprig. */
public final class HostText {
    private HostText() {}
    public static String unicodeUnit(String hex) { return String.valueOf((char) Integer.parseInt(hex, 16)); }
    public static boolean isControl(String unit) { return unit.length() == 1 && unit.charAt(0) < 32; }
    public static String controlEscape(String unit) { return String.format(java.util.Locale.ROOT, "\\u%04x", (int) unit.charAt(0)); }
}
