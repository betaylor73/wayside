package com.questrail.wayside.protocol.genisys.internal.events;

import java.time.Instant;
import java.util.Objects;

/**
 * GenisysEvent
 * -----------------------------------------------------------------------------
 * Marker interface for all internal events processed by the GENISYS master
 * state machine.
 *
 * <h2>Role in the architecture</h2>
 * GENISYS is modeled as an event-driven, actor-style system. All changes to
 * protocol state occur strictly in response to {@link GenisysEvent}s that are
 * serialized and processed one at a time.
 * <p>
 * Events are the *only* way information enters the GENISYS controller core.
 * This includes:
 * <ul>
 *   <li>Bytes or frames received from the wire</li>
 *   <li>Timeouts and scheduled actions</li>
 *   <li>External semantic intent (control updates)</li>
 *   <li>Transport-level lifecycle changes</li>
 * </ul>
 *
 * <h2>Design constraints</h2>
 * <ul>
 *   <li>Events must be immutable</li>
 *   <li>Events must be side-effect free</li>
 *   <li>Events must carry only the information needed to advance state</li>
 * </ul>
 *
 * This interface deliberately contains no methods other than a timestamp,
 * allowing event handling logic to pattern-match on concrete event types.
 */
public interface GenisysEvent
{
    /**
     * Time at which the event occurred or was generated.
     * <p>
     * Used for debugging, tracing, and (optionally) timeout correlation.
     */
    Instant timestamp();

    /**
     * Convenience base class for simple events.
     */
    abstract class Base implements GenisysEvent {
        private final Instant timestamp;

        protected Base(Instant timestamp) {
            this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }
    }
}
