package fixture;
public final class Stamp {
    public static String tag(String value) { return "jar:" + value; }
    public int bump(int value) { return value + 7; }
    public static int code() { return 7; }
}
