package com.contentgrid.appserver.autoconfigure.s3.testing;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal in-memory S3 stand-in over plain HTTP for tests.
 * <p>
 * Supports small single-request {@code PUT}/{@code GET}/{@code DELETE} object operations with optional
 * {@code Range} handling. Every request is recorded so tests can assert wire-level details such as the
 * absence of {@code partNumber} query parameters. Anything resembling multipart traffic (query-bearing
 * writes, {@code partNumber} reads, {@code POST}) is rejected loudly instead of silently accepted.
 */
public final class S3LoopbackServer implements AutoCloseable {

    /** Single recorded HTTP request. */
    public record RecordedRequest(String method, String path, String query, String rangeHeader) {
        public boolean hasPartNumber() {
            return query != null && query.contains("partNumber");
        }
    }

    private final HttpServer server;
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private final List<RecordedRequest> requests = new ArrayList<>();

    private S3LoopbackServer(HttpServer server) {
        this.server = server;
        server.createContext("/", this::handle);
    }

    public static S3LoopbackServer start() throws IOException {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var loopback = new S3LoopbackServer(server);
        server.start();
        return loopback;
    }

    public String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public synchronized List<RecordedRequest> requests() {
        return List.copyOf(requests);
    }

    public synchronized int requestCount() {
        return requests.size();
    }

    public synchronized List<RecordedRequest> requestsFor(String method) {
        return requests.stream().filter(request -> request.method().equals(method)).toList();
    }

    public void seed(String bucket, String key, byte[] content) {
        objects.put("/" + bucket + "/" + key, content.clone());
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private synchronized void record(HttpExchange exchange) {
        requests.add(new RecordedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                exchange.getRequestURI().getRawQuery(), exchange.getRequestHeaders().getFirst("Range")));
    }

    private void handle(HttpExchange exchange) throws IOException {
        record(exchange);
        try {
            switch (exchange.getRequestMethod()) {
                case "PUT" -> handlePut(exchange);
                case "GET" -> handleGet(exchange);
                case "DELETE" -> handleDelete(exchange);
                default -> send(exchange, 405, new byte[0]);
            }
        } finally {
            exchange.close();
        }
    }

    private void handlePut(HttpExchange exchange) throws IOException {
        if (exchange.getRequestURI().getRawQuery() != null) {
            send(exchange, 400, "multipart-style writes are not supported".getBytes(StandardCharsets.UTF_8));
            return;
        }
        objects.put(exchange.getRequestURI().getPath(), exchange.getRequestBody().readAllBytes());
        exchange.getResponseHeaders().set("ETag", "\"loopback-etag\"");
        send(exchange, 200, new byte[0]);
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        if (exchange.getRequestURI().getRawQuery() != null) {
            send(exchange, 400, "part downloads are not supported".getBytes(StandardCharsets.UTF_8));
            return;
        }
        var content = objects.get(exchange.getRequestURI().getPath());
        if (content == null) {
            send(exchange, 404, """
                    <Error><Code>NoSuchKey</Code><Message>The specified key does not exist.</Message></Error>"""
                    .getBytes(StandardCharsets.UTF_8));
            return;
        }
        var rangeHeader = exchange.getRequestHeaders().getFirst("Range");
        exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
        exchange.getResponseHeaders().set("ETag", "\"loopback-etag\"");
        if (rangeHeader == null) {
            send(exchange, 200, content);
            return;
        }
        var bounds = rangeHeader.substring("bytes=".length()).split("-", 2);
        int start = Integer.parseInt(bounds[0]);
        int end = Integer.parseInt(bounds[1]);
        var slice = java.util.Arrays.copyOfRange(content, start, end + 1);
        exchange.getResponseHeaders()
                .set("Content-Range", "bytes %d-%d/%d".formatted(start, end, content.length));
        send(exchange, 206, slice);
    }

    private void handleDelete(HttpExchange exchange) throws IOException {
        objects.remove(exchange.getRequestURI().getPath());
        send(exchange, 204, new byte[0]);
    }

    private static void send(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }
}
