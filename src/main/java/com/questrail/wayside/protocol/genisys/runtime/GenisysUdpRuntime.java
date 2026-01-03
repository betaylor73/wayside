package com.questrail.wayside.protocol.genisys.runtime;

import com.questrail.wayside.protocol.genisys.GenisysWaysideController;
import com.questrail.wayside.protocol.genisys.codec.GenisysFrameDecoder;
import com.questrail.wayside.protocol.genisys.codec.GenisysFrameEncoder;
import com.questrail.wayside.protocol.genisys.internal.decode.GenisysDecodeException;
import com.questrail.wayside.protocol.genisys.internal.decode.GenisysMessageDecoder;
import com.questrail.wayside.protocol.genisys.internal.encode.GenisysMessageEncoder;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysMessageEvent;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysTransportEvent;
import com.questrail.wayside.protocol.genisys.internal.frame.GenisysFrame;
import com.questrail.wayside.protocol.genisys.transport.DatagramEndpoint;
import com.questrail.wayside.protocol.genisys.transport.DatagramEndpointListener;

import java.net.SocketAddress;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

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
 *        → GenisysFrameDecoder
 *            → GenisysMessageDecoder
 *                → Semantic Event (MessageReceived)
 *                    → GenisysWaysideController
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
 *            → GenisysFrameEncoder
 *                → DatagramEndpoint
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
 *   <li>Perform byte-level decoding or encoding directly</li>
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
    private final DatagramEndpoint endpoint;
    private final GenisysFrameDecoder frameDecoder;
    private final GenisysFrameEncoder frameEncoder;
    private final GenisysMessageDecoder messageDecoder;
    private final GenisysMessageEncoder messageEncoder;

    public GenisysUdpRuntime(GenisysWaysideController controller,
                             DatagramEndpoint endpoint,
                             GenisysFrameDecoder frameDecoder,
                             GenisysFrameEncoder frameEncoder,
                             GenisysMessageDecoder messageDecoder,
                             GenisysMessageEncoder messageEncoder) {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.frameDecoder = Objects.requireNonNull(frameDecoder, "frameDecoder");
        this.frameEncoder = Objects.requireNonNull(frameEncoder, "frameEncoder");
        this.messageDecoder = Objects.requireNonNull(messageDecoder, "messageDecoder");
        this.messageEncoder = Objects.requireNonNull(messageEncoder, "messageEncoder");

        this.endpoint.setListener(new Listener());
    }

    /**
     * Start the runtime.
     *
     * <p>This activates the underlying transport. Protocol behavior begins only
     * once {@link GenisysTransportEvent.TransportUp} is delivered to the controller.</p>
     */
    public void start() {
        endpoint.start();
    }

    /**
     * Stop the runtime and release transport resources.
     */
    public void stop() {
        endpoint.stop();
    }

    // -------------------------------------------------------------------------
    // Transport Listener
    // -------------------------------------------------------------------------

    private final class Listener implements DatagramEndpointListener {
        @Override
        public void onTransportUp() {
            controller.submit(new GenisysTransportEvent.TransportUp(Instant.now()));
            controller.drain();
        }

        @Override
        public void onTransportDown(Throwable cause) {
            controller.submit(new GenisysTransportEvent.TransportDown(Instant.now()));
            controller.drain();
        }

        @Override
        public void onDatagram(SocketAddress remote, byte[] payload) {
            // Decode bytes -> frame
            Optional<GenisysFrame> frameOpt = frameDecoder.decode(payload);
            if (frameOpt.isEmpty()) {
                // Framing error: transport-level defect. Drop silently.
                return;
            }

            GenisysFrame frame = frameOpt.get();

            // Decode frame -> message
            final GenisysMessage message;
            try {
                message = messageDecoder.decode(frame);
            } catch (GenisysDecodeException e) {
                // Protocol decode failure: treat as invalid inbound data and drop.
                // This is an integration/framing defect, not a protocol behavior signal.
                return;
            }

            // Emit semantic event (decode-before-event boundary)
            controller.submit(new GenisysMessageEvent.MessageReceived(
                    Instant.now(),
                    message.station().value(),
                    message
            ));
            controller.drain();
        }
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

        GenisysFrame frame = messageEncoder.encode(message);
        byte[] payload = frameEncoder.encode(frame);
        endpoint.send(remote, payload);
    }
}
