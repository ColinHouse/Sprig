package sprig.runtime.http;

import java.util.List;

/** Indexed, immutable transport result exposed to the Sprig façade. */
public final class HttpResponseData {
    private final int status;
    private final String body;
    private final List<String> headerPairs;

    HttpResponseData(int status, String body, List<String> headerPairs) {
        this.status = status;
        this.body = body;
        this.headerPairs = List.copyOf(headerPairs);
    }

    public int status() { return status; }
    public String body() { return body; }
    public long headerCount() { return headerPairs.size() / 2L; }
    public String headerName(long index) { return headerPairs.get(Math.toIntExact(index * 2)); }
    public String headerValue(long index) { return headerPairs.get(Math.toIntExact(index * 2 + 1)); }
}
