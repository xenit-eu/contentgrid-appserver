package com.contentgrid.appserver.contentstore.impl.s3;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal loopback TCP fixture for the S3 read-retry integration tests: plain JDK sockets, no extra
 * framework. Forwards to (or blackholes instead of) a target endpoint so the real Netty transport
 * produces genuine connection failures — refused while nothing listens, a hung request in blackhole
 * mode, or a throttled stream in forward mode with a per-chunk delay.
 * <p>
 * The proxy only ever binds loopback addresses and forwards to a test-given target; it never
 * touches host networking configuration and never contacts anything outside the test fixtures.
 */
final class LocalTcpProxy implements Closeable {

    enum Mode {
        /**
         * Accept connections but never respond; the peer observes closure when we close.
         * Switching to this mode also stalls connections that are already forwarding: no further response
         * bytes reach the peer, and request bytes are discarded instead of forwarded.
         */
        BLACKHOLE,
        /** Pipe connections through to the target, optionally throttling the response direction. */
        FORWARD
    }

    private static final int CHUNK_BYTES = 8 * 1024;

    private static final int HEAD_CAPTURE_BYTES = 512;

    private final int requestedPort;

    private final String targetHost;

    private final int targetPort;

    private volatile Mode mode;

    private volatile long chunkDelayMillis;

    private ServerSocket listener;

    private Thread acceptThread;

    private final List<Socket> openSockets = new CopyOnWriteArrayList<>();

    private final AtomicInteger acceptedConnections = new AtomicInteger();

    private final CountDownLatch firstAccepted = new CountDownLatch(1);

    private final CountDownLatch peerCloseObserved = new CountDownLatch(1);

    private final AtomicInteger peerCloses = new AtomicInteger();

    private final Object peerCloseMonitor = new Object();

    private final List<String> requestHeads = new CopyOnWriteArrayList<>();

    private final List<String> responseHeads = new CopyOnWriteArrayList<>();

    LocalTcpProxy(int requestedPort, String targetHost, int targetPort, Mode mode, long chunkDelayMillis) {
        this.requestedPort = requestedPort;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.mode = mode;
        this.chunkDelayMillis = chunkDelayMillis;
    }

    /** Reserves a currently free loopback port (nothing is bound until {@link #start()}). */
    static int freePort() {
        try (var reservation = new ServerSocket()) {
            reservation.setReuseAddress(true);
            reservation.bind(new java.net.InetSocketAddress("127.0.0.1", 0));
            return reservation.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("no free loopback port available", e);
        }
    }

    /** Starts listening on the requested loopback port. */
    synchronized void start() throws IOException {
        if (listener != null) {
            throw new IllegalStateException("proxy already started");
        }
        listener = new ServerSocket();
        listener.setReuseAddress(true);
        listener.bind(new java.net.InetSocketAddress("127.0.0.1", requestedPort), 128);
        acceptThread = new Thread(this::acceptLoop, "local-tcp-proxy");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    int port() {
        var current = listener;
        if (current == null) {
            throw new IllegalStateException("proxy not started");
        }
        return current.getLocalPort();
    }

    void setMode(Mode mode) {
        this.mode = mode;
    }

    void setChunkDelayMillis(long chunkDelayMillis) {
        this.chunkDelayMillis = chunkDelayMillis;
    }

    int acceptedCount() {
        return acceptedConnections.get();
    }

    List<String> requestHeads() {
        return List.copyOf(requestHeads);
    }

    List<String> responseHeads() {
        return List.copyOf(responseHeads);
    }

    boolean awaitAccepted(long timeout, TimeUnit unit) throws InterruptedException {
        return firstAccepted.await(timeout, unit);
    }

    /** Whether a held connection observed peer closure (the client really closed its socket). */
    boolean awaitPeerClose(long timeout, TimeUnit unit) throws InterruptedException {
        return peerCloseObserved.await(timeout, unit);
    }

    int peerCloseCount() {
        return peerCloses.get();
    }

    /** Whether any forwarded or held connection observed a new peer closure since {@code baseline}. */
    boolean awaitPeerClosesGreaterThan(int baseline, long timeout, TimeUnit unit) throws InterruptedException {
        var deadline = System.nanoTime() + unit.toNanos(timeout);
        synchronized (peerCloseMonitor) {
            while (peerCloses.get() <= baseline) {
                var remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0) {
                    return false;
                }
                TimeUnit.NANOSECONDS.timedWait(peerCloseMonitor, remainingNanos);
            }
        }
        return true;
    }

    private void notePeerClose() {
        synchronized (peerCloseMonitor) {
            peerCloses.incrementAndGet();
            peerCloseMonitor.notifyAll();
        }
        peerCloseObserved.countDown();
    }

    private void acceptLoop() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                var socket = listener.accept();
                track(socket);
                acceptedConnections.incrementAndGet();
                firstAccepted.countDown();
                var handler = new Thread(() -> handle(socket), "local-tcp-proxy-conn");
                handler.setDaemon(true);
                handler.start();
            }
        } catch (IOException expectedOnClose) {
            // listener closed: normal shutdown
        }
    }

    private void handle(Socket peer) {
        try {
            if (mode == Mode.BLACKHOLE) {
                hold(peer);
            } else {
                forward(peer);
            }
        } catch (IOException | RuntimeException ignored) {
            // test fixture: best effort per connection
        }
    }

    /** Reads and discards until the peer closes; records the request head and the closure. */
    private void hold(Socket peer) throws IOException {
        try (peer) {
            var input = peer.getInputStream();
            var head = new byte[CHUNK_BYTES];
            var first = input.read(head);
            if (first == -1) {
                return;
            }
            requestHeads.add(new String(head, 0, Math.min(first, HEAD_CAPTURE_BYTES),
                    StandardCharsets.US_ASCII).split("\r\n\r\n", 2)[0]);
            var buffer = new byte[CHUNK_BYTES];
            while (input.read(buffer) != -1) {
                // discard: never respond, the request hangs
            }
            notePeerClose();
        }
    }

    private void forward(Socket peer) throws IOException {
        // the accepted peer socket is fixture-owned from here on: close it on every path below
        Socket target;
        try {
            target = new Socket(targetHost, targetPort);
        } catch (IOException | RuntimeException e) {
            closeQuietly(peer);
            throw e;
        }
        track(target);
        var head = new byte[CHUNK_BYTES];
        var first = peer.getInputStream().read(head);
        if (first == -1) {
            closeQuietly(peer, target);
            return;
        }
        // re-inject the consumed bytes: capture the request head, then replay it exactly once through
        // the pipe below (never write it directly as well: that would duplicate the request upstream)
        InputStream peerIn = new java.io.SequenceInputStream(
                new java.io.ByteArrayInputStream(head, 0, first), peer.getInputStream());
        requestHeads.add(new String(head, 0, Math.min(first, HEAD_CAPTURE_BYTES), StandardCharsets.US_ASCII)
                .split("\r\n\r\n", 2)[0]);
        var targetOut = target.getOutputStream();
        // when either direction ends, both sockets close; a client-side EOF proves the peer went away
        // once blackholed, the request direction keeps draining (so a client-side EOF is still observed),
        // while the response direction stops forwarding and holds the connection open until it is closed
        pipe(peerIn, targetOut, () -> 0, null, () -> {
            notePeerClose();
            closeQuietly(peer, target);
        }, chunk -> { });
        pipe(target.getInputStream(), peer.getOutputStream(), () -> chunkDelayMillis, peer,
                () -> closeQuietly(peer, target), chunk -> responseHeads.add(
                        new String(chunk, 0, Math.min(chunk.length, HEAD_CAPTURE_BYTES),
                                StandardCharsets.US_ASCII).split("\r\n\r\n", 2)[0]));
        // pipes run on daemon threads; the sockets close when either direction ends
    }

    /**
     * Pumps {@code input} to {@code output} on a daemon thread. While the proxy is blackholed, read data is
     * discarded; when {@code stallUntilClosed} is given, the pump instead stops reading and waits for that
     * socket to close.
     */
    private void pipe(InputStream input, OutputStream output, java.util.function.LongSupplier delayMillis,
            Socket stallUntilClosed, Runnable onEnd, java.util.function.Consumer<byte[]> firstChunk) {
        var pump = new Thread(() -> {
            try {
                var buffer = new byte[CHUNK_BYTES];
                int read;
                boolean first = true;
                while ((read = input.read(buffer)) != -1) {
                    delayChunk(delayMillis.getAsLong());
                    if (mode == Mode.BLACKHOLE) {
                        if (stallUntilClosed != null) {
                            awaitClosed(stallUntilClosed);
                            return;
                        }
                        continue;
                    }
                    output.write(buffer, 0, read);
                    output.flush();
                    if (first) {
                        first = false;
                        firstChunk.accept(java.util.Arrays.copyOf(buffer, read));
                    }
                }
            } catch (IOException | InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                onEnd.run();
            }
        }, "local-tcp-proxy-pump");
        pump.setDaemon(true);
        pump.start();
    }

    /** Holds a blackholed connection open until either pump or {@link #close()} closes the socket. */
    @SuppressWarnings("java:S2925")
    private static void awaitClosed(Socket socket) throws InterruptedException {
        while (!socket.isClosed()) {
            Thread.sleep(50);
        }
    }

    /** Injects network latency: this delay is the test stimulus, not an asynchronous assertion wait. */
    @SuppressWarnings("java:S2925")
    private static void delayChunk(long delayMillis) throws InterruptedException {
        if (delayMillis > 0) {
            Thread.sleep(delayMillis);
        }
    }

    private void track(Socket socket) {
        openSockets.add(socket);
    }

    private static void closeQuietly(Socket... sockets) {
        for (var socket : sockets) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // best effort
            }
        }
    }

    @Override
    public void close() {
        try {
            if (listener != null) {
                listener.close();
                listener = null;
            }
        } catch (IOException ignored) {
            // best effort
        }
        if (acceptThread != null) {
            acceptThread.interrupt();
            acceptThread = null;
        }
        for (var socket : openSockets) {
            closeQuietly(socket);
        }
    }
}
