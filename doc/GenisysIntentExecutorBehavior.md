# GENISYS Intent Executor – Behavioral Contract

## Scope and purpose

This document defines the **behavioral contract** for concrete implementations of `GenisysIntentExecutor`.

It is **implementation‑normative** and describes *how* an executor must interpret and realize `GenisysIntents` produced by the reducer. It does **not** define GENISYS protocol semantics.

---

## Position in the architecture

The intent executor is the **only side‑effecting boundary** in the GENISYS master:

```
GenisysEvent → Reducer → GenisysIntents → IntentExecutor → (I/O, timers, events)
```

All logic above this boundary is pure and deterministic. All logic below this boundary is impure and environment‑dependent.

---

## Execution model (Normative)

### Single‑threaded / Actor semantics

A concrete executor **must** be invoked from a serialized execution context (e.g. an Actor or single‑threaded event loop).

The executor:

- must assume no concurrent calls to `execute`
- must not spawn uncontrolled concurrent protocol activity
- must not block the calling thread

---

## Interpretation of `GenisysIntents`

### Atomic interpretation

Each invocation of:

```
execute(GenisysIntents intents)
```

represents a **single atomic reducer step**.

The executor must interpret the **entire intent set atomically**:

- no partial execution
- no persistence of intents beyond the call

---

### Dominant intents

Some intent kinds dominate all others and **preempt protocol activity**.

If present, they suppress interpretation of all lower‑priority intents for that execution step.

Dominant intents:

1. `SUSPEND_ALL`
2. `BEGIN_INITIALIZATION`

#### `SUSPEND_ALL`

When present, the executor must:

- immediately suppress all protocol transmission
- cancel or quiesce outstanding protocol timers
- perform no polling, recall, or control delivery

No other intent kinds may be acted upon in the same execution step.

#### `BEGIN_INITIALIZATION`

When present (and `SUSPEND_ALL` is not present), the executor must:

- initiate the initialization sequence
- prepare for per‑slave RECALL execution
- cancel protocol activity inconsistent with initialization

---

### Protocol intents

If no dominant intent is present, protocol intents are interpreted according to current controller and slave state.

Typical mappings:

- `SEND_RECALL(station)`

  - send Recall message to `station`
  - arm a response timer for that station

- `SEND_CONTROLS(station)`

  - send Control Data (and Execute, if configured)
  - arm a response timer for that station

- `POLL_NEXT`

  - select the next slave according to controller state
  - send Poll or Ack+Poll as appropriate
  - arm a response timer

- `RETRY_CURRENT(station)`

  - resend the protocol action implied by the slave’s current phase
  - reuse or re‑arm the appropriate timer

The reducer decides *what* should happen next. The executor decides *how* that intent is realized on the wire.

---

## Timer management (Normative)

- Timers are owned by the executor
- The reducer never reasons about timer identity or reuse
- A timeout always results in a `GenisysEvent.ResponseTimeout`

The executor must ensure:

- no duplicate timers for the same semantic purpose
- timers are cancelled when superseded by dominant intents

---

## Idempotency and safety

The executor **must tolerate repeated execution** of equivalent intent sets.

- Re‑sending the same protocol message must be safe
- Re‑arming a timer must not create duplicate timeouts
- Cancellation must be idempotent

This guarantees correctness under retry, replay, and recovery scenarios.

---

## Event emission

The executor communicates outcomes **only** via `GenisysEvent`s, including:

- `MessageReceived`
- `ResponseTimeout`
- `TransportDown` / `TransportUp`

### Ingress message legality (Normative)

The I/O boundary that accepts inbound traffic (transport adapter + frame decode +
semantic message decode) MUST emit `MessageReceived` events **only** for messages
that are:

- syntactically valid and successfully decoded into a semantic `GenisysMessage`, and
- **legal for the current per-slave protocol phase and effective configuration**
  (e.g., checkback enabled vs disabled).

Messages that are decoded but *illegal in context* MUST be treated as **non-events**:

- no `MessageReceived` is emitted
- the condition is surfaced only via observability mechanisms at the I/O boundary
- protocol behavior is affected only indirectly (e.g., the reducer observes a timeout)

This preserves the architectural rule that reducer-visible events represent **semantic
truth**, not wire-level or contextual invalidity.

The executor must not mutate controller state directly.

---

## Non‑goals

The executor is **not responsible** for:

- protocol state decisions
- retry policy
- failure thresholds
- observability policy
- logging semantics

Those concerns belong to the reducer or higher layers.

---

## Summary

`GenisysIntentExecutor` is a strict execution boundary:

- deterministic input (`GenisysIntents`)
- controlled side effects
- event‑only feedback

Correctness depends on faithful adherence to this contract.
