package sprig.runtime.web;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import sprig.runtime.Fn1;
import sprig.runtime.SprigError;

/** JDK transport/lifetime boundary. No routing, JSON or OpenAPI policy. */
public final class HostServer {
    private final HttpServer server;
    private final ExecutorService executor;
    private boolean stopped;
    public HostServer(long port, Fn1<HostRequest, HostResponse> handler) {
        this("127.0.0.1", port, handler);
    }

    /** Binds the given host name or address; "0.0.0.0" listens on every interface. */
    public HostServer(String host, long port, Fn1<HostRequest, HostResponse> handler) {
        if (port < 0 || port > 65535) throw new SprigError("port must be 0..65535");
        if (host == null || host.isBlank()) throw new SprigError("host must not be blank");
        try { server = HttpServer.create(new java.net.InetSocketAddress(host, (int) port), 0); }
        catch (IOException | IllegalArgumentException error) { throw new SprigError("cannot bind HTTP server to " + host + ":" + port, error); }
        // One request at a time keeps the first synchronous library's mutable routes/database state explicit.
        executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.createContext("/", exchange -> handle(exchange, handler));
        server.start();
    }
    private static void handle(HttpExchange exchange, Fn1<HostRequest, HostResponse> handler) throws IOException {
        try {
            HostResponse response;
            try {
                String body = HostCodec.utf8(exchange.getRequestBody().readAllBytes());
                response = handler.apply(new HostRequest(exchange.getRequestMethod(), exchange.getRequestURI().getRawPath(),
                        exchange.getRequestURI().getRawQuery() == null ? "" : exchange.getRequestURI().getRawQuery(), body, exchange.getRequestHeaders()));
                if (response == null) throw new IllegalStateException("null HTTP response");
            } catch (java.nio.charset.CharacterCodingException | BadRequest error) {
                response = new HostResponse(400, "Bad request", "text/plain; charset=utf-8");
            } catch (RuntimeException error) {
                response = new HostResponse(500, "Internal server error", "text/plain; charset=utf-8");
            }
            for (var header : response.headers.entrySet()) exchange.getResponseHeaders().set(header.getKey(), header.getValue());
            byte[] bytes = response.body.getBytes(StandardCharsets.UTF_8);
            if (response.status == 204 || response.status == 304 || exchange.getRequestMethod().equals("HEAD")) {
                exchange.sendResponseHeaders(response.status, -1);
            } else {
                exchange.sendResponseHeaders(response.status, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        } finally { exchange.close(); }
    }
    public long port() { return server.getAddress().getPort(); }
    public synchronized void stop() {
        if (!stopped) { stopped = true; server.stop(0); executor.shutdownNow(); }
    }
}
