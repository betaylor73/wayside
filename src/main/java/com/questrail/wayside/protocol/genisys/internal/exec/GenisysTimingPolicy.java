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
 * concerns such as timeout windows, cadence gating, and retry spacing.</p>
 *
 * <h2>Configuration Parameters</h2>
 * <ul>
 *   <li><b>responseTimeout</b> — Maximum time to wait for a slave response before
 *       injecting a {@code ResponseTimeout} event. Applies to all sends that expect
 *       a response (recall, poll, control delivery).</li>
 *   <li><b>pollMinGap</b> — Minimum interval between successive polls to the same
 *       station. Prevents overwhelming slow slaves or saturating the channel.</li>
 *   <li><b>recallRetryDelay</b> — Fixed delay between recall retry attempts for
 *       failed or unresponsive slaves. The reducer decides <em>whether</em> to retry;
 *       this policy controls <em>when</em> the retry is executed.</li>
 *   <li><b>controlCoalesceWindow</b> — Time window during which multiple control
 *       updates to the same station may be coalesced into a single delivery. Reduces
 *       unnecessary traffic when controls change rapidly.</li>
 * </ul>
 *
 * <h2>Architectural Note</h2>
 * <p>These parameters control <em>operational scheduling</em>, not protocol semantics.
 * The reducer remains the sole authority for retry decisions, failure thresholds, and
 * state transitions. This policy only influences timing and cadence.</p>
 */
public record GenisysTimingPolicy(
        Duration responseTimeout,
        Duration pollMinGap,
        Duration recallRetryDelay,
        Duration controlCoalesceWindow
) {
    /**
     * Canonical constructor with validation.
     */
    public GenisysTimingPolicy {
        Objects.requireNonNull(responseTimeout, "responseTimeout");
        Objects.requireNonNull(pollMinGap, "pollMinGap");
        Objects.requireNonNull(recallRetryDelay, "recallRetryDelay");
        Objects.requireNonNull(controlCoalesceWindow, "controlCoalesceWindow");

        if (responseTimeout.isNegative()) {
            throw new IllegalArgumentException("responseTimeout must be non-negative");
        }
        if (pollMinGap.isNegative()) {
            throw new IllegalArgumentException("pollMinGap must be non-negative");
        }
        if (recallRetryDelay.isNegative()) {
            throw new IllegalArgumentException("recallRetryDelay must be non-negative");
        }
        if (controlCoalesceWindow.isNegative()) {
            throw new IllegalArgumentException("controlCoalesceWindow must be non-negative");
        }
    }

    /**
     * Creates a policy with only the response timeout specified.
     * Other values default to zero (no cadence gating, no coalescing).
     *
     * <p>This factory is provided for backward compatibility and simple test scenarios
     * where only timeout behavior is being verified.</p>
     *
     * @param responseTimeout maximum time to wait for a slave response
     * @return a timing policy with default cadence settings
     */
    public static GenisysTimingPolicy withResponseTimeout(Duration responseTimeout) {
        return new GenisysTimingPolicy(
                responseTimeout,
                Duration.ZERO,
                Duration.ZERO,
                Duration.ZERO
        );
    }

    /**
     * Creates a policy with sensible defaults for typical GENISYS deployments.
     *
     * <p>Default values:</p>
     * <ul>
     *   <li>responseTimeout: 500ms</li>
     *   <li>pollMinGap: 10ms</li>
     *   <li>recallRetryDelay: 250ms</li>
     *   <li>controlCoalesceWindow: 50ms</li>
     * </ul>
     *
     * @return a timing policy with typical operational defaults
     */
    public static GenisysTimingPolicy defaults() {
        return new GenisysTimingPolicy(
                Duration.ofMillis(500),
                Duration.ofMillis(10),
                Duration.ofMillis(250),
                Duration.ofMillis(50)
        );
    }
}
