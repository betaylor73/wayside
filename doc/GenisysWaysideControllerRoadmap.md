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

### Phase 3 Re‑Anchor Acknowledgement (Authoritative)

By entering Phase 3, we explicitly acknowledge and lock the following constraints:

- **Protocol semantics are closed.**  
  No changes to `genisys.md`, reducer logic, state transitions, or executor semantics are permitted in Phase 3.

- **Reducer and executor boundaries are frozen.**  
  Phase 3 code may *host* and *invoke* the reducer and executor, but must not reinterpret events, invent sequencing, or compensate for missing behavior.

- **All Phase 1 and Phase 2 tests are non‑negotiable regression oracles.**  
  Any failure of existing tests in Phase 3 indicates an error in Phase 3 code, not a deficiency in tests or protocol semantics.

- **No new sources of protocol behavior are introduced.**  
  Phase 3 introduces structural ownership only: event loop, state ownership, and wiring. Transport, timers, concurrency, and I/O remain out of scope.

- **Transient internal phases are not externally asserted.**  
  Phase 3 code must respect that reducers and executors may advance internal phases immediately; correctness is defined in terms of stable semantic invariants.

This acknowledgement formally marks the transition from **semantic validation** (Phases 1–2) to **structural ownership** (Phase 3), while preserving the integrity of the GENISYS protocol core.

---

### Goal

Introduce a production‑shaped controller without real I/O.

### Work items (all completed)

- Implement `GenisysWaysideController`

- Controller owns:

  - Event queue
  - Reducer invocation
  - Executor invocation
  - Controller state ownership

- Canonical controller loop implemented and exercised in tests

- Phase‑agnostic test harness interface introduced

- Legacy Phase 1/2 harness preserved

- Phase 3 controller‑backed harness added

- All Phase 1 and Phase 2 tests executed against the controller without modification

### Constraints (honored)

- Single‑threaded / actor‑style execution
- No blocking in executor
- Events are the only inputs
- No protocol reinterpretation

### Exit criteria (met)

- Controller runs entirely in tests
- All Phase 1 and Phase 2 tests pass unchanged
- No protocol logic depends on transport or time

### Status: ✅ **Complete**

---

## Phase 4 — UDP Transport Adapter

### Phase 4 Re‑Anchor Acknowledgement (Authoritative)

By entering Phase 4, we explicitly acknowledge and lock the following constraints:

- **GENISYS protocol semantics are permanently closed.**  
  No changes to `genisys.md`, reducer logic, state transitions, intent definitions, or executor semantics are permitted in Phase 4.

- **The reducer remains transport‑agnostic.**  
  Reducers must never see bytes, frames, sockets, ports, IP addresses, or timing artifacts. All inputs to the reducer are semantic `GenisysEvent` instances only.

- **Transport integration is strictly adapter‑level.**  
  Phase 4 introduces *adapters* that translate between real transports and semantic events. Adapters may fail, reconnect, or drop data, but they must not compensate for or reinterpret protocol behavior.

- **Decode‑before‑event is mandatory.**  
  All decoding pipelines must fully validate and classify inbound data before emitting semantic events. Invalid or undecodable input must not reach the reducer.

- **The controller is not a transport.**  
  `GenisysWaysideController` remains unaware of sockets, threads, timers, and retry logic. It consumes events and emits intents only.

- **Executor remains the sole egress for outbound traffic.**  
  Transport adapters may only send data in response to explicit executor intents. No adapter may originate protocol messages on its own initiative.

- **Transport defects are not protocol defects.**  
  Any failure observed in Phase 4 must be classified as:
  - transport implementation error, or
  - decode/encode boundary error
  
  Under no circumstances is a Phase 4 failure grounds for modifying reducer logic.

This acknowledgement formally marks the transition from **structural ownership** (Phase 3) to **real‑world integration** (Phase 4), while preserving the GENISYS protocol as a closed, validated semantic core.

---

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

### Status: 🟡 **In Progress**



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
- Phase 3: **Complete** (controller skeleton hosts core without reinterpretation)
- Phase 4: **In Progress** (codec boundary complete; UDP adapter pending)

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

### A.2 What Phase 1 proved

Phase 1 establishes the following facts as *true and tested*:

1. **Protocol semantics are transport‑independent**\
   The GENISYS protocol can be expressed, reasoned about, and validated entirely in terms of semantic events and state transitions.

2. **Reducers are pure and exhaustive**\
   Reducers:

   - Perform no I/O
   - Perform no logging
   - Perform no scheduling
   - See only semantically valid events

3. **Executor behavior is non‑inventive**\
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

6. **The protocol core is test‑complete**\
   All GENISYS protocol flows defined in Phase 1 are exercised via executable semantic tests, not transport simulations.

### A.3 What Phase 1 explicitly did *not* attempt

Phase 1 deliberately excluded:

- Real transports (UDP, serial, sockets)
- Real timers or clocks
- Decoding or encoding pipelines
- Observability sinks (logging, metrics, tracing)
- Configuration binding

These exclusions are intentional and ensure that subsequent phases add **integration and realism**, not new protocol logic.

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

- All Phase 2 stress scenarios were implemented using **existing `GenisysEvent` types only**
- No new protocol rules, state transitions, or executor behaviors were introduced
- All issues discovered during Phase 2 were resolved by **correcting test assumptions**, not by modifying reducer logic

This demonstrates that the protocol semantics defined in `genisys.md` and enforced by the reducer are **stable under stress**, not merely under idealized flows.

### B.2 What Phase 2 proved

Phase 2 establishes the following facts as *true and tested*:

1. **Reducer behavior is deterministic under adversarial ordering**  
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
   - Transient internal phases are correctly treated as non‑stable

6. **Closed reducer–executor loop semantics are respected**

   - Immediate executor‑driven advancement does not violate protocol law
   - Tests assert semantic invariants, not transient scheduling artifacts

---

## Appendix C — Phase 3 Completion Notes

This appendix records **why Phase 3 is formally closed**, what architectural risks were addressed, and what guarantees now hold before entering Phase 4.

### C.1 Why Phase 3 is closed

Phase 3 is complete because a **production‑shaped controller** has been introduced **without altering protocol semantics**, and all previously validated behavior has been preserved.

Specifically:

- `GenisysWaysideController` owns:
  - the event queue
  - reducer invocation
  - executor invocation
  - controller state lifecycle

- No protocol logic was moved, duplicated, or reinterpreted
- No new sources of behavior were introduced
- All Phase 1 and Phase 2 tests pass unchanged when executed against the controller

This confirms that the GENISYS protocol core is not merely correct in isolation, but **correct under realistic structural ownership**.

### C.2 What Phase 3 proved

Phase 3 establishes the following facts as *true and tested*:

1. **Protocol semantics survive production ownership**  
   Hosting the reducer and executor inside a controller does not alter behavior.

2. **Controller responsibilities are purely structural**  
   The controller manages sequencing and ownership only; it does not invent or reinterpret semantics.

3. **Historical tests remain authoritative**  
   Phase 1 and Phase 2 tests act as non‑negotiable regression oracles across architectural evolution.

4. **Stepwise and quiescent execution semantics coexist safely**  
   Both immediate (`apply`) and queued (`submit` + `runToQuiescence`) execution models behave consistently.

5. **The system is ready for real integration**  
   With ownership concerns resolved, subsequent phases may safely introduce transport, timers, and observability.

### C.3 What Phase 3 explicitly did *not* attempt

Phase 3 deliberately excluded:

- Real transports or sockets
- Concurrency or threading models
- Real timers or clocks
- Performance or throughput optimization

These concerns are deferred intentionally to later phases.

### C.4 Implication for Phase 4

Because Phase 3 is closed:

- Phase 4 may introduce real transport adapters (UDP) without touching reducer logic
- Decode pipelines may be added so long as they emit semantic events only
- Any protocol regression in Phase 4 is a **transport‑integration defect**, not a semantic defect

Phase 3 therefore represents the point at which the GENISYS WaysideController transitions from a **validated semantic core** to a **deployable system architecture**.

---

## Appendix D — Phase 4 Completion Notes (Codec Boundary)

This appendix records **what Phase 4 has completed to date**, what was *explicitly proven*, and why the codec boundary is now considered **audited and stable**, even though Phase 4 as a whole remains open pending full UDP transport integration.

### D.1 Scope of Phase 4 work completed

The completed portion of Phase 4 is limited strictly to the **codec boundary**, comprising:

- Frame‑level encoding and decoding
- Message‑level semantic encoding and decoding
- Enforcement of protocol‑mandated CRC rules
- End‑to‑end semantic round‑trip validation

No transport adapters, sockets, threads, or timers are included in this scope.

### D.2 What was proven

The following properties are now *true and tested*:

1. **Frame correctness is enforced at the codec boundary**

   - `GenisysFrameEncoder` and `GenisysFrameDecoder` correctly implement GENISYS framing, escaping, and CRC correctness
   - Corrupt or malformed frames are rejected before semantic decoding

2. **Semantic CRC rules are enforced correctly**

   - `$F1` (Acknowledge) explicitly forbids CRC
   - `$FB` (Poll) uses CRC presence to indicate secure polling
   - `$F2/$F3/$FC/$FD/$FE/$FA` require CRC and are rejected if missing

   These rules are enforced at the **message semantic boundary**, not at the reducer or transport layers.

3. **Encoder/decoder symmetry is proven**

   - Representative GENISYS messages successfully complete the round trip:
     
     `GenisysMessage → Frame → Bytes → Frame → GenisysMessage`

   - No‑payload and payload‑bearing messages are both covered

4. **Payload delivery is lossless and isolated**

   - Payload bytes emitted by the message encoder are delivered intact to the injected payload decoders
   - Tests prove payload plumbing without inventing `ControlSet` or `IndicationSet` constructions

5. **Architectural boundaries are preserved**

   - Reducers never see frames or bytes
   - Decoders emit semantic messages only
   - Encoders act only in response to executor intents

### D.3 What Phase 4 explicitly did *not* attempt

The completed codec work deliberately excluded:

- Socket lifecycle management
- UDP or Netty adapters
- Transport error handling
- Threading or concurrency concerns
- Timer or retry semantics

These exclusions are intentional and ensure that codec correctness is validated *independently* of transport behavior.

### D.4 Implication for remaining Phase 4 work

Because the codec boundary is now audited and stable:

- Remaining Phase 4 work is **pure integration**, not protocol design
- Any future failures must be classified as:
  - transport adapter defects, or
  - environment‑specific integration issues

Under no circumstances should remaining Phase 4 work require changes to:

- reducer logic
- executor semantics
- GENISYS protocol rules

The codec boundary therefore represents a **hard architectural seam** between validated protocol semantics and real‑world transport integration.

