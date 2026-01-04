package com.questrail.wayside.protocol.genisys.transport;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * FakeDatagramEndpoint
 * -----------------------------------------------------------------------------
 * Test-only {@link DatagramEndpoint} implementation.
 *
 * <p>This fake preserves the transport seam used by Phase 4 and Phase 5 tests.
 * It intentionally contains no GENISYS semantics; it only stores outbound
 * datagrams and allows tests to inject inbound datagrams.</p>
 */
public final class FakeDatagramEndpoint implements DatagramEndpoint {

    public record Sent(SocketAddress remote, byte[] payload) {}

    private DatagramEndpointListener listener;
    private final List<Sent> sent = new ArrayList<>();

    @Override
    public void setListener(DatagramEndpointListener listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    @Override
    public void start() {
        if (listener != null) {
            listener.onTransportUp();
        }
    }

    @Override
    public void stop() {
        if (listener != null) {
            listener.onTransportDown(null);
        }
    }

    @Override
    public void send(SocketAddress remote, byte[] payload) {
        Objects.requireNonNull(remote, "remote");
        Objects.requireNonNull(payload, "payload");
        sent.add(new Sent(remote, payload));
    }

    // ---------------------------------------------------------------------
    // Test helpers
    // ---------------------------------------------------------------------

    public void injectDatagram(SocketAddress remote, byte[] payload) {
        Objects.requireNonNull(remote, "remote");
        Objects.requireNonNull(payload, "payload");
        if (listener == null) {
            throw new IllegalStateException("No listener installed");
        }
        listener.onDatagram(remote, payload);
    }

    public List<Sent> sent() {
        return Collections.unmodifiableList(sent);
    }

    public void clear() {
        sent.clear();
    }
}