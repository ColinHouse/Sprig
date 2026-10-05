package sprig.compiler.lsp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** The LSP base protocol: {@code Content-Length} framed UTF-8 JSON messages. */
final class Transport {
    private final InputStream in;
    private final OutputStream out;

    Transport(InputStream in, OutputStream out) {
        this.in = in;
        this.out = out;
    }

    /** Reads the next message body, or returns null at the end of the input. */
    String read() throws IOException {
        int length = -1;
        while (true) {
            String line = headerLine();
            if (line == null) {
                return null;
            }
            if (line.isEmpty()) {
                if (length >= 0) {
                    break;
                }
                continue;
            }
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase("Content-Length")) {
                try {
                    length = Integer.parseInt(line.substring(colon + 1).trim());
                } catch (NumberFormatException e) {
                    throw new IOException("Invalid Content-Length header: " + line);
                }
            }
        }
        byte[] body = in.readNBytes(length);
        if (body.length < length) {
            return null;
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    synchronized void write(String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        out.write(("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    private String headerLine() throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (true) {
            int b = in.read();
            if (b < 0) {
                return line.size() == 0 ? null : line.toString(StandardCharsets.US_ASCII);
            }
            if (b == '\n') {
                String text = line.toString(StandardCharsets.US_ASCII);
                return text.endsWith("\r") ? text.substring(0, text.length() - 1) : text;
            }
            line.write(b);
        }
    }
}
