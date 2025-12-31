package com.questrail.wayside.protocol.genisys.test.exec;

import com.questrail.wayside.protocol.genisys.internal.state.GenisysControllerState;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysStateReducer;

/**
 * GenisysTestHarnessFactory
 * -----------------------------------------------------------------------------
 * Centralized construction point for GENISYS test harnesses.
 *
 * <p>This factory makes all test wiring decisions <em>explicit</em> and prevents
 * test code from binding to concrete harness implementations.</p>
 */
public final class GenisysTestHarnessFactory
{
    private GenisysTestHarnessFactory() {}

    /**
     * Phase 3 default: controller-backed harness.
     */
    public static GenisysTestHarness newControllerHarness(GenisysControllerState initialState)
    {
        RecordingIntentExecutor executor = new RecordingIntentExecutor();
        return new GenisysControllerTestHarness(
                initialState,
                new GenisysStateReducer(),
                executor);
    }

    /**
     * Legacy Phase 1 / Phase 2 harness.
     */
    public static GenisysTestHarness newLegacyHarness(GenisysControllerState initialState)
    {
        RecordingIntentExecutor executor = new RecordingIntentExecutor();
        return new GenisysReducerExecutorHarness(initialState, executor);
    }
}
