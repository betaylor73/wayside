package com.questrail.wayside.protocol.genisys.test.exec;

import com.questrail.wayside.protocol.genisys.internal.state.GenisysStateReducer;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysControllerState;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysStateReducer.Result;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysEvent;

import java.util.Deque;
import java.util.ArrayDeque;
import java.util.Objects;

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
 *
 * <p>This class is intentionally retained, largely unchanged, to preserve the
 * historical semantics and explanatory value of early-phase tests. It directly
 * coordinates the reducer and executor without a production controller.</p>
 *
 * <p>In Phase 3 and later, tests should prefer {@link GenisysControllerHarness},
 * but this class remains a valid {@link GenisysTestHarness} implementation.</p>
 */
final class GenisysReducerExecutorHarness implements GenisysTestHarness
{
    private final GenisysStateReducer reducer = new GenisysStateReducer();
    private final RecordingIntentExecutor executor;
    private final Deque<GenisysEvent> queue = new ArrayDeque<>();

    private GenisysControllerState state;

    public GenisysReducerExecutorHarness(GenisysControllerState initialState,
                                         RecordingIntentExecutor executor)
    {
        this.state = Objects.requireNonNull(initialState, "initialState");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public void submit(GenisysEvent event)
    {
        queue.addLast(Objects.requireNonNull(event));
    }

    /**
     * Applies a single GenisysEvent to the reducer and immediately executes
     * the resulting intents.
     *
     * This method represents exactly one iteration of the conceptual
     * controller loop.
     */
    @Override
    public void apply(GenisysEvent event) {
        Objects.requireNonNull(event, "event");
        Result result = reducer.apply(state, event);
        state = result.newState();
        executor.execute(result.intents());
    }

    @Override
    public void runToQuiescence()
    {
        while (!queue.isEmpty()) {
            GenisysEvent event = queue.pollFirst();
            GenisysStateReducer.Result result = reducer.apply(state, event);
            state = result.newState();
            executor.execute(result.intents());
        }
    }

    @Override
    public GenisysControllerState state()
    {
        return state;
    }

    @Override
    public RecordingIntentExecutor executor()
    {
        return executor;
    }
}
