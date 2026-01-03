package com.questrail.wayside.protocol.genisys.transport;

import java.net.SocketAddress;

/**
 * DatagramEndpoint
 * -----------------------------------------------------------------------------
 * Minimal port for a datagram-based transport (UDP-style).
 *
 * <p>This endpoint is intentionally small. Higher layers (Phase 4 wiring) are
 * responsible for:</p>
 * <ul>
 *   <li>feeding inbound datagrams into the codec pipeline</li>
 *   <li>submitting resulting semantic events into {@code GenisysWaysideController}</li>
 *   <li>using the executor to trigger outbound sends</li>
 * </ul>
 *
 * <p>Implementations may be backed by Netty, java.nio, or a test harness.</p>
 */
public interface DatagramEndpoint
{
    /**
     * Start the endpoint and begin receiving datagrams.
     *
     * <p>On successful activation, the endpoint MUST notify its listener via
     * {@link DatagramEndpointListener#onTransportUp()} exactly once per transition.</p>
     */
    void start();

    /**
     * Stop the endpoint and release all transport resources.
     *
     * <p>On shutdown (graceful or error-induced), the endpoint MUST notify its
     * listener via {@link DatagramEndpointListener#onTransportDown(Throwable)} at
     * most once per transition.</p>
     */
    void stop();

    /**
     * Send a datagram to the specified remote endpoint.
     *
     * <p>This method is used exclusively by an executor-driven path.
     * The endpoint must not invent outbound traffic.</p>
     *
     * @param remote remote destination
     * @param payload datagram payload
     */
    void send(SocketAddress remote, byte[] payload);

    /**
     * Register the listener that receives inbound datagrams and lifecycle events.
     *
     * <p>This must be called before {@link #start()}.</p>
     */
    void setListener(DatagramEndpointListener listener);
}

