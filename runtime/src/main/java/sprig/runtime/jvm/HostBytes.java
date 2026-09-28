package sprig.runtime.jvm;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import sprig.runtime.SprigError;

/**
 * Explicit helpers for opaque {@code byte[]} JVM values. A byte array stays a
 * foreign JVM value: there is no Sprig array syntax, no indexing and no
 * implicit String conversion. UTF-8 decoding is strict and never replaces
 * malformed input.
 */
public final class HostBytes {
    private HostBytes() {}

    public static byte[] utf8(String value) {
        requireString(value);
        return value.getBytes(StandardCharsets.UTF_8);
    }

    public static String utf8String(byte[] value) {
        requireBytes(value);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value))
                    .toString();
        } catch (CharacterCodingException error) {
            throw new SprigError("byte array is not valid UTF-8", error);
        }
    }

    public static long length(byte[] value) {
        requireBytes(value);
        return value.length;
    }

    /** Deterministic lowercase hex, two characters per byte. */
    public static String hex(byte[] value) {
        requireBytes(value);
        char[] digits = "0123456789abcdef".toCharArray();
        StringBuilder out = new StringBuilder(value.length * 2);
        for (byte item : value) {
            out.append(digits[(item >> 4) & 0xF]).append(digits[item & 0xF]);
        }
        return out.toString();
    }

    private static void requireBytes(byte[] value) {
        if (value == null) throw new SprigError("byte array is null");
    }

    private static void requireString(String value) {
        if (value == null) throw new SprigError("string is null");
    }
}
