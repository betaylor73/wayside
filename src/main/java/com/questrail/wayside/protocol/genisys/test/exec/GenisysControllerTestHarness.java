package com.questrail.wayside.protocol.genisys.test.exec;

import com.questrail.wayside.protocol.genisys.GenisysWaysideController;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysEvent;
import com.questrail.wayside.protocol.genisys.internal.exec.GenisysIntentExecutor;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysControllerState;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysStateReducer;

/*
 * -----------------------------------------------------------------------------
 * Phase 3 Test Harness Adapter
 * -----------------------------------------------------------------------------
 *
 * This adapter preserves the Phase 1 / Phase 2 test surface while redirecting
 * execution through {@link GenisysWaysideController}. It exists solely to
 * maintain historical continuity and prevent semantic drift.
 *
 * IMPORTANT:
 *  - This class lives in test scope.
 *  - It introduces no new behavior.
 *  - It must remain a thin delegation layer.
 */
public final class GenisysControllerTestHarness implements GenisysTestHarness
{
    private final RecordingIntentExecutor executor;
    private final GenisysWaysideController controller;

    public GenisysControllerTestHarness(GenisysControllerState initialState,
            GenisysStateReducer reducer,
            GenisysIntentExecutor executor)
    {
        this.executor = (RecordingIntentExecutor) executor;
        this.controller = new GenisysWaysideController(initialState, reducer, executor);
    }

    @Override
    public void submit (GenisysEvent event)
    {
        controller.submit(event);
    }

    @Override
    public void apply(GenisysEvent event)
    {
        controller.submit(event);
        controller.step();
    }

    @Override
    public void runToQuiescence ()
    {
        controller.drain();
    }

    @Override
    public GenisysControllerState state ()
    {
        return controller.state();
    }

    @Override
    public RecordingIntentExecutor executor () {
        return executor;
    }
}

