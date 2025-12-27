package com.questrail.wayside.protocol.genisys.internal.events;

import com.questrail.wayside.protocol.genisys.internal.GenisysFrame;

import java.time.Instant;
import java.util.Objects;

/**
 * GenisysFrameEvent
 * -----------------------------------------------------------------------------
 * Events related to protocol frames received from the wire.
 *
 * These events represent *decoded* GENISYS frames. Raw byte streams and framing
 * logic live strictly below this layer.
 */
public sealed interface GenisysFrameEvent extends GenisysEvent
        permits GenisysFrameEvent.FrameReceived, GenisysFrameEvent.FrameInvalid
{
    /**
     * A syntactically valid GENISYS frame was received and decoded.
     */
    final class FrameReceived extends GenisysEvent.Base implements GenisysFrameEvent {
        private final GenisysFrame frame;

        public FrameReceived(Instant timestamp, GenisysFrame frame) {
            super(timestamp);
            this.frame = Objects.requireNonNull(frame, "frame");
        }

        public GenisysFrame frame() {
            return frame;
        }
    }

    /**
     * Bytes were received but could not be decoded into a valid GENISYS frame.
     *
     * This event allows the state machine to increment failure counters or
     * trigger retries without coupling it to framing details.
     */
    final class FrameInvalid extends GenisysEvent.Base implements GenisysFrameEvent {
        private final String reason;

        public FrameInvalid(Instant timestamp, String reason) {
            super(timestamp);
            this.reason = Objects.requireNonNull(reason, "reason");
        }

        public String reason() {
            return reason;
        }
    }
}
