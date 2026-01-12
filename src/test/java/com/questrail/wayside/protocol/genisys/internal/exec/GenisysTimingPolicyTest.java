package com.questrail.wayside.protocol.genisys.internal.exec;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GenisysTimingPolicyTest
 * -----------------------------------------------------------------------------
 * Validates Phase 5 timing policy configuration and factory methods.
 */
class GenisysTimingPolicyTest {

    @Test
    void canonicalConstructorAcceptsValidDurations() {
        GenisysTimingPolicy policy = new GenisysTimingPolicy(
                Duration.ofMillis(500),
                Duration.ofMillis(10),
                Duration.ofMillis(250),
                Duration.ofMillis(50)
        );

        assertEquals(Duration.ofMillis(500), policy.responseTimeout());
        assertEquals(Duration.ofMillis(10), policy.pollMinGap());
        assertEquals(Duration.ofMillis(250), policy.recallRetryDelay());
        assertEquals(Duration.ofMillis(50), policy.controlCoalesceWindow());
    }

    @Test
    void canonicalConstructorAcceptsZeroDurations() {
        GenisysTimingPolicy policy = new GenisysTimingPolicy(
                Duration.ZERO,
                Duration.ZERO,
                Duration.ZERO,
                Duration.ZERO
        );

        assertEquals(Duration.ZERO, policy.responseTimeout());
        assertEquals(Duration.ZERO, policy.pollMinGap());
        assertEquals(Duration.ZERO, policy.recallRetryDelay());
        assertEquals(Duration.ZERO, policy.controlCoalesceWindow());
    }

    @Test
    void canonicalConstructorRejectsNullResponseTimeout() {
        assertThrows(NullPointerException.class, () ->
                new GenisysTimingPolicy(null, Duration.ZERO, Duration.ZERO, Duration.ZERO)
        );
    }

    @Test
    void canonicalConstructorRejectsNullPollMinGap() {
        assertThrows(NullPointerException.class, () ->
                new GenisysTimingPolicy(Duration.ZERO, null, Duration.ZERO, Duration.ZERO)
        );
    }

    @Test
    void canonicalConstructorRejectsNullRecallRetryDelay() {
        assertThrows(NullPointerException.class, () ->
                new GenisysTimingPolicy(Duration.ZERO, Duration.ZERO, null, Duration.ZERO)
        );
    }

    @Test
    void canonicalConstructorRejectsNullControlCoalesceWindow() {
        assertThrows(NullPointerException.class, () ->
                new GenisysTimingPolicy(Duration.ZERO, Duration.ZERO, Duration.ZERO, null)
        );
    }

    @Test
    void canonicalConstructorRejectsNegativeResponseTimeout() {
        assertThrows(IllegalArgumentException.class, () ->
                new GenisysTimingPolicy(Duration.ofMillis(-1), Duration.ZERO, Duration.ZERO, Duration.ZERO)
        );
    }

    @Test
    void canonicalConstructorRejectsNegativePollMinGap() {
        assertThrows(IllegalArgumentException.class, () ->
                new GenisysTimingPolicy(Duration.ZERO, Duration.ofMillis(-1), Duration.ZERO, Duration.ZERO)
        );
    }

    @Test
    void canonicalConstructorRejectsNegativeRecallRetryDelay() {
        assertThrows(IllegalArgumentException.class, () ->
                new GenisysTimingPolicy(Duration.ZERO, Duration.ZERO, Duration.ofMillis(-1), Duration.ZERO)
        );
    }

    @Test
    void canonicalConstructorRejectsNegativeControlCoalesceWindow() {
        assertThrows(IllegalArgumentException.class, () ->
                new GenisysTimingPolicy(Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ofMillis(-1))
        );
    }

    @Test
    void withResponseTimeoutFactoryDefaultsOtherFieldsToZero() {
        GenisysTimingPolicy policy = GenisysTimingPolicy.withResponseTimeout(Duration.ofMillis(750));

        assertEquals(Duration.ofMillis(750), policy.responseTimeout());
        assertEquals(Duration.ZERO, policy.pollMinGap());
        assertEquals(Duration.ZERO, policy.recallRetryDelay());
        assertEquals(Duration.ZERO, policy.controlCoalesceWindow());
    }

    @Test
    void defaultsFactoryReturnsExpectedValues() {
        GenisysTimingPolicy policy = GenisysTimingPolicy.defaults();

        assertEquals(Duration.ofMillis(500), policy.responseTimeout());
        assertEquals(Duration.ofMillis(10), policy.pollMinGap());
        assertEquals(Duration.ofMillis(250), policy.recallRetryDelay());
        assertEquals(Duration.ofMillis(50), policy.controlCoalesceWindow());
    }
}
