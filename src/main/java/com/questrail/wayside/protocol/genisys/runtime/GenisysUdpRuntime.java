package com.questrail.wayside.protocol.genisys.runtime;

import com.questrail.wayside.protocol.genisys.GenisysWaysideController;
import com.questrail.wayside.protocol.genisys.codec.GenisysFrameDecoder;
import com.questrail.wayside.protocol.genisys.codec.GenisysFrameEncoder;
import com.questrail.wayside.protocol.genisys.internal.decode.GenisysMessageDecoder;
import com.questrail.wayside.protocol.genisys.internal.encode.GenisysMessageEncoder;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysTransportEvent;
import com.questrail.wayside.protocol.genisys.transport.DatagramEndpoint;
import com.questrail.wayside.protocol.genisys.transport.udp.UdpTransportAdapter;

import java.net.SocketAddress;
import java.util.Objects;

import com.questrail.wayside.protocol.genisys.model.GenisysMessage;

/**
 * GenisysUdpRuntime
 * =============================================================================
 * Phase 4 composition root for running the GENISYS WaysideController over a
 * datagram-based transport (UDP).
 *
 * <h2>Architectural Role</h2>
 * This class is a <strong>wiring and ownership component only</strong>. It exists
 * to connect otherwise independent, already-validated subsystems into a single
 * runnable topology suitable for real transport integration.
 *
 * <p><strong>No protocol semantics live here.</strong> All protocol meaning,
 * state transitions, retries, and lifecycle rules remain exclusively within the
 * reducer/executor pair hosted by {@link GenisysWaysideController}.</p>
 *
 * <h2>Inbound Data Flow (Decode-Before-Event)</h2>
 * The inbound path enforced by this class is intentionally linear and explicit:
 *
 * <pre>
 *   DatagramEndpoint
 *        → UdpTransportAdapter
 *            → GenisysFrameDecoder
 *                → GenisysMessageDecoder
 *                    → Semantic Event (MessageReceived)
 *                        → GenisysWaysideController
 * </pre>
 *
 * <p>This flow guarantees that:</p>
 * <ul>
 *   <li>Reducers never see bytes, frames, sockets, or transport artifacts</li>
 *   <li>Malformed input is classified as a transport/framing defect and dropped</li>
 *   <li>Only fully validated semantic events reach the protocol core</li>
 * </ul>
 *
 * <h2>Outbound Data Flow (Executor-Driven)</h2>
 * Outbound traffic follows the reverse direction and is <em>executor-authoritative</em>:
 *
 * <pre>
 *   GenisysIntentExecutor
 *        → GenisysUdpRuntime.send(...)
 *            → UdpTransportAdapter.send(...)
 *                → GenisysMessageEncoder
 *                    → GenisysFrameEncoder
 *                        → DatagramEndpoint
 * </pre>
 *
 * <p>This ensures that transport code never originates protocol messages and
 * that all outbound traffic is the result of explicit reducer decisions.</p>
 *
 * <h2>Explicit Non-Responsibilities</h2>
 * This class MUST NOT:
 * <ul>
 *   <li>Interpret protocol meaning</li>
 *   <li>Modify reducer or executor behavior</li>
 *   <li>Invent retries, polling, or timing behavior</li>
 *   <li>Perform byte-level decoding or encoding directly (that is the adapter/codec boundary)</li>
 * </ul>
 *
 * <h2>Execution Model and Determinism</h2>
 * All calls into {@link GenisysWaysideController} must be serialized.
 * This class assumes (but does not enforce) that it is invoked from a single
 * execution context (e.g., a Netty EventLoop or single-threaded runner).
 *
 * <p>Immediate calls to {@link GenisysWaysideController#drain()} ensure that each inbound
 * transport stimulus is fully resolved into stable semantic state before
 * processing the next stimulus.</p>
 */
public final class GenisysUdpRuntime {
    private final GenisysWaysideController controller;
    private final UdpTransportAdapter transport;

    public GenisysUdpRuntime(GenisysWaysideController controller,
                             DatagramEndpoint endpoint,
                             GenisysFrameDecoder frameDecoder,
                             GenisysFrameEncoder frameEncoder,
                             GenisysMessageDecoder messageDecoder,
                             GenisysMessageEncoder messageEncoder) {
        this.controller = Objects.requireNonNull(controller, "controller");

        // Phase 4 boundary: UDP transport integration is performed by the adapter.
        // The runtime is a composition root only.
        this.transport = new UdpTransportAdapter(
                this.controller,
                Objects.requireNonNull(endpoint, "endpoint"),
                Objects.requireNonNull(frameDecoder, "frameDecoder"),
                Objects.requireNonNull(frameEncoder, "frameEncoder"),
                Objects.requireNonNull(messageDecoder, "messageDecoder"),
                Objects.requireNonNull(messageEncoder, "messageEncoder")
        );
    }

    /**
     * Start the runtime.
     *
     * <p>This activates the underlying transport. Protocol behavior begins only
     * once {@link GenisysTransportEvent.TransportUp} is delivered to the controller.</p>
     */
    public void start() {
        transport.start();
    }

    /**
     * Stop the runtime and release transport resources.
     */
    public void stop() {
        transport.stop();
    }

    // -------------------------------------------------------------------------
    // Outbound hook (executor-facing)
    // -------------------------------------------------------------------------

    /**
     * Send a protocol message to the specified remote endpoint.
     * <p>
     * Outbound pipeline is explicit and symmetric:
     * GenisysMessage -> GenisysFrame -> byte[]
     */
    public void send(SocketAddress remote, GenisysMessage message) {
        Objects.requireNonNull(remote, "remote");
        Objects.requireNonNull(message, "message");

        // Phase 4 boundary: encoding and UDP send are delegated to the adapter/codec pipeline.
        transport.send(remote, message);
    }
}