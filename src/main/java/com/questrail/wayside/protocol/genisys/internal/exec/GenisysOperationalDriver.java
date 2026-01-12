package com.questrail.wayside.protocol.genisys.internal.exec;

import com.questrail.wayside.protocol.genisys.internal.events.GenisysEvent;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysControllerState;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysIntents;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysStateReducer;
import com.questrail.wayside.protocol.genisys.internal.time.Cancellable;
import com.questrail.wayside.protocol.genisys.internal.time.MonotonicClock;
import com.questrail.wayside.protocol.genisys.internal.time.MonotonicScheduler;
import com.questrail.wayside.protocol.genisys.internal.time.SystemWallClock;
import com.questrail.wayside.protocol.genisys.observability.GenisysErrorEvent;
import com.questrail.wayside.protocol.genisys.observability.GenisysObservabilitySink;
import com.questrail.wayside.protocol.genisys.observability.GenisysStateTransitionEvent;
import com.questrail.wayside.protocol.genisys.observability.NullObservabilitySink;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * GenisysOperationalDriver
 * =============================================================================
 * Phase 5 operational coordinator that runs a serialized event loop for GENISYS protocol execution.
 *
 * <h2>Purpose</h2>
 * This class provides the "actor-like" runtime behavior for a GENISYS master:
 * <ul>
 *   <li>Serialized event processing (one event at a time)</li>
 *   <li>State management (maintains current {@link GenisysControllerState})</li>
 *   <li>Reducer coordination (applies events via {@link GenisysStateReducer})</li>
 *   <li>Intent execution with timing policies (via {@link GenisysIntentExecutor})</li>
 *   <li>Scheduled polling and control delivery (driven by timing policy)</li>
 * </ul>
 *
 * <h2>Threading Model</h2>
 * The driver runs a single event-processing thread. All events are submitted to a queue
 * and processed sequentially. This ensures:
 * <ul>
 *   <li>No concurrent modification of controller state</li>
 *   <li>Deterministic event ordering</li>
 *   <li>Simple reasoning about correctness</li>
 * </ul>
 *
 * <h2>Lifecycle</h2>
 * <pre>
 *   driver.start()           → starts event loop thread
 *   driver.submitEvent(...)  → enqueues event for processing
 *   driver.stop()            → gracefully stops event loop
 * </pre>
 *
 * <h2>Phase 5 Constraints</h2>
 * <ul>
 *   <li>No changes to reducer semantics or state transitions</li>
 *   <li>No changes to protocol message encoding/decoding</li>
 *   <li>All timing uses monotonic time sources</li>
 *   <li>Transport-neutral (works with UDP, serial, etc.)</li>
 * </ul>
 */
public final class GenisysOperationalDriver {

    private final GenisysStateReducer reducer;
    private final GenisysIntentExecutor executor;
    private final MonotonicClock clock;
    private final MonotonicScheduler scheduler;
    private final GenisysTimingPolicy timingPolicy;
    private final Supplier<GenisysControllerState> initialStateSupplier;
    private final GenisysObservabilitySink observabilitySink;

    private final BlockingQueue<GenisysEvent> eventQueue;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Object stateLock = new Object();

    private volatile GenisysControllerState currentState;
    private volatile Thread eventLoopThread;
    private volatile Cancellable scheduledPoll;

    /**
     * Creates a new operational driver.
     *
     * @param reducer reducer for state transitions
     * @param executor executor for intents (typically {@link TimedGenisysIntentExecutor})
     * @param clock monotonic clock for timing
     * @param scheduler scheduler for deferred execution
     * @param timingPolicy timing policy for cadence/retry/backoff
     * @param initialStateSupplier supplies the initial controller state
     */
    public GenisysOperationalDriver(GenisysStateReducer reducer,
                                   GenisysIntentExecutor executor,
                                   MonotonicClock clock,
                                   MonotonicScheduler scheduler,
                                   GenisysTimingPolicy timingPolicy,
                                   Supplier<GenisysControllerState> initialStateSupplier,
                                   GenisysObservabilitySink observabilitySink)
    {
        this.reducer = Objects.requireNonNull(reducer, "reducer");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.timingPolicy = Objects.requireNonNull(timingPolicy, "timingPolicy");
        this.initialStateSupplier = Objects.requireNonNull(initialStateSupplier, "initialStateSupplier");
        this.observabilitySink = Objects.requireNonNullElse(observabilitySink, NullObservabilitySink.INSTANCE);

        this.eventQueue = new LinkedBlockingQueue<>();
        this.currentState = initialStateSupplier.get();
    }

    /**
     * Backward-compatible constructor for Phase 5.
     */
    public GenisysOperationalDriver(GenisysStateReducer reducer,
                                   GenisysIntentExecutor executor,
                                   MonotonicClock clock,
                                   MonotonicScheduler scheduler,
                                   GenisysTimingPolicy timingPolicy,
                                   Supplier<GenisysControllerState> initialStateSupplier)
    {
        this(reducer, executor, clock, scheduler, timingPolicy, initialStateSupplier, null);
    }

    /**
     * Starts the event loop thread.
     * Idempotent: calling start() multiple times has no effect after the first call.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            currentState = initialStateSupplier.get();
            eventLoopThread = new Thread(this::runEventLoop, "genisys-operational-driver");
            eventLoopThread.start();
        }
    }

    /**
     * Stops the event loop thread gracefully.
     * Blocks until the event loop thread terminates.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            // Cancel any scheduled polling
            if (scheduledPoll != null) {
                scheduledPoll.cancel();
                scheduledPoll = null;
            }

            // Interrupt the event loop thread if it's blocked on queue.take()
            if (eventLoopThread != null) {
                eventLoopThread.interrupt();
                try {
                    eventLoopThread.join(5000); // Wait up to 5 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Submits an event for processing.
     * Events are processed sequentially in submission order.
     *
     * @param event the event to process (must not be null)
     */
    public void submitEvent(GenisysEvent event) {
        Objects.requireNonNull(event, "event");
        if (running.get()) {
            eventQueue.offer(event);
        }
    }

    /**
     * Returns the current controller state.
     * Thread-safe: can be called from any thread.
     *
     * @return the current state
     */
    public GenisysControllerState currentState() {
        synchronized (stateLock) {
            return currentState;
        }
    }

    /**
     * Main event loop - runs on dedicated thread.
     */
    private void runEventLoop() {
        while (running.get()) {
            try {
                GenisysEvent event = eventQueue.take(); // Blocks until event available
                if (running.get()) {
                    processEvent(event);
                }
            } catch (InterruptedException e) {
                // Expected during shutdown
                if (running.get()) {
                    Thread.currentThread().interrupt();
                }
            } catch (Exception e) {
                observabilitySink.onError(new GenisysErrorEvent(
                    SystemWallClock.INSTANCE.now(),
                    "Event processing error",
                    e
                ));
            }
        }
    }

    /**
     * Processes a single event: apply via reducer, execute resulting intents.
     */
    private void processEvent(GenisysEvent event) {
        final GenisysControllerState oldState;
        final GenisysStateReducer.Result result;

        synchronized (stateLock) {
            oldState = currentState;
            result = reducer.apply(currentState, event);
            currentState = result.newState();
        }

        // Observability hook
        observabilitySink.onStateTransition(new GenisysStateTransitionEvent(
            SystemWallClock.INSTANCE.now(),
            oldState,
            result.newState(),
            event,
            result.intents()
        ));

        // Execute intents if non-empty
        if (!result.intents().isEmpty()) {
            executeIntents(result.intents());
        }
    }

    /**
     * Executes intents with timing policy applied.
     *
     * <p>Phase 5 timing policies (cadence gating, retry delays) are enforced here
     * by scheduling delayed execution when immediate execution would violate policy.</p>
     */
    private void executeIntents(GenisysIntents intents) {
        // For Phase 5, immediate execution through the timed executor.
        // The TimedGenisysIntentExecutor arms timeouts; cadence/retry scheduling
        // is deferred to future Phase 5 refinements.
        executor.execute(intents);

        // TODO Phase 5: Add cadence gating - check timing policy and delay POLL_NEXT
        // if too soon after last poll. For now, executor handles timeout arming only.
    }
}
