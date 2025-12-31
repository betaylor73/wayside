package com.questrail.wayside.protocol.genisys.test.exec;

import com.questrail.wayside.protocol.genisys.internal.events.GenisysEvent;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysControllerState;

/**
 * GenisysTestHarness
 * -----------------------------------------------------------------------------
 * Phase-agnostic test harness interface.
 *
 * <p>This interface defines the <em>stable semantic surface</em> that all GENISYS
 * protocol tests bind to, independent of the underlying execution model.
 *
 * <p>Both the Phase 1/2 reducer–executor harness and the Phase 3 controller-backed
 * harness implement this interface. This allows tests to remain unchanged while
 * execution ownership evolves.</p>
 *
 * <p><strong>IMPORTANT:</strong> This interface is test-scope only and encodes no
 * protocol semantics. It exists purely to preserve historical continuity and
 * prevent test drift.</p>
 */
public interface GenisysTestHarness
{
    void submit(GenisysEvent event);

    /**
     * Apply exactly one semantic event immediately.
     * Used by stepwise integration tests.
     */
    void apply(GenisysEvent event);

    void runToQuiescence();

    GenisysControllerState state();

    /**
     * Exposes the RecordingIntentExecutor used by this harness so that
     * integration tests may assert on emitted intents.
     */
    RecordingIntentExecutor executor();
}
