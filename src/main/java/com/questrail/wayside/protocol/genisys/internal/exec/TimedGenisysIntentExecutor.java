package com.questrail.wayside.protocol.genisys.internal.exec;

import com.questrail.wayside.protocol.genisys.internal.events.GenisysEvent;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysTimeoutEvent;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysIntents;
import com.questrail.wayside.protocol.genisys.internal.time.Cancellable;
import com.questrail.wayside.protocol.genisys.internal.time.MonotonicClock;
import com.questrail.wayside.protocol.genisys.internal.time.MonotonicScheduler;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * TimedGenisysIntentExecutor
 * =============================================================================
 * Phase 5 operational wrapper that adds monotonic-time timeout scheduling to
 * an existing {@link GenisysIntentExecutor}.
 *
 * <h2>Anchoring constraints</h2>
 * <ul>
 *   <li>No reducer or codec changes.</li>
 *   <li>Retries remain reducer-driven; this executor injects timeout <em>events</em>.</li>
 *   <li>All correctness uses monotonic time. Wall-clock is observational only.</li>
 * </ul>
 *
 * <h2>Minimal initial behavior</h2>
 * Arms a response timeout when intents request an outbound action that expects
 * a semantic response (e.g. SEND_RECALL, SEND_CONTROLS, RETRY_CURRENT).
 */
public final class TimedGenisysIntentExecutor implements GenisysIntentExecutor {

    private final GenisysIntentExecutor delegate;
    private final Consumer<GenisysEvent> eventSink;
    private final MonotonicClock clock;
    private final MonotonicScheduler scheduler;
    private final Supplier<Instant> wallClock;
    private final GenisysTimingPolicy timingPolicy;
    private final GenisysMonotonicActivityTracker activityTracker;

    // Minimal per-station bookkeeping so stale scheduled tasks do nothing.
    private final AtomicLong lastArmedSequence = new AtomicLong(0);
    private volatile long armedSequenceSnapshot = 0;
    private volatile Cancellable armedTimeout = null;
    private volatile int armedStation = -1;
    private volatile long armedSendTickNanos = 0L;

    public TimedGenisysIntentExecutor(GenisysIntentExecutor delegate,
                                     Consumer<GenisysEvent> eventSink,
                                     GenisysMonotonicActivityTracker activityTracker,
                                     MonotonicClock clock,
                                     MonotonicScheduler scheduler,
                                     Supplier<Instant> wallClock,
                                     GenisysTimingPolicy timingPolicy)
    {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.eventSink = Objects.requireNonNull(eventSink, "eventSink");
        this.activityTracker = Objects.requireNonNull(activityTracker, "activityTracker");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.wallClock = Objects.requireNonNull(wallClock, "wallClock");
        this.timingPolicy = Objects.requireNonNull(timingPolicy, "timingPolicy");
    }

    @Override
    public void execute(GenisysIntents intents)
    {
        Objects.requireNonNull(intents, "intents");

        // Delegate first. This preserves existing execution semantics.
        delegate.execute(intents);

        // Arm a response timeout only for intent kinds that (in Phase 5 policy)
        // require receiving a semantic response within a window.
        if (!requiresResponseTimeout(intents)) {
            return;
        }

        int station = intents.targetStation().orElse(-1);
        if (station < 0) {
            // For now we only arm timeouts when a concrete station is targeted.
            // POLL_NEXT station-selection is executor-owned and is handled in a later increment.
            return;
        }

        armResponseTimeout(station);
    }

    private static boolean requiresResponseTimeout(GenisysIntents intents)
    {
        return intents.contains(GenisysIntents.Kind.SEND_RECALL)
                || intents.contains(GenisysIntents.Kind.SEND_CONTROLS)
                || intents.contains(GenisysIntents.Kind.RETRY_CURRENT);
    }

    private void armResponseTimeout(int station)
    {
        // Cancel any previously armed timeout (single-flight initial model).
        Cancellable prior = armedTimeout;
        if (prior != null) {
            prior.cancel();
        }

        long seq = lastArmedSequence.incrementAndGet();
        armedSequenceSnapshot = seq;
        armedStation = station;
        armedSendTickNanos = clock.nowNanos();

        long deadline = armedSendTickNanos + timingPolicy.responseTimeout().toNanos();

        armedTimeout = scheduler.scheduleAtNanos(deadline, () -> onResponseTimeout(seq, station, armedSendTickNanos));
    }

    private void onResponseTimeout(long seq, int station, long sendTickNanos)
    {
        // Stale guard: ignore if a new timeout has been armed since.
        if (seq != armedSequenceSnapshot) {
            return;
        }

        // Activity guard: ignore if we observed semantic activity after the send.
        long lastActivity = activityTracker.lastActivityNanos(station);
        if (lastActivity > sendTickNanos) {
            return;
        }

        // Inject the semantic timeout event. Timestamp is observational only.
        eventSink.accept(new GenisysTimeoutEvent.ResponseTimeout(wallClock.get(), station));
    }
}
