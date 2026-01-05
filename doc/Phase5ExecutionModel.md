# Phase 5 Execution Model (Operational Behavior)

> **Scope:** Phase 5 introduces *operational behavior* (timing, retries, scheduling, recovery, observability, configuration) **without changing** protocol semantics, reducer logic, state transitions, codec behavior, or Phase 4 transport adapter boundaries.

## Constraints (binding)

### Frozen surfaces (no changes without explicit approval)

* `genisys.md`
* Protocol semantics and message legality/phase rules
* Reducer logic
* State transitions
* Codec behavior (frame/message encode/decode)
* Phase 4 transport adapter boundaries

### Architectural invariants (binding)

* Decode-before-event
* Reducers are semantic-only
* Adapters translate bytes ⇄ semantic events only
* Transport defects ≠ protocol defects
* Executor is the sole source of outbound protocol messages
* Composition roots contain wiring only

## Phase 5 design principle

**Operational behavior belongs above the Phase 4 boundary.**

* The **reducer** remains the authority for *what* should happen next (intents, transitions).
* The **executor** remains the authority for sending outbound protocol messages.
* Phase 5 adds *when* and *how often* (timing/backoff/cadence) by **scheduling intent execution** and **injecting timeout events**.
* The **transport adapter** remains translation-only (bytes ⇄ semantic events). No timers, retries, or cadence inside adapters.

---

## Execution topology (unchanged Phase 4 pipeline)

### Inbound

`TransportEndpoint → TransportAdapter → FrameDecoder → MessageDecoder → GenisysEvent.MessageReceived → Controller.submit(event) → Reducer`

### Outbound

`Reducer emits intent → Controller.drain() → IntentExecutor executes → Runtime.send(bytes) → Adapter.send(bytes) → TransportEndpoint`

No Phase 5 logic is inserted into adapters or codec boundary.

---

## Phase 5 execution model

Phase 5 adds a single operational "driver" above the controller that provides:

1. **Serialized execution** (actor-like loop) for controller/event processing
2. **Scheduling** (poll cadence, recall cadence, control delivery cadence)
3. **Timeout detection** and **timeout injection** (as semantic timeout events)
4. **Retry policy** (spacing/backoff/jitter) implemented as scheduling policy, not semantics
5. **Transport recovery handling** (lifecycle monitoring, restart, degraded mode) without semantic leakage
6. **Operational observability** tied to runtime behavior
7. **Configuration surfaces** for all of the above

### Core rule

> **Timeouts become events**; retries remain reducer-driven.

Phase 5 does **not** silently resend “under the covers.” Instead, Phase 5 injects an explicit `ResponseTimeout` (or equivalent existing timeout event) when the response window expires. The reducer then decides the next intent(s) (retry current, send recall, declare failed, etc.) using the already-frozen semantics.

---

## Behavioral flows

### A) Response timeout → retry/fail (Phase 5 injection, existing reducer behavior)

1. Executor sends a message that expects a response (poll / recall / control, etc.).
2. Phase 5 schedules a timeout check: `sendInstant + responseTimeout`.
3. When the timeout check fires:

   * Read the current controller state snapshot (via an existing state supplier / controller query).
   * If the station’s `lastActivity` (or equivalent semantic receipt marker) is **after** the send instant, a response arrived → ignore the timeout.
   * Otherwise inject `ResponseTimeout(station, now, correlation)` into the controller.
4. Reducer processes `ResponseTimeout` and emits the next intent per existing logic (e.g., `RETRY_CURRENT`, `SEND_RECALL`, transition to FAILED, etc.).

**Result:** transport silence becomes a *semantic timeout event*; reducer remains authoritative.

### B) Poll cadence / rate limiting (Phase 5 scheduling)

When reducer emits `POLL_NEXT` (or equivalent intent), Phase 5 executor:

* Sends immediately if cadence allows, **or**
* Delays until `nextPollDue` based on policy:

  * min gap since last poll
  * global rate limit
  * per-station rate limit

No reducer changes required.

### C) Recall retry spacing / backoff (Phase 5 retry policy)

When reducer emits `SEND_RECALL` repeatedly (driven by timeouts), Phase 5 applies policy:

* Fixed delay (e.g., 250ms)
* Exponential backoff with cap
* Optional jitter

Reducer still decides that recall is the correct next action.

### D) Control delivery cadence (realize `SCHEDULE_CONTROL_DELIVERY`)

Phase 5 gives operational meaning to control delivery intents:

* Coalesce controls for a station within a `controlCoalesceWindow`
* Deliver controls on a configured cadence
* Ensure controls do not starve polling (fairness / priority policy)

No semantics or legality changes; this is purely *when to execute* control sends.

---

## Minimal new components

### 1) `GenisysOperationalDriver`

**Category:** scheduling / recovery / observability

**Purpose:** a single place to host operational runtime behavior without polluting protocol layers.

Responsibilities:

* Run a serialized event loop:

  * accept inbound semantic events
  * submit them to controller
  * drain intents
  * pass intents to executor
* Own the scheduler (timers, delayed tasks)
* Manage lifecycle of the chosen `TransportRuntime` (start/stop/restart)
* Centralize shutdown coordination

Non-responsibilities:

* No protocol interpretation
* No legality decisions
* No encoding/decoding
* No state transition logic

### 2) `TimedGenisysIntentExecutor` (Phase 5 executor implementation or wrapper)

**Category:** scheduling + retry/recovery

Responsibilities:

* Execute intents by sending via the existing runtime
* Track per-station inflight sends (typically 0/1 outstanding)
* Arm response timeouts for send types that expect responses
* Gate sends by poll cadence / rate limits
* Schedule control deliveries for `SCHEDULE_CONTROL_DELIVERY`
* On timeout, inject existing timeout events into the controller

Key property:

* Retries remain reducer-driven; this executor only schedules sends and injects timeout events.

### 3) `GenisysSchedulePolicy` + `GenisysRetryPolicy`

**Category:** configuration

A small immutable configuration surface (records) containing only operational knobs:

* `Duration responseTimeout`
* `Duration pollMinGap`
* `Duration recallMinGap`
* `BackoffStrategy recallBackoff` (fixed/exponential/capped)
* `Duration controlCadence`
* `Duration controlCoalesceWindow`
* `int maxOutstandingPerStation` (typically 1)
* Optional: global/per-station rate limits

These are explicitly Phase 5 policies (operational), not semantic rules.

### 4) `OperationalObservabilitySink`

**Category:** observability

A minimal interface for structured operational signals:

* send attempted / send completed
* timeout armed / timeout fired / timeout injected
* send delayed due to cadence
* transport up/down + restart attempts
* station failed/recovered (derived from state transitions)

**Note:** Observability is not logging; logging is one sink.

---

## Transport choice remains clean (UDP, serial, etc.)

Phase 5 is transport-agnostic by design.

* The Operational Driver depends on a **transport-neutral runtime interface**, not UDP-specific classes.
* The Intent Executor sends via that runtime interface.
* The transport-specific adapter remains translation-only.

### Key abstraction boundary

* `TransportEndpoint` (UDP socket, serial port, etc.)
* `TransportAdapter` (bytes ⇄ semantic events) **transport-specific**
* `TransportRuntime` (start/stop/send + inbound event callback) **transport-specific**
* `OperationalDriver` and `TimedIntentExecutor` **transport-neutral**

### Invariants preserved

* No transport logic in reducer/controller/executor semantics
* Adapters translate bytes ⇄ semantic events only
* Operational policies (cadence/retry/backoff) live above transport

---

## Phase 5 Time Model — Monotonic First, Wall-Clock Optional

### Problem statement
Wall-clock time is **not safe** for operational correctness because it can:

- Jump forwards or backwards (daylight saving time)
- Be stepped manually or via NTP
- Skew during suspend/resume
- Differ across systems

Therefore:

> **All Phase 5 timing, scheduling, retry, and timeout logic MUST be driven by a monotonic time source.**

Wall-clock time may be used **only for observability and reporting**, never for correctness.

---

### Binding Phase 5 invariant

Operational correctness MUST NOT depend on wall-clock time.

Concretely:

- Timeouts → monotonic
- Cadence / backoff / retry spacing → monotonic
- Elapsed time comparisons → monotonic
- State progression → monotonic-derived events
- Logging / metrics timestamps → wall-clock (informational only)

This mirrors best practice in kernels, real-time systems, and reliable protocol stacks.

---

### Architectural implications

#### 1. Split time into two explicit concepts

Phase 5 must not use `Instant.now()` (or equivalents) for logic.

Instead, define two clocks:

- **MonotonicClock** — used for all operational logic
- **WallClock** — used only for observability

Reducers and executors never see wall-clock time.

---

#### 2. Scheduling API must be monotonic

**Forbidden:**

- Scheduling based on wall-clock instants

**Allowed:**

- `scheduleAfter(Duration delay)`
- `scheduleAtTick(long monotonicTick)`

Only elapsed time matters; absolute calendar time does not.

---

#### 3. Timeout logic is elapsed-time based

Timeout evaluation must be of the form:

```
if (clock.nowTicks() - sentTick >= timeoutTicks) { ... }
```

Both `sentTick` and `nowTicks()` must come from the same `MonotonicClock`.

---

#### 4. Timeouts remain semantic events

Phase 5 injects timeout **events**; it does not silently retry.

Reducers remain authoritative for retry, recall, and failure semantics.

No reducer logic changes are required.

---

#### 5. Deterministic testing

Using monotonic time enables:

- Manual clocks
- Deterministic schedulers
- Zero `sleep()` usage
- Repeatable timeout tests

This is required for reliable Phase 5 verification.

---

### Transport independence preserved

The monotonic time model reinforces transport neutrality:

- Works identically for UDP, serial, TCP, CAN, etc.
- Survives transport restarts and blocking behavior
- Maps cleanly to embedded hardware timers or `System.nanoTime()`

---

### Explicitly forbidden in Phase 5 logic

The following are architecturally illegal for Phase 5 correctness logic:

- `Instant.now()`
- `ZonedDateTime`
- `LocalDateTime`
- Wall-clock-based scheduling
- Wall-clock elapsed time comparisons

These may appear **only** in logging, metrics, tracing, or diagnostics.

---

## Summary

Phase 5 introduces a transport-neutral **Operational Driver** and a **Timed Intent Executor** that:

* Schedules execution of existing intents
* Arms and evaluates response timeouts
* Injects timeout events to drive existing reducer retry/fail behavior
* Applies cadence/rate-limit/backoff policies
* Adds observability and configuration

All without touching frozen Phase 1–4 semantics or Phase 4 adapter boundaries.

