package com.questrail.wayside.protocol.genisys.transport.udp;

import com.questrail.wayside.protocol.genisys.GenisysWaysideController;
import com.questrail.wayside.protocol.genisys.codec.GenisysFrameDecoder;
import com.questrail.wayside.protocol.genisys.codec.GenisysFrameEncoder;
import com.questrail.wayside.protocol.genisys.internal.decode.GenisysDecodeException;
import com.questrail.wayside.protocol.genisys.internal.decode.GenisysMessageDecoder;
import com.questrail.wayside.protocol.genisys.internal.encode.GenisysMessageEncoder;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysMessageEvent;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysTransportEvent;
import com.questrail.wayside.protocol.genisys.internal.exec.GenisysMonotonicActivityTracker;
import com.questrail.wayside.protocol.genisys.internal.frame.GenisysFrame;
import com.questrail.wayside.protocol.genisys.model.GenisysMessage;
import com.questrail.wayside.protocol.genisys.transport.DatagramEndpoint;
import com.questrail.wayside.protocol.genisys.transport.DatagramEndpointListener;

import java.net.SocketAddress;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * UdpTransportAdapter
 * =============================================================================
 * Phase 4 UDP transport adapter, implemented against the frozen codec boundary.
 *
 * <h2>Authoritative anchor</h2>
 * This adapter is the concrete realization of the "Phase 4 Integration Sketch — UDP Adapter"
 * in {@code GenisysWaysideControllerRoadmap.md}.
 *
 * <h2>Inbound path (decode-before-event)</h2>
 *
 * <pre>
 *   DatagramEndpoint
 *        → GenisysFrameDecoder
 *            → GenisysMessageDecoder
 *                → GenisysMessageEvent.MessageReceived
 *                    → GenisysWaysideController
 * </pre>
 *
 * <h2>Outbound path (executor-authoritative)</h2>
 *
 * <pre>
 *   GenisysMessage
 *        → GenisysMessageEncoder
 *            → GenisysFrameEncoder
 *                → DatagramEndpoint.send(...)
 * </pre>
 *
 * <h2>Explicit non-responsibilities (Phase 4 constraints)</h2>
 * This class MUST NOT add retries, timing, scheduling, or recovery logic.
 * Transport defects are handled as transport defects: invalid datagrams are dropped
 * and are never translated into protocol semantics.
 *
 * <h2>Phase 5 activity tracking</h2>
 * An optional {@link GenisysMonotonicActivityTracker} may be provided to record
 * semantic activity for timeout suppression. When a valid message is decoded, the
 * tracker is notified. This enables Phase 5 timeout logic to distinguish between
 * "no response" and "response arrived after timeout was armed".
 */
public class UdpTransportAdapter implements DatagramEndpointListener {

    private final GenisysWaysideController controller;
    private final DatagramEndpoint endpoint;

    private final GenisysFrameDecoder frameDecoder;
    private final GenisysFrameEncoder frameEncoder;

    private final GenisysMessageDecoder messageDecoder;
    private final GenisysMessageEncoder messageEncoder;

    // Phase 5: optional activity tracker for timeout suppression
    private final GenisysMonotonicActivityTracker activityTracker;

    /**
     * Phase 4 constructor (no activity tracking).
     */
    public UdpTransportAdapter(GenisysWaysideController controller,
                               DatagramEndpoint endpoint,
                               GenisysFrameDecoder frameDecoder,
                               GenisysFrameEncoder frameEncoder,
                               GenisysMessageDecoder messageDecoder,
                               GenisysMessageEncoder messageEncoder) {
        this(controller, endpoint, frameDecoder, frameEncoder, messageDecoder, messageEncoder, null);
    }

    /**
     * Phase 5 constructor with activity tracking.
     *
     * @param activityTracker optional tracker for recording semantic activity (may be null)
     */
    public UdpTransportAdapter(GenisysWaysideController controller,
                               DatagramEndpoint endpoint,
                               GenisysFrameDecoder frameDecoder,
                               GenisysFrameEncoder frameEncoder,
                               GenisysMessageDecoder messageDecoder,
                               GenisysMessageEncoder messageEncoder,
                               GenisysMonotonicActivityTracker activityTracker) {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.frameDecoder = Objects.requireNonNull(frameDecoder, "frameDecoder");
        this.frameEncoder = Objects.requireNonNull(frameEncoder, "frameEncoder");
        this.messageDecoder = Objects.requireNonNull(messageDecoder, "messageDecoder");
        this.messageEncoder = Objects.requireNonNull(messageEncoder, "messageEncoder");
        this.activityTracker = activityTracker; // may be null

        // The endpoint is the raw I/O surface; this adapter is the translation layer.
        this.endpoint.setListener(this);
    }

    public void start() {
        endpoint.start();
    }

    public void stop() {
        endpoint.stop();
    }

    /**
     * Encode and send a semantic protocol message as a UDP datagram.
     *
     * <p>
     * Encoding remains in the codec pipeline; transport integration code does not
     * reinterpret message meaning.
     * </p>
     */
    public void send(SocketAddress remote, GenisysMessage message) {
        Objects.requireNonNull(remote, "remote");
        Objects.requireNonNull(message, "message");

        GenisysFrame frame = messageEncoder.encode(message);
        byte[] payload = frameEncoder.encode(frame);
        endpoint.send(remote, payload);
    }

    // -------------------------------------------------------------------------
    // DatagramEndpointListener
    // -------------------------------------------------------------------------

    @Override
    public void onTransportUp() {
        controller.submit(new GenisysTransportEvent.TransportUp(Instant.now()));
        controller.drain();
    }

    @Override
    public void onTransportDown(Throwable cause) {
        // Phase 4 integration policy: cause is diagnostic-only and has no protocol meaning.
        controller.submit(new GenisysTransportEvent.TransportDown(Instant.now()));
        controller.drain();
    }

    @Override
    public void onDatagram(SocketAddress remote, byte[] payload) {
        Objects.requireNonNull(remote, "remote");
        Objects.requireNonNull(payload, "payload");

        // 1) Datagram bytes -> frame (drop invalid framing)
        Optional<GenisysFrame> frameOpt = frameDecoder.decode(payload);
        if (frameOpt.isEmpty()) {
            return;
        }

        // 2) Frame -> semantic message (drop semantic decode failures)
        final GenisysMessage message;
        try {
            message = messageDecoder.decode(frameOpt.get());
        } catch (GenisysDecodeException e) {
            return;
        }

        // 3) Phase 5: record semantic activity for timeout suppression
        //    This must happen BEFORE submitting the event so timeout checks
        //    that race with event processing see the activity.
        if (activityTracker != null) {
            activityTracker.recordSemanticActivity(message.station().value());
        }

        // 4) Message -> semantic event (decode-before-event boundary)
        controller.submit(new GenisysMessageEvent.MessageReceived(
                Instant.now(),
                message.station().value(),
                message
        ));
        controller.drain();
    }
}