package sprig.runtime.http;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import sprig.runtime.SprigError;
import sprig.runtime.SprigList;

/** JDK HTTP transport mechanics. Public application semantics live in std/http.spr. */
public final class HostHttp {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private HostHttp() {}

    /**
     * Send one blocking request. Header pairs alternate name/value strings;
     * response headers are returned as a sorted, lower-case indexed snapshot.
     */
    public static HttpResponseData send(String method, String url, SprigList<String> headerPairs,
                                        boolean hasBody, String body, long timeoutMillis) {
        if (timeoutMillis <= 0) throw new SprigError("HTTP timeout_ms must be greater than zero");
        if (headerPairs == null || headerPairs.size() % 2 != 0)
            throw new SprigError("Invalid HTTP request header list");
        URI uri = parseUri(url);
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(timeoutMillis));
        try {
            for (long i = 0; i < headerPairs.size(); i += 2) {
                request.header(headerPairs.get(i), headerPairs.get(i + 1));
            }
            HttpRequest.BodyPublisher publisher = hasBody
                    ? HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)
                    : HttpRequest.BodyPublishers.noBody();
            request.method(method, publisher);
        } catch (IllegalArgumentException | NullPointerException failure) {
            throw new SprigError("Invalid HTTP method or request header", failure);
        }

        try {
            HttpResponse<byte[]> response = CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            String responseBody = decodeUtf8(response.body());
            return new HttpResponseData(response.statusCode(), responseBody,
                    responseHeaderPairs(response.headers().map()));
        } catch (HttpTimeoutException failure) {
            throw new SprigError("HTTP request timed out", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new SprigError("HTTP request interrupted", failure);
        } catch (IOException failure) {
            throw new SprigError("HTTP transport failure (" + failure.getClass().getSimpleName() + ")", failure);
        }
    }

    private static URI parseUri(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (!uri.isAbsolute() || uri.getHost() == null || scheme == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new SprigError("HTTP URL must be an absolute http or https URI with a host");
            }
            return uri;
        } catch (URISyntaxException | IllegalArgumentException failure) {
            throw new SprigError("Invalid HTTP URI", failure);
        }
    }

    private static String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException failure) {
            throw new SprigError("HTTP response body is not valid UTF-8", failure);
        }
    }

    private static List<String> responseHeaderPairs(Map<String, List<String>> headers) {
        List<String> result = new ArrayList<>();
        headers.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey)).forEach(entry -> {
            String name = entry.getKey().toLowerCase(java.util.Locale.ROOT);
            for (String value : entry.getValue()) {
                result.add(name);
                result.add(value);
            }
        });
        return result;
    }
}
