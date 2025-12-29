package com.questrail.wayside.protocol.genisys.test.exec;

import com.questrail.wayside.protocol.genisys.internal.state.GenisysStateReducer;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysControllerState;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysStateReducer.Result;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysEvent;

/**
 * GenisysReducerExecutorHarness
 * --------------------------------
 *
 * Test-only harness used for Phase 1 reducer → executor integration tests.
 *
 * This class deliberately mirrors the production controller loop *without*
 * introducing:
 *  - threads
 *  - blocking
 *  - timers
 *  - transports
 *
 * It exists solely to validate that:
 *  - the reducer produces correct semantic intents, and
 *  - those intents, when executed, produce the expected semantic actions.
 *
 * Architectural constraints:
 *  - Uses the real GenisysStateReducer
 *  - Uses the real GenisysControllerState
 *  - Uses a RecordingIntentExecutor (test-only)
 *  - Applies exactly one event at a time
 */
final class GenisysReducerExecutorHarness {

    private GenisysControllerState state;
    private final GenisysStateReducer reducer;
    private final RecordingIntentExecutor executor;

    GenisysReducerExecutorHarness(GenisysControllerState initialState) {
        this.state = initialState;
        this.reducer = new GenisysStateReducer();
        this.executor = new RecordingIntentExecutor();
    }

    /**
     * Applies a single GenisysEvent to the reducer and immediately executes
     * the resulting intents.
     *
     * This method represents exactly one iteration of the conceptual
     * controller loop.
     */
    void apply(GenisysEvent event) {
        Result result = reducer.apply(state, event);
        state = result.newState();
        executor.execute(result.intents());
    }

    GenisysControllerState state() {
        return state;
    }

    RecordingIntentExecutor executor() {
        return executor;
    }
}
