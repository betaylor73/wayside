package com.questrail.wayside.protocol.genisys.internal.exec;

import com.questrail.wayside.protocol.genisys.internal.events.GenisysEvent;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysTimeoutEvent;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysIntents;
import com.questrail.wayside.protocol.genisys.internal.time.Cancellable;
import com.questrail.wayside.protocol.genisys.internal.time.MonotonicClock;
import com.questrail.wayside.protocol.genisys.internal.time.MonotonicScheduler;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
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
 * <h2>Behavior</h2>
 * <ul>
 *   <li>Arms response timeouts for sends that expect responses (SEND_RECALL, SEND_CONTROLS,
 *       RETRY_CURRENT, POLL_NEXT).</li>
 *   <li>Cancels armed timeouts when dominant intents (SUSPEND_ALL, BEGIN_INITIALIZATION)
 *       are present.</li>
 *   <li>Tracks which station was sent to via {@link SendTracker} for POLL_NEXT where
 *       station selection is executor-owned.</li>
 *   <li>Suppresses timeout injection if semantic activity was observed after the send.</li>
 * </ul>
 */
public final class TimedGenisysIntentExecutor implements GenisysIntentExecutor {

    private final GenisysIntentExecutor delegate;
    private final Consumer<GenisysEvent> eventSink;
    private final MonotonicClock clock;
    private final MonotonicScheduler scheduler;
    private final Supplier<Instant> wallClock;
    private final GenisysTimingPolicy timingPolicy;
    private final GenisysMonotonicActivityTracker activityTracker;

    /**
     * Tracker for learning which station the delegate executor sent to.
     * Owned by this executor and passed to the delegate at construction time.
     */
    private final SendTracker sendTracker;

    // Per-station bookkeeping so stale scheduled tasks do nothing.
    private final AtomicLong lastArmedSequence = new AtomicLong(0);
    private volatile long armedSequenceSnapshot = 0;
    private volatile Cancellable armedTimeout = null;
    private volatile int armedStation = -1;
    private volatile long armedSendTickNanos = 0L;

    // Per-station tracking for cadence gating (last send time in nanos).
    private final Map<Integer, Long> lastSendTimeByStation = new ConcurrentHashMap<>();

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
        this.sendTracker = new SendTracker();
    }

    /**
     * Returns the {@link SendTracker} that should be passed to the delegate executor
     * at construction time. The delegate calls {@link SendTracker#recordSend(int)}
     * when sending, allowing this executor to learn which station was targeted.
     */
    public SendTracker sendTracker() {
        return sendTracker;
    }

    @Override
    public void execute(GenisysIntents intents)
    {
        Objects.requireNonNull(intents, "intents");

        // Handle dominant intents first - they suppress protocol activity.
        if (intents.contains(GenisysIntents.Kind.SUSPEND_ALL)) {
            cancelArmedTimeout();
            delegate.execute(intents);
            return;
        }

        if (intents.contains(GenisysIntents.Kind.BEGIN_INITIALIZATION)) {
            cancelArmedTimeout();
            delegate.execute(intents);
            return;
        }

        // Clear the send tracker before delegation so we can detect what was sent.
        sendTracker.clear();

        // Delegate to perform the actual send.
        delegate.execute(intents);

        // Determine which station was targeted for timeout arming.
        int station = determineTargetStation(intents);
        if (station < 0) {
            // No station was sent to; nothing to arm.
            return;
        }

        // Record send time for cadence tracking.
        lastSendTimeByStation.put(station, clock.nowNanos());

        // Arm a response timeout if this intent kind expects a response.
        if (requiresResponseTimeout(intents)) {
            armResponseTimeout(station);
        }
    }

    /**
     * Determines the target station from intents or send tracker.
     *
     * <p>For POLL_NEXT, the station selection is executor-owned, so we rely on the
     * send tracker to tell us which station was actually polled.</p>
     */
    private int determineTargetStation(GenisysIntents intents) {
        // First check if the intent explicitly specifies a station.
        int explicitStation = intents.targetStation().orElse(-1);
        if (explicitStation >= 0) {
            return explicitStation;
        }

        // For POLL_NEXT without explicit station, check what the delegate sent.
        if (intents.contains(GenisysIntents.Kind.POLL_NEXT)) {
            return sendTracker.lastSentStation();
        }

        return -1;
    }

    private static boolean requiresResponseTimeout(GenisysIntents intents)
    {
        return intents.contains(GenisysIntents.Kind.SEND_RECALL)
                || intents.contains(GenisysIntents.Kind.SEND_CONTROLS)
                || intents.contains(GenisysIntents.Kind.RETRY_CURRENT)
                || intents.contains(GenisysIntents.Kind.POLL_NEXT);
    }

    private void cancelArmedTimeout() {
        Cancellable prior = armedTimeout;
        if (prior != null) {
            prior.cancel();
            armedTimeout = null;
        }
        armedStation = -1;
    }

    private void armResponseTimeout(int station)
    {
        // Cancel any previously armed timeout (single-flight model).
        cancelArmedTimeout();

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

    /**
     * Returns the last send time for a station, or 0 if never sent.
     * Useful for cadence gating checks by external components.
     */
    public long lastSendTimeNanos(int station) {
        return lastSendTimeByStation.getOrDefault(station, 0L);
    }
}
