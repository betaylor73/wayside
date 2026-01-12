package com.questrail.wayside.protocol.genisys.observability;

import java.time.Instant;

/**
 * Record representing an error or anomaly in the GENISYS protocol stack.
 */
public record GenisysErrorEvent(
    Instant timestamp,
    String message,
    Throwable cause
) {
}
