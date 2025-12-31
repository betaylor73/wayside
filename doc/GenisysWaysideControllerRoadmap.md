# GENISYS WaysideController Roadmap (Authoritative)

This document tracks the **planned progression** from the current GENISYS protocol architecture to a **production‑ready WaysideController implementation**, with UDP as the first supported transport.

This roadmap is **implementation‑oriented** and intentionally separate from GENISYS protocol definitions. It reflects the *current, authoritative status* of the project as of the completion of Phase 1.

---

## Phase 0 — Baseline (Completed)

### Status: ✅ **Complete**

The following architectural foundations are locked and treated as authoritative:

- Normative architecture documents are binding
- Reducers are pure, exhaustive, and semantic‑only
- Decode‑before‑event is enforced
- Reducers never see frames or bytes
- Observability ≠ logging
- Configuration ≠ file parsing
- Transport‑down gating is defined and implemented
- Intent semantics are explicit and documented
- Executor behavior contract is defined
- Reference intent executor and unit tests exist

At this point, GENISYS protocol logic is transport‑agnostic and deterministic.

---

## Phase 1 — Reducer → Executor Integration

### Goal

Validate that reducer output and executor behavior compose correctly in a **closed semantic loop**, without transport, timers, or decoding.

### Work items (all completed)

- Reducer–executor integration harness
  - Real `GenisysStateReducer`
  - Real `GenisysControllerState`
  - `RecordingIntentExecutor`
  - In‑memory event queue

- End‑to‑end semantic integration tests:
  - Initialization → Recall → Control delivery → Polling
  - Poll → ResponseTimeout → Retry / Failure
  - TransportDown → TransportUp → Forced re‑initialization

- Protocol‑mandated lifecycle completion:
  - Global `INITIALIZING → RUNNING` transition derived once **all slaves complete initial Recall at least once**

### Explicit exclusions (honored)

- No sockets
- No timers
- No decoding
- No logging

### Exit criteria

- Reducer–executor integration tests pass
- All protocol flows validated semantically
- Global lifecycle semantics match `MasterStateMachine.md`

### Status: ✅ **Complete**

Phase 1 is now **formally closed**. The reducer/executor pair constitutes a complete, deterministic semantic protocol core.

---

## Phase 2 — Synthetic Event Sources

### Goal

Stress reducer and executor behavior using **realistic but deterministic synthetic event sequences**, validating correctness under adversarial ordering.

Phase 2 introduces *no new protocol behavior* and *no new architectural responsibilities*.

### Work items (current focus)

- Implement synthetic event producers for existing event types:
  - `MessageReceived`
  - `ResponseTimeout`
  - `TransportUp` / `TransportDown`

- Exercise adversarial scenarios:
  - Duplicate messages
  - Late responses (message‑after‑timeout)
  - Missing responses
  - Transport flapping at arbitrary points in the protocol cycle

### Constraints (unchanged)

- Reducer logic must not change
- Executor must not invent retries, polling, or sequencing
- No transport, sockets, decoding, or timers

### Exit criteria

- Reducer behavior remains deterministic and correct under stress
- No architectural boundaries are violated
- No new protocol logic is required to accommodate stress scenarios

### Status: ✅ **Complete**

---

## Phase 3 — Concrete Controller Skeleton

### Goal

Introduce a production‑shaped controller without real I/O.

### Work items

- Implement `GenisysWaysideController`
- Owns:
  - Event queue
  - Reducer
  - Executor
  - Controller state

- Run the canonical controller loop:

```text
while (running) {
    GenisysEvent event = eventQueue.take();
    Result r = reducer.apply(state, event);
    state = r.newState();
    intentExecutor.execute(r.intents());
}
```

### Constraints

- Single‑threaded / Actor‑style execution
- No blocking in executor
- Events are the only inputs

### Exit criteria

- Controller runs entirely in tests
- No protocol logic depends on transport or time

### Status: ⏳ **Pending**

---

## Phase 4 — UDP Transport Adapter

### Goal

Add the first real transport without contaminating core protocol logic.

### Work items

- Implement `UdpTransportAdapter`
  - Socket lifecycle management
  - Datagram send/receive
  - Emit `TransportUp` / `TransportDown`

- Implement decoding pipeline:
  - Bytes → Frames
  - Frames → Messages
  - Messages → `GenisysEvent`

### Architectural constraints

- Decoder emits events only
- Reducer never sees frames or bytes
- Executor is the only component allowed to send frames

### Exit criteria

- GENISYS runs correctly over UDP
- No changes to reducer logic

### Status: ⏳ **Pending**

---

## Phase 5 — Real Timer Integration

### Goal

Replace synthetic timers with real timing infrastructure.

### Work items

- Implement executor‑owned timer service
- Map intents to:
  - Timer arm
  - Timer cancel

- Emit `ResponseTimeout` events

### Constraints

- Reducer remains unaware of timers
- Timer callbacks never mutate state directly

### Exit criteria

- Protocol timing behaves correctly under real time

### Status: ⏳ **Pending**

---

## Phase 6 — Observability Sinks

### Goal

Make the system explain its behavior without violating architectural purity.

### Work items

- Emit observability signals for:
  - State transitions
  - Intent execution

- Bind multiple sinks:
  - Logging
  - Metrics
  - Tracing
  - Diagnostic streams

### Constraints

- Reducers do not log
- Observability remains semantic

### Exit criteria

- System behavior is diagnosable in production

### Status: ⏳ **Pending**

---

## Phase 7 — Configuration Binding

### Goal

Bind semantic configuration to deployment environments.

### Work items

- Map configuration inputs to semantic objects:
  - Slave addresses
  - Poll cadence
  - Retry limits

### Constraints

- No file parsing in core logic
- No hidden defaults in reducers

### Exit criteria

- Same binary deploys safely across environments

### Status: ⏳ **Pending**

---

## Phase 8 — Production Hardening

### Goal

Achieve rail‑grade robustness.

### Work items

- Fault injection testing
- Transport flapping scenarios
- Long‑running soak tests
- Memory and GC profiling
- Backpressure and overload testing

### Exit criteria

- System meets production reliability requirements

### Status: ⏳ **Pending**

---

## Summary (Authoritative)

- Phase 0: **Complete**
- Phase 1: **Complete** (global lifecycle semantics closed)
- Phase 2: **Complete** (stress-validated under adversarial semantics)

The intellectually hardest work — protocol semantics, state modeling, intent boundaries — is now complete. Remaining phases focus on **validation, integration, and adaptation**, not invention.

---

## Appendix A — Phase 1 Completion Notes

This appendix records **why Phase 1 is formally closed** and **what was proven**, to provide a durable re‑anchoring point for future work and reviews.

### A.1 Why Phase 1 is closed

Phase 1 is complete because the GENISYS protocol core now forms a **closed, deterministic semantic loop**:

- The reducer and executor compose without hidden side effects
- All protocol behavior is driven exclusively by **semantic events**
- No protocol decisions depend on transport availability, timing infrastructure, or wire‑level artifacts

The final blocking item for Phase 1 — the global lifecycle transition from `INITIALIZING` to `RUNNING` — has been implemented and validated:

- The transition is **derived**, not imperative
- It occurs exactly when **all configured slaves have completed initial Recall at least once**
- The behavior matches the normative requirements in `MasterStateMachine.md`

With this change, Phase 1 no longer contains any acknowledged gaps or “known incompleteness.”

---

### A.2 What Phase 1 proved

Phase 1 establishes the following facts as *true and tested*:

1. **Protocol semantics are transport‑independent**  
   The GENISYS protocol can be expressed, reasoned about, and validated entirely in terms of semantic events and state transitions.

2. **Reducers are pure and exhaustive**  
   Reducers:
   - Perform no I/O
   - Perform no logging
   - Perform no scheduling
   - See only semantically valid events

3. **Executor behavior is non‑inventive**  
   The executor:
   - Interprets intents atomically
   - Applies deterministic precedence rules
   - Does not invent retries, polls, or protocol sequencing

4. **Initialization and recovery semantics are correct**  
   - Recall‑until‑active behavior is validated
   - TransportDown globally suppresses protocol activity
   - TransportUp forces re‑initialization via Recall

5. **Failure and retry behavior is deterministic**  
   - Timeouts escalate retries deterministically
   - Failed slaves re‑enter Recall without corrupting global state

6. **The protocol core is test‑complete**  
   All GENISYS protocol flows defined in Phase 1 are exercised via executable semantic tests, not transport simulations.

---

### A.3 What Phase 1 explicitly did *not* attempt

Phase 1 deliberately excluded:

- Real transports (UDP, serial, sockets)
- Real timers or clocks
- Decoding or encoding pipelines
- Observability sinks (logging, metrics, tracing)
- Configuration binding

These exclusions are intentional and ensure that subsequent phases add **integration and realism**, not new protocol logic.

---

### A.4 Implication for subsequent phases

Because Phase 1 is closed:

- Phase 2 may **stress** the protocol core but must not change it
- Phase 3 may **host** the protocol core but must not reinterpret it
- Later phases may **adapt** the protocol core to transports and environments without contaminating its semantics

Phase 1 therefore represents a **semantic foundation** that all subsequent work must preserve.

---

## Appendix B — Phase 2 Completion Notes

This appendix records **why Phase 2 is formally closed**, what was *intentionally stressed*, and what semantic guarantees were confirmed before advancing to Phase 3.

### B.1 Why Phase 2 is closed

Phase 2 is complete because the GENISYS reducer–executor core has been exercised under **adversarial but legal semantic conditions** without requiring any reducer or protocol changes.

Specifically:

- All Phase 2 stress scenarios were implemented using **existing ****GenisysEvent**** types only**
- No new protocol rules, state transitions, or executor behaviors were introduced
- All issues discovered during Phase 2 were resolved by **correcting test assumptions**, not by modifying reducer logic

This demonstrates that the protocol semantics defined in `genisys.md` and enforced by the reducer are **stable under stress**, not merely under idealized flows.

---

### B.2 What Phase 2 proved

Phase 2 establishes the following facts as *true and tested*:

1. **Reducer behavior is deterministic under adversarial ordering**\
   Duplicate events, reordered events, and delayed events do not produce nondeterministic state or illegal transitions.

2. **Failure semantics are phase‑scoped and correct**

   - Failure counters increment monotonically only in phases where failures are meaningful
   - Timeouts during `RECALL` do not pollute operational failure metrics
   - Late valid responses may legally normalize failure state

3. **Cross‑slave isolation is preserved**

   - Failures, retries, and progress for one slave do not affect any other slave
   - Interleaved multi‑slave sequences remain coherent and deterministic

4. **Transport gating semantics are enforced globally**

   - `TransportDown` suppresses all non‑transport events
   - Stale messages and timeouts are ignored while transport is unavailable

5. **Global re‑initialization semantics are correct**

   - `TransportDown → TransportUp` forces a return to `INITIALIZING`
   - All slaves are re‑entered into recall semantics
   - Transient internal phases (e.g. `SEND_CONTROLS`) are correctly treated as non‑stable

6. **Closed reducer–executor loop semantics are respected**

   - Immediate executor‑driven advancement does not violate protocol law
   - Tests assert *semantic invariants*, not transient scheduling artifacts

---

### B.3 What Phase 2 explicitly did *not* attempt

Phase 2 deliberately excluded:

- Production controller ownership
- Threading or concurrency models
- Real transport adapters
- Real timing infrastructure
- Performance or throughput evaluation

Phase 2 is strictly concerned with **semantic correctness under stress**, not deployment realism.

---

### B.4 Implication for Phase 3

Because Phase 2 is closed:

- The reducer and executor are considered **stress‑validated**
- Phase 3 may introduce a production‑shaped controller **without modifying protocol logic**
- All Phase 1 and Phase 2 tests must continue to pass unchanged when run against the Phase 3 controller

Phase 3 therefore transitions the project from **semantic validation** to **structural ownership**, not from correctness to correctness.

This clean separation is intentional and preserves the architectural integrity of the GENISYS WaysideController.

