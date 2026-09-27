package sprig.runtime.web;

import java.util.LinkedHashMap;
import java.util.Map;

/** Wire response only: status, UTF-8 body and headers. */
public final class HostResponse {
    final int status;
    final String body;
    final Map<String, String> headers = new LinkedHashMap<>();
    public HostResponse(long status, String body, String contentType) {
        if (status < 200 || status > 599) throw new IllegalArgumentException("HTTP status must be 200..599");
        this.status = (int) status; this.body = java.util.Objects.requireNonNull(body);
        header("Content-Type", contentType);
    }
    public void header(String name, String value) {
        if (!name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+") || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0)
            throw new IllegalArgumentException("invalid response header");
        // Framing is owned by the HTTP transport, never the application.
        if (name.equalsIgnoreCase("Content-Length") || name.equalsIgnoreCase("Transfer-Encoding"))
            throw new IllegalArgumentException("transport owns HTTP framing");
        headers.put(name, value);
    }
}
