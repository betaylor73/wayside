package com.questrail.wayside.protocol.genisys.transport;

import java.net.SocketAddress;

/**
 * DatagramEndpointListener
 * -----------------------------------------------------------------------------
 * Callback sink for {@link DatagramEndpoint}.
 *
 * <p>All callbacks must be delivered in a <em>serialized</em> manner by the
 * implementation. Ordering guarantees must be documented by each concrete
 * endpoint (Netty endpoints typically serialize callbacks on the channel’s
 * event loop).</p>
 */
public interface DatagramEndpointListener
{
    /**
     * Called when the transport becomes usable.
     *
     * <p>This is a transport lifecycle signal only. It carries no protocol meaning.
     * Phase 4 wiring will translate this into a semantic {@code TransportUp} event.</p>
     */
    void onTransportUp();

    /**
     * Called when the transport becomes unusable.
     *
     * <p>This is a transport lifecycle signal only. It carries no protocol meaning.
     * Phase 4 wiring will translate this into a semantic {@code TransportDown} event.</p>
     *
     * @param cause an exception or diagnostic cause; may be {@code null} for
     *              orderly shutdown
     */
    void onTransportDown(Throwable cause);

    /**
     * Called when a datagram is received.
     *
     * <p>This method must deliver the payload exactly as received (minus any
     * framework-specific wrappers). For Netty-backed implementations, this means
     * the adapter must copy from {@code ByteBuf} into a {@code byte[]} and ensure
     * reference-counted buffers are released internally.</p>
     *
     * <p>The listener MUST treat the payload as an atomic unit (a full datagram).
     * No streaming assumptions are permitted at this boundary.</p>
     *
     * @param remote remote sender endpoint
     * @param payload raw datagram payload
     */
    void onDatagram(SocketAddress remote, byte[] payload);
}
