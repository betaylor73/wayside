package com.questrail.wayside.protocol.genisys.internal.exec;

import com.questrail.wayside.protocol.genisys.internal.state.GenisysIntents;

/**
 * GenisysIntentExecutor
 * -----------------------------------------------------------------------------
 * Execution boundary between the pure GENISYS state machine and the impure
 * world of protocol I/O, timers, and transports.
 *
 * <h2>Role in the architecture</h2>
 * {@code GenisysIntentExecutor} is responsible for *realizing* the intentions
 * produced by the {@link com.questrail.wayside.protocol.genisys.internal.state.GenisysStateReducer}.
 *
 * It is the ONLY layer allowed to:
 * <ul>
 *   <li>Send GENISYS frames</li>
 *   <li>Start or cancel timers</li>
 *   <li>Interact with transports (serial, TCP, etc.)</li>
 *   <li>Schedule future events</li>
 * </ul>
 *
 * Everything above this boundary is pure, deterministic logic.
 * Everything below this boundary is impure and side-effecting.
 *
 * <h2>Actor-style execution model</h2>
 * Implementations are expected to execute intents in a serialized manner,
 * consistent with an actor or single-threaded event-loop model.
 *
 * This interface deliberately does not expose callbacks or futures.
 * Execution results are communicated back to the state machine only via
 * {@code GenisysEvent}s.
 */
public interface GenisysIntentExecutor
{
    /**
     * Execute the supplied protocol intentions.
     * <p>
     * Implementations may interpret this as:
     * <ul>
     *   <li>sending one or more GENISYS frames</li>
     *   <li>arming or canceling timers</li>
     *   <li>updating internal execution bookkeeping</li>
     * </ul>
     *
     * Execution must be <b>non-blocking</b>. Any subsequent outcomes
     * (responses, timeouts, failures) must be reported back to the controller
     * core as {@code GenisysEvent}s.
     *
     * @param intents immutable set of actions to perform
     */
    void execute(GenisysIntents intents);
}
