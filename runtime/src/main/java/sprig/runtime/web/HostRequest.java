package sprig.runtime.web;

import com.sun.net.httpserver.Headers;

/** Immutable request transport snapshot. URI interpretation stays in the library. */
public final class HostRequest {
    private final String method, path, query, body;
    private final Headers headers;
    public HostRequest(String method, String path, String query, String body, Headers headers) {
        this.method = method; this.path = path; this.query = query; this.body = body; this.headers = headers;
    }
    public String method() { return method; }
    public String rawPath() { return path; }
    public String rawQuery() { return query; }
    public String body() { return body; }
    public String header(String name) { return headers.getFirst(name); }
}
