package com.questrail.wayside.protocol.genisys.internal.events;

import com.questrail.wayside.protocol.genisys.model.GenisysMessage;

import java.time.Instant;
import java.util.Objects;

/**
 * GenisysMessageEvent
 * -----------------------------------------------------------------------------
 * Events related to *semantic* GENISYS messages.
 *
 * <h2>Architectural intent</h2>
 * These events exist to enforce the "decode-before-event" boundary:
 *
 * <ul>
 *   <li>{@code GenisysFrameEvent} is wire-adjacent (header + bytes)</li>
 *   <li>{@code GenisysMessageEvent} is semantic (meaningful message objects)</li>
 * </ul>
 *
 * Reducers (and especially {@code GenisysStateReducer}) should prefer handling
 * {@code GenisysMessageEvent} rather than branching on header bytes.
 */
public sealed interface GenisysMessageEvent extends GenisysEvent
        permits GenisysMessageEvent.MessageReceived
{
    /**
     * A valid semantic GENISYS message was received.
     *
     * <p>
     * The timestamp is when the message was observed/accepted at the protocol
     * boundary (i.e., after successful frame decode).
     * </p>
     */
    final class MessageReceived extends GenisysEvent.Base implements GenisysMessageEvent
    {
        private final int stationAddress;
        private final GenisysMessage message;

        public MessageReceived(Instant timestamp, int stationAddress, GenisysMessage message) {
            super(timestamp);
            this.stationAddress = stationAddress;
            this.message = Objects.requireNonNull(message, "message");
        }

        public int stationAddress() {
            return stationAddress;
        }

        public GenisysMessage message() {
            return message;
        }
    }
}
