# GENISYS Intent Scheduling

## Scope

This document defines the **intent scheduling and interpretation contract** for the GENISYS master implementation.

Intent scheduling in GENISYS is **not** a standalone runtime component. It is a set of **deterministic rules** governing how an immutable set of intents, produced by the reducer for a single event, is interpreted and executed.

- Inputs: `GenisysControllerState` + `GenisysIntents` (from the reducer)
- Execution model: a single-threaded controller loop (e.g. Actor-style)
- Output: execution of zero or more **semantic protocol actions** and/or control-flow effects

This document is **implementation-normative** (master behavior), not a GENISYS protocol definition.

## Core rules

### Rule 1 — Intents are semantic-only

Intents do not contain frames, bytes, timing details, or transport mechanics.

### Rule 2 — Intents are edge-triggered

Each reducer step may emit intents. The executor treats these intents as **edge-triggered commands** that apply to the *current* reducer step only.

- Intents do not persist beyond a single reducer–executor cycle
- If repeated action is required (retry, recall loop), the reducer will emit a new intent on each applicable event
- The executor must **not** invent retries, polling, or recall behavior

### Rule 3 — Intents are interpreted atomically per reducer step

For each event processed by the controller loop:

1. The reducer is applied exactly once
2. The resulting `GenisysIntents` set is passed exactly once to the executor
3. The executor interprets the **entire intent set atomically**

The executor may:
- send zero or more protocol messages
- arm or cancel timers
- suppress protocol activity

The reducer never sequences execution and never assumes a one-to-one mapping between intents and messages.

### Rule 4 — Global gating preempts all

If `GenisysControllerState.globalState == TRANSPORT_DOWN`, the executor must treat the intent set as **globally suppressed**.

- No protocol messages are sent
- No timers are armed or retried
- Only transport recovery actions are permitted

### Rule 5 — Initialization dominates

If `globalState == INITIALIZING`, the only scheduled actions are those required to complete initialization (per-slave RECALL).

## Intent precedence and dominance

`GenisysIntents` may contain multiple intent kinds simultaneously. Ordering is not encoded in the intent set itself.

The executor must apply **deterministic precedence rules** to interpret the set coherently.

### Dominant intent kinds

Some intents dominate all others and preempt normal protocol execution:

1. `SUSPEND_ALL`
2. `BEGIN_INITIALIZATION`

If a dominant intent is present, lower-priority protocol intents must be ignored for that execution step.

### Protocol intent interpretation

If no dominant intent is present, protocol intents are interpreted according to the current controller and slave state:

- `SEND_RECALL` → send Recall to the targeted slave
- `SEND_CONTROLS` → send Control Data (and Execute if configured)
- `POLL_NEXT` → send Poll (or Ack+Poll) for the appropriate slave
- `RETRY_CURRENT` → resend the protocol action implied by the slave’s current phase

The reducer is responsible for deciding *what* should happen next; the executor is responsible for determining *how* that intent is realized.

The scheduler maps intent kinds to semantic actions:

- `SEND_RECALL(station)` → send Recall message for `station`
- `SEND_CONTROLS(station)` → send Control Data (and Execute if enabled) for `station`
- `SEND_ACK_POLL(station)` → send Ack+Poll for `station`
- `SEND_POLL(station)` → send Poll for `station`
- `SUSPEND_ALL` → no outbound messages; ensure transport layer is quiesced
- `BEGIN_INITIALIZATION` → enqueue initialization sequence start (typically RECALL-first)
- `RETRY_CURRENT(station)` → resend the action implied by the slave’s current phase

## Execution context

The GENISYS controller is expected to execute the reducer–executor loop within a **single-threaded execution context** (e.g. an Actor).

Conceptually:

```
while (true) {
    GenisysEvent event = eventQueue.take();
    Result r = reducer.apply(state, event);
    state = r.newState();
    intentExecutor.execute(r.intents());
}
```

All reducer application and intent execution occurs atomically with respect to event processing.

---

## Open items

- Whether initialization recall order is determined entirely by reducer state or assisted by executor-local iteration
- Whether executor may coalesce multiple protocol sends within a single intent execution step
- Exact timer semantics for `RETRY_CURRENT` (arming vs reuse)

