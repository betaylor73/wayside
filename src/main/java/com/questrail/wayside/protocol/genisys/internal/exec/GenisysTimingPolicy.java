package com.questrail.wayside.protocol.genisys.internal.exec;

import java.time.Duration;
import java.util.Objects;

/**
 * GenisysTimingPolicy
 * -----------------------------------------------------------------------------
 * Phase 5 operational timing configuration for the executor layer.
 *
 * <p>This is deliberately <em>operational only</em>. It must not encode
 * protocol semantics or message legality rules; it only controls scheduling
 * concerns such as timeout windows.</p>
 */
public record GenisysTimingPolicy(Duration responseTimeout)
{
    public GenisysTimingPolicy {
        Objects.requireNonNull(responseTimeout, "responseTimeout");
        if (responseTimeout.isNegative()) {
            throw new IllegalArgumentException("responseTimeout must be non-negative");
        }
    }
}