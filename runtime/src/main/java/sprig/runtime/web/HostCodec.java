package sprig.runtime.web;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;

/** Percent/UTF-8 mechanics, without query or route policy. */
public final class HostCodec {
    private HostCodec() {}
    public static String decode(String input, boolean plusAsSpace) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int i = 0; i < input.length();) {
            char ch = input.charAt(i);
            if (ch == '%') {
                if (i + 2 >= input.length()) throw new BadRequest("incomplete URI escape");
                int hi = Character.digit(input.charAt(i + 1), 16), lo = Character.digit(input.charAt(i + 2), 16);
                if (hi < 0 || lo < 0) throw new BadRequest("invalid URI escape");
                bytes.write(hi * 16 + lo); i += 3;
            } else {
                int cp = input.codePointAt(i);
                bytes.writeBytes((plusAsSpace && ch == '+' ? " " : new String(Character.toChars(cp))).getBytes(StandardCharsets.UTF_8));
                i += Character.charCount(cp);
            }
        }
        try { return utf8(bytes.toByteArray()); }
        catch (CharacterCodingException error) { throw new BadRequest("invalid UTF-8 URI"); }
    }
    static String utf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
}
