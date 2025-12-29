// Reference tests for RecordingIntentExecutor

package com.questrail.wayside.protocol.genisys.test.exec;

import com.questrail.wayside.protocol.genisys.internal.state.GenisysIntents;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GenisysIntentExecutorReferenceTest
 * ---------------------------------
 *
 * Reference tests that lock down the semantic behavior expected of
 * GenisysIntentExecutor implementations.
 *
 * These tests operate purely at the intent → executor boundary and
 * perform no real I/O or timing.
 */
class GenisysIntentExecutorReferenceTest {

    private RecordingIntentExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new RecordingIntentExecutor();
    }

    @Test
    void suspendAllDominatesAllOtherIntents() {
        GenisysIntents intents = GenisysIntents.builder()
                .add(GenisysIntents.Kind.SUSPEND_ALL)
                .add(GenisysIntents.Kind.SEND_RECALL)
                .targetStation(5)
                .build();

        executor.execute(intents);

        assertEquals(2, executor.actions().size(),
                "Only dominant suspend actions should be recorded");

        assertEquals(RecordedAction.protocolSuspended(), executor.actions().get(0));
        assertEquals(RecordedAction.allTimersCancelled(), executor.actions().get(1));
    }

    @Test
    void sendRecallArmsResponseTimer() {
        GenisysIntents intents = GenisysIntents.builder()
                .add(GenisysIntents.Kind.SEND_RECALL)
                .targetStation(5)
                .build();

        executor.execute(intents);

        assertEquals(2, executor.actions().size());
        assertEquals(RecordedAction.sentRecall(5), executor.actions().get(0));
        assertEquals(RecordedAction.timerArmed(5), executor.actions().get(1));
    }

    @Test
    void repeatedExecutionIsIdempotentAtSemanticLevel() {
        GenisysIntents intents = GenisysIntents.builder()
                .add(GenisysIntents.Kind.SEND_CONTROLS)
                .targetStation(3)
                .build();

        executor.execute(intents);
        executor.execute(intents);

        assertEquals(4, executor.actions().size(),
                "Repeated execution should produce stable, repeatable actions");

        assertEquals(RecordedAction.sentControls(3), executor.actions().get(0));
        assertEquals(RecordedAction.timerArmed(3), executor.actions().get(1));
        assertEquals(RecordedAction.sentControls(3), executor.actions().get(2));
        assertEquals(RecordedAction.timerArmed(3), executor.actions().get(3));
    }

    @Test
    void beginInitializationSuppressesProtocolIntents() {
        GenisysIntents intents = GenisysIntents.builder()
                .add(GenisysIntents.Kind.BEGIN_INITIALIZATION)
                // POLL_NEXT is the semantic polling intent (poll or ack+poll).
                .add(GenisysIntents.Kind.POLL_NEXT)
                .build();

        executor.execute(intents);

        assertEquals(1, executor.actions().size());
        assertEquals(RecordedAction.initializationStarted(), executor.actions().get(0));
    }

    @Test
    void pollNextProducesPollAndTimer() {
        GenisysIntents intents = GenisysIntents.builder()
                .add(GenisysIntents.Kind.POLL_NEXT)
                .build();

        executor.execute(intents);

        assertEquals(2, executor.actions().size());
        assertEquals(RecordedAction.sentPoll(), executor.actions().get(0));
        assertTrue(executor.actions().get(1).kind() == RecordedAction.Kind.TIMER_ARMED);
    }

    @Test
    void retryCurrentResendsWithoutCreatingDuplicateTimerSemantics() {
        // This test validates RETRY_CURRENT semantics at the executor boundary.
        //
        // The executor is allowed (and expected) to re-send the implied protocol
        // action, but it must not introduce *new semantic timer purposes* beyond
        // what already exists. In the reference executor, this is represented by
        // a TIMER_REARMED action rather than an additional TIMER_ARMED.

        GenisysIntents intents = GenisysIntents.builder()
                .add(GenisysIntents.Kind.RETRY_CURRENT)
                .targetStation(7)
                .build();

        executor.execute(intents);

        assertEquals(2, executor.actions().size(),
                "Retry should produce exactly one resend and one timer rearm");

        assertEquals(RecordedAction.retriedCurrent(7), executor.actions().get(0));
        assertEquals(RecordedAction.timerRearmed(7), executor.actions().get(1));
    }
}
