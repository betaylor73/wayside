package com.questrail.wayside.protocol.genisys.test.exec;

import com.questrail.wayside.protocol.genisys.internal.events.GenisysEvent;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysControllerState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * GenisysSyntheticHarness
 * ----------------------
 *
 * Phase 2 harness.
 *
 * Extends the Phase 1 reducer→executor harness concept by introducing
 * a deterministic in-memory event queue and synthetic event sources.
 *
 * This harness remains single-threaded and applies exactly one event at a time.
 */
final class GenisysSyntheticHarness
{
    private final GenisysReducerExecutorHarness inner;
    private final Deque<GenisysEvent> queue;

    GenisysSyntheticHarness(GenisysControllerState initialState) {
        this.inner = new GenisysReducerExecutorHarness(initialState, new RecordingIntentExecutor());
        this.queue = new ArrayDeque<>();
    }

    void enqueue(GenisysEvent e) {
        queue.addLast(Objects.requireNonNull(e));
    }

    void enqueueAll(List<? extends GenisysEvent> events) {
        Objects.requireNonNull(events);
        for (GenisysEvent e : events) {
            enqueue(e);
        }
    }

    /**
     * Pulls the next queued event and applies it to the real reducer/executor.
     */
    void step() {
        GenisysEvent e = queue.removeFirst();
        inner.apply(e);
    }

    /**
     * Runs until the queue is empty.
     */
    void runToQuiescence() {
        while (!queue.isEmpty()) {
            step();
        }
    }

    /**
     * Convenience: ask sources to populate the queue.
     */
    void populateFrom(List<? extends SyntheticEventSource> sources) {
        Objects.requireNonNull(sources);
        for (SyntheticEventSource s : sources) {
            enqueueAll(s.events());
        }
    }

    GenisysControllerState state() {
        return inner.state();
    }

    RecordingIntentExecutor executor() {
        return inner.executor();
    }

    /**
     * For Phase 2 tests that want to restart from the same state, callers can
     * snapshot actions before clearing.
     */
    List<RecordedAction> drainActions() {
        List<RecordedAction> out = new ArrayList<>(executor().actions());
        executor().clear();
        return out;
    }
}
