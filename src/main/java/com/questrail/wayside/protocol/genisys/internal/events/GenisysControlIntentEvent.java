package com.questrail.wayside.protocol.genisys.internal.events;

import com.questrail.wayside.api.ControlSet;

import java.time.Instant;
import java.util.Objects;

/**
 * GenisysControlIntentEvent
 * -----------------------------------------------------------------------------
 * Event indicating that the desired control state has changed.
 *
 * This event bridges the semantic layer ({@link com.questrail.wayside.api.WaysideController})
 * and the GENISYS protocol state machine.
 */
public sealed interface GenisysControlIntentEvent extends GenisysEvent
        permits GenisysControlIntentEvent.ControlIntentChanged {

    /**
     * Indicates that a new control intent has been expressed.
     */
    final class ControlIntentChanged extends GenisysEvent.Base
            implements GenisysControlIntentEvent {

        private final ControlSet delta;
        private final ControlSet full;

        public ControlIntentChanged(Instant timestamp,
                                    ControlSet delta,
                                    ControlSet full) {
            super(timestamp);
            this.delta = Objects.requireNonNull(delta, "delta");
            this.full = Objects.requireNonNull(full, "full");
        }

        /**
         * Returns only the controls that changed.
         */
        public ControlSet delta() {
            return delta;
        }

        /**
         * Returns the complete materialized control state.
         */
        public ControlSet full() {
            return full;
        }
    }
}
