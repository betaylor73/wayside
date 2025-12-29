// Test-only reference implementation of GenisysIntentExecutor
// Records semantic actions instead of performing real I/O.

package com.questrail.wayside.protocol.genisys.test.exec;

import com.questrail.wayside.protocol.genisys.internal.exec.GenisysIntentExecutor;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysIntents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * RecordingIntentExecutor
 * -----------------------
 *
 * A test-only reference executor that records semantic execution actions
 * instead of performing any real protocol I/O or timer scheduling.
 *
 * This class exists solely to validate reducer → intent → executor semantics.
 * It must never be used in production.
 */
public final class RecordingIntentExecutor implements GenisysIntentExecutor {

    /** Recorded semantic actions, in execution order. */
    private final List<RecordedAction> actions = new ArrayList<>();

    @Override
    public void execute(GenisysIntents intents) {
        // Clear is NOT performed here: tests assert cumulative behavior per step
        // unless explicitly reset by the test harness.

        if (intents.isEmpty()) {
            return;
        }

        // Dominant intent handling
        if (intents.contains(GenisysIntents.Kind.SUSPEND_ALL)) {
            actions.add(RecordedAction.protocolSuspended());
            actions.add(RecordedAction.allTimersCancelled());
            return;
        }

        if (intents.contains(GenisysIntents.Kind.BEGIN_INITIALIZATION)) {
            actions.add(RecordedAction.initializationStarted());
            return;
        }

        // Protocol intents
        intents.targetStation().ifPresentOrElse(station -> {
            if (intents.contains(GenisysIntents.Kind.SEND_RECALL)) {
                actions.add(RecordedAction.sentRecall(station));
                actions.add(RecordedAction.timerArmed(station));
            }
            if (intents.contains(GenisysIntents.Kind.SEND_CONTROLS)) {
                actions.add(RecordedAction.sentControls(station));
                actions.add(RecordedAction.timerArmed(station));
            }
            if (intents.contains(GenisysIntents.Kind.RETRY_CURRENT)) {
                actions.add(RecordedAction.retriedCurrent(station));
                actions.add(RecordedAction.timerRearmed(station));
            }
            // Even if a specific station is targeted, POLL_NEXT remains a global scheduler action
            if (intents.contains(GenisysIntents.Kind.POLL_NEXT)) {
                actions.add(RecordedAction.sentPoll());
                actions.add(RecordedAction.timerArmed(null));
            }
        }, () -> {
            if (intents.contains(GenisysIntents.Kind.POLL_NEXT)) {
                actions.add(RecordedAction.sentPoll());
                actions.add(RecordedAction.timerArmed(null));
            }
        });
    }

    public List<RecordedAction> actions() {
        return Collections.unmodifiableList(actions);
    }

    public void clear() {
        actions.clear();
    }
}
