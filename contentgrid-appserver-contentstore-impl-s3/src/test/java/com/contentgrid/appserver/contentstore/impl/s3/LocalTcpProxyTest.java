package com.contentgrid.appserver.contentstore.impl.s3;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Fixture-level tests for {@link LocalTcpProxy}: every byte the proxy accepts must reach the
 * upstream endpoint exactly once (regression test for a double-send bug where the captured head
 * was both written directly and replayed through the piping stream).
 */
@Timeout(value = 60)
class LocalTcpProxyTest {

    private static final class Upstream {
        final ServerSocket listener = new ServerSocket();
        final CompletableFuture<byte[]> received = new CompletableFuture<>();

        Upstream() throws Exception {
            listener.setReuseAddress(true);
            listener.bind(new InetSocketAddress("127.0.0.1", 0));
            var accept = new Thread(() -> {
                try (var socket = listener.accept()) {
                    var out = new ByteArrayOutputStream();
                    var buffer = new byte[4096];
                    int read;
                    while ((read = socket.getInputStream().read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    received.complete(out.toByteArray());
                } catch (Exception e) {
                    received.completeExceptionally(e);
                }
            });
            accept.setDaemon(true);
            accept.start();
        }

        int port() {
            return listener.getLocalPort();
        }

        void close() throws Exception {
            listener.close();
        }
    }

    @Test
    void forward_deliversEachByteExactlyOnce() throws Exception {
        var upstream = new Upstream();
        LocalTcpProxy proxy = new LocalTcpProxy(LocalTcpProxy.freePort(), "127.0.0.1", upstream.port(),
                LocalTcpProxy.Mode.FORWARD, 0);
        proxy.start();
        try {
            var payload = new byte[20 * 1024];
            Arrays.fill(payload, (byte) 'q');
            var head = "GET /bucket/key?partNumber=1 HTTP/1.1\r\nHost: x\r\n\r\n"
                    .getBytes(StandardCharsets.US_ASCII);
            var sent = new byte[head.length + payload.length];
            System.arraycopy(head, 0, sent, 0, head.length);
            System.arraycopy(payload, 0, sent, head.length, payload.length);

            try (var client = new Socket("127.0.0.1", proxy.port())) {
                client.getOutputStream().write(sent);
                client.getOutputStream().flush();
                client.shutdownOutput();
                // keep the client read side open until upstream saw EOF so pumps can finish
                Awaitility.await().atMost(Duration.ofSeconds(10))
                        .until(upstream.received::isDone);
            }
            assertArrayEquals(sent, upstream.received.get(10, TimeUnit.SECONDS));
            assertTrue(proxy.requestHeads().stream().anyMatch(h -> h.contains("partNumber=1")),
                    "request head not captured: " + proxy.requestHeads());
        } finally {
            proxy.close();
            upstream.close();
        }
    }

    @Test
    void blackhole_recordsHeadAndObservesPeerClose() throws Exception {
        var upstream = new Upstream();
        LocalTcpProxy proxy = new LocalTcpProxy(LocalTcpProxy.freePort(), "127.0.0.1", upstream.port(),
                LocalTcpProxy.Mode.BLACKHOLE, 0);
        proxy.start();
        try {
            try (var client = new Socket("127.0.0.1", proxy.port())) {
                client.getOutputStream().write("GET /bucket/key HTTP/1.1\r\nHost: x\r\n\r\n"
                        .getBytes(StandardCharsets.US_ASCII));
                client.getOutputStream().flush();
                assertTrue(proxy.awaitAccepted(10, TimeUnit.SECONDS), "proxy never accepted");
            }
            assertTrue(proxy.awaitPeerClose(10, TimeUnit.SECONDS), "peer close not observed");
            assertTrue(proxy.requestHeads().stream().anyMatch(h -> h.startsWith("GET /bucket/key ")),
                    "request head not captured: " + proxy.requestHeads());
            assertEquals(1, proxy.acceptedCount());
        } finally {
            proxy.close();
            upstream.close();
        }
    }
}
