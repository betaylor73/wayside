package com.questrail.wayside.protocol.genisys.internal.events;

import java.time.Instant;

/**
 * GenisysTransportEvent
 * -----------------------------------------------------------------------------
 * Base type for events that originate from the transport layer.
 *
 * These events indicate changes in the availability or health of the underlying
 * transport (serial port, TCP socket, etc.), not protocol-level correctness.
 */
public sealed interface GenisysTransportEvent extends GenisysEvent
        permits GenisysTransportEvent.TransportUp, GenisysTransportEvent.TransportDown
{
    /** Transport became available. */
    final class TransportUp extends GenisysEvent.Base implements GenisysTransportEvent {
        public TransportUp(Instant timestamp) {
            super(timestamp);
        }
    }

    /** Transport became unavailable or unusable. */
    final class TransportDown extends GenisysEvent.Base implements GenisysTransportEvent {
        public TransportDown(Instant timestamp) {
            super(timestamp);
        }
    }
}
