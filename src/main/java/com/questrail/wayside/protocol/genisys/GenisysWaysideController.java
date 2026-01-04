package com.questrail.wayside.protocol.genisys;

import com.questrail.wayside.protocol.genisys.internal.events.GenisysEvent;
import com.questrail.wayside.protocol.genisys.internal.exec.GenisysIntentExecutor;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysControllerState;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysStateReducer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;

/**
 * GenisysWaysideController
 * -----------------------------------------------------------------------------
 * Phase 3 — Concrete Controller Skeleton
 *
 * <p>This class is the <em>first production-shaped owner</em> of the GENISYS protocol
 * core. It deliberately introduces <strong>structure without semantics</strong>.
 * All protocol meaning remains exclusively in the reducer and executor, which were
 * validated and stress-tested in Phases 1 and 2.</p>
 *
 * <h2>What this class is</h2>
 * <ul>
 *   <li>An explicit owner of the controller event loop</li>
 *   <li>The composition point for reducer, executor, and controller state</li>
 *   <li>A single-threaded, actor-style coordinator</li>
 * </ul>
 *
 * <h2>What this class is <em>not</em></h2>
 * <ul>
 *   <li>It is <strong>not</strong> a transport adapter</li>
 *   <li>It is <strong>not</strong> a timer service</li>
 *   <li>It is <strong>not</strong> responsible for decoding bytes or frames</li>
 *   <li>It is <strong>not</strong> allowed to invent protocol behavior</li>
 * </ul>
 *
 * <h2>Phase 3 architectural contract</h2>
 * <p>The following constraints are <strong>binding</strong>:</p>
 * <ul>
 *   <li>Protocol semantics are closed and must not be modified here</li>
 *   <li>All inputs arrive exclusively as {@link GenisysEvent} instances</li>
 *   <li>The reducer defines legality; the executor defines effects</li>
 *   <li>This class may host and invoke, but never reinterpret</li>
 * </ul>
 *
 * <h2>Execution model</h2>
 * <p>The controller executes in a deterministic, single-threaded loop:</p>
 * <pre>
 *   event → reducer → new state → intents → executor
 * </pre>
 * <p>No blocking, waiting, or scheduling occurs here. In Phase 3, callers are
 * responsible for driving the loop explicitly via {@link #step()} or {@link #drain()}.</p>
 *
 * <h2>On transient phases</h2>
 * <p>Callers and tests must not assert against transient internal phases
 * (e.g. {@code SEND_CONTROLS}). Correctness is defined in terms of stable
 * semantic invariants, not intermediate scheduling artifacts.</p>
 *
 * <h2>Testing and re-anchoring</h2>
 * <p>All Phase 1 and Phase 2 tests are expected to run unchanged against this
 * controller. Any failures indicate an error in this class, not in the
 * reducer, executor, or tests.</p>
 */
public class GenisysWaysideController
{
    private final GenisysStateReducer reducer;
    private final GenisysIntentExecutor executor;

    private final Deque<GenisysEvent> queue = new ArrayDeque<>();

    // Volatile is a minimal guard for cross-thread observation in tests.
    // Phase 3 makes no thread-safety guarantees.
    private volatile GenisysControllerState state;

    public GenisysWaysideController(GenisysControllerState initialState,
                                    GenisysStateReducer reducer,
                                    GenisysIntentExecutor executor)
    {
        this.state = Objects.requireNonNull(initialState, "initialState");
        this.reducer = Objects.requireNonNull(reducer, "reducer");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Enqueue a semantic event for later processing.
     */
    public void submit(GenisysEvent event)
    {
        Objects.requireNonNull(event, "event");
        queue.addLast(event);
    }

    /**
     * Process exactly one queued event, if present.
     *
     * @return {@code true} if an event was processed; {@code false} if the queue was empty.
     */
    public boolean step()
    {
        GenisysEvent event = queue.pollFirst();
        if (event == null) {
            return false;
        }

        GenisysStateReducer.Result result = reducer.apply(state, event);
        this.state = result.newState();

        // Executor performs side effects (or, in tests, records actions).
        executor.execute(result.intents());

        return true;
    }

    /**
     * Drain the queue until no events remain.
     */
    public void drain()
    {
        while (step()) {
            // Intentionally empty.
        }
    }

    /**
     * Exposes the current immutable controller state snapshot.
     */
    public GenisysControllerState state()
    {
        return state;
    }

    /**
     * Convenience accessor for tests and higher-level coordinators.
     */
    public int queuedEventCount()
    {
        return queue.size();
    }

    /**
     * Optional peek for debugging/test assertions without mutating the queue.
     */
    public Optional<GenisysEvent> peekNextEvent()
    {
        return Optional.ofNullable(queue.peekFirst());
    }
}
