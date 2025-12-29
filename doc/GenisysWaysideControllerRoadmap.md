# GENISYS WaysideController Roadmap (Authoritative)

This document tracks the **planned progression** from the current GENISYS protocol architecture to a **production-ready WaysideController implementation**, with UDP as the first supported transport.

This roadmap is **implementation-oriented** and intentionally separate from GENISYS protocol definitions.

---

## Phase 0 — Baseline (Completed)

### Status: ✅ Complete

The following architectural foundations are locked and treated as authoritative:

- Normative architecture documents are binding
- Reducers are pure, exhaustive, and semantic-only
- Decode-before-event is enforced
- Reducers never see frames or bytes
- Observability ≠ logging
- Configuration ≠ file parsing
- Transport-down gating is defined and implemented
- Intent semantics are explicit and documented
- Executor behavior contract is defined
- Reference intent executor and unit tests exist

At this point, GENISYS protocol logic is transport-agnostic and deterministic.

---

## Phase 1 — Reducer → Executor Integration

### Goal

Validate that reducer output and executor behavior compose correctly in a closed semantic loop.

### Work items

- Build a reducer–executor integration harness
  - Real `GenisysStateReducer`
  - Real `GenisysControllerState`
  - `RecordingIntentExecutor`
  - In-memory event queue

- Write end-to-end semantic tests:
  - Initialization → Recall → Poll → Running
  - Poll → ResponseTimeout → Retry
  - TransportDown → TransportUp → Re-initialization

### Explicit exclusions

- No sockets
- No timers
- No decoding
- No logging

### Exit criteria

- Reducer–executor integration tests pass
- All protocol flows are validated semantically

---

## Phase 2 — Synthetic Event Sources

### Goal

Stress reducer and executor behavior using realistic but deterministic event sequences.

### Work items

- Implement synthetic event sources for:
  - `MessageReceived`
  - `ResponseTimeout`
  - `TransportUp` / `TransportDown`

- Validate behavior under unusual or adversarial event ordering

### Exit criteria

- Reducer behavior remains correct under synthetic stress
- No architectural boundaries are violated

---

## Phase 3 — Concrete Controller Skeleton

### Goal

Introduce a production-shaped controller without real I/O.

### Work items

- Implement `GenisysWaysideController`
- Owns:
  - event queue
  - reducer
  - executor
  - controller state

- Runs the canonical controller loop:

```
while (running) {
    GenisysEvent event = eventQueue.take();
    Result r = reducer.apply(state, event);
    state = r.newState();
    intentExecutor.execute(r.intents());
}
```

### Constraints

- Single-threaded / Actor-style execution
- No blocking in executor
- Events are the only inputs

### Exit criteria

- Controller runs entirely in tests
- No protocol logic depends on transport or time

---

## Phase 4 — UDP Transport Adapter

### Goal

Add the first real transport without contaminating core protocol logic.

### Work items

- Implement `UdpTransportAdapter`
  - Socket lifecycle management
  - Datagram send/receive
  - Surface `TransportUp` / `TransportDown`

- Implement decoding pipeline:
  - bytes → frames
  - frames → messages
  - messages → `GenisysEvent`

### Architectural constraints

- Decoder emits events only
- Reducer never sees frames or bytes
- Executor is the only component allowed to send frames

### Exit criteria

- GENISYS runs correctly over UDP
- No changes to reducer logic

---

## Phase 5 — Real Timer Integration

### Goal

Replace synthetic timers with real timing infrastructure.

### Work items

- Implement executor-owned timer service
- Map intents to:
  - timer arm
  - timer cancel

- Emit `ResponseTimeout` events

### Constraints

- Reducer remains unaware of timers
- Timer callbacks never mutate state directly

### Exit criteria

- Protocol timing behaves correctly under real time

---

## Phase 6 — Observability Sinks

### Goal

Make the system explain its behavior without violating architectural purity.

### Work items

- Emit observability signals for:
  - state transitions
  - intent execution

- Bind multiple sinks:
  - logging
  - metrics
  - tracing
  - diagnostic streams

### Constraints

- Reducers do not log
- Observability remains semantic

### Exit criteria

- System behavior is diagnosable in production

---

## Phase 7 — Configuration Binding

### Goal

Bind semantic configuration to deployment environments.

### Work items

- Map configuration inputs to semantic objects:
  - slave addresses
  - polling cadence
  - retry limits

### Constraints

- No file parsing in core logic
- No hidden defaults in reducers

### Exit criteria

- Same binary deploys safely across environments

---

## Phase 8 — Production Hardening

### Goal

Achieve rail-grade robustness.

### Work items

- Fault injection testing
- Transport flapping scenarios
- Long-running soak tests
- Memory and GC profiling
- Backpressure and overload testing

### Exit criteria

- System meets production reliability requirements

---

## Summary

The intellectually hardest work — protocol semantics, state modeling, intent boundaries — is already complete.

Remaining phases focus on **integration, adaptation, and validation**, not invention.

This roadmap is the authoritative guide for progressing from the current architecture to a production GENISYS WaysideController.

