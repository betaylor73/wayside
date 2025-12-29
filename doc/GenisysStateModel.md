# GENISYS State Model

## 1. Purpose and Scope

This document defines the **GENISYS protocol state model** as implemented by the
Wayside Controller. It is normative for how protocol state is represented,
transitioned, and reasoned about inside the GENISYS reducer.

The goals of this document are:

- To make GENISYS state transitions explicit and inspectable
- To ensure reducer behavior is deterministic and testable
- To provide a stable re-synchronization point if implementation context is lost

This document focuses on **protocol state**, not transport state or framework
lifecycle concerns.

---

## 2. Design Principles

The GENISYS state model adheres to the following principles:

1. **Explicit lifecycle phases**
   - Protocol phases are named and finite.
   - Transitions between phases are deliberate and documented.

2. **Single source of truth**
   - All protocol-relevant state is carried in immutable state objects.
   - Reducers produce new state rather than mutating existing state.

3. **Event-driven transitions**
   - State transitions occur only in response to explicit events.
   - Protocol state transitions occur in response to semantic message events, not raw frame reception.
   - Frame validity is a prerequisite handled below the state model.

4. **Safety before liveness**
   - The model prioritizes correctness and protocol safety over throughput.

---

## 3. Top-Level State Structure

At a high level, the GENISYS controller maintains:

- Global controller state
- Per-slave protocol state

Each slave is modeled independently, even though the master orchestrates
communication.

---

## 4. Per-Slave Lifecycle States

Each GENISYS slave progresses through a defined lifecycle.

### 4.1 UNINITIALIZED

**Meaning**
- No valid communication has been established.
- The master does not yet trust the slave’s database.

**Entry Conditions**
- Controller startup
- Slave added dynamically
- Slave marked failed and reset

**Permitted Actions**
- Send Recall messages only

---

### 4.2 RECALLED

**Meaning**
- A full indication database image has been received.
- The master is synchronized with the slave’s indication state.

**Entry Conditions**
- Receipt of a valid IndicationData response to Recall

**Protocol Obligations**
- The indication data MUST be acknowledged.

**Permitted Actions**
- Send Acknowledge-and-Poll
- Transition toward ACTIVE polling

---

### 4.3 ACTIVE

**Meaning**
- Normal steady-state operation.
- The slave is actively polled and control data may be exchanged.

**Entry Conditions**
- Recall completed and acknowledgment obligations satisfied

**Permitted Actions**
- Poll / Acknowledge-and-Poll
- Send ControlData
- Execute Controls (if checkback enabled)

---

### 4.4 FAILED

**Meaning**
- Communication with the slave is unreliable or lost.
- Normal polling is suspended.

**Entry Conditions**
- Retry limits exceeded
- Repeated protocol errors

**Permitted Actions**
- Periodic Recall attempts
- Limited control delivery (policy-dependent)

---

## 5. Acknowledgment Tracking

GENISYS requires explicit acknowledgment of indication data.

The state model therefore tracks:

- `acknowledgmentPending` flag per slave

Rules:

- Any IndicationData receipt sets `acknowledgmentPending = true`
- Acknowledge-and-Poll clears the obligation
- Poll without acknowledgment pending is illegal

---

## 6. Retry and Failure Tracking

Each slave maintains retry counters associated with:

- Message timeouts
- Invalid or malformed responses

Rules:

- Retries increment deterministically on timeout/error events
- Exceeding configured limits triggers transition to FAILED

---

## 7. Dynamic Reconfiguration Considerations

Dynamic configuration changes interact with state as follows:

- Hot-reloadable parameters take effect on next transition
- Structural changes may force transition to UNINITIALIZED or FAILED
- Reconfiguration boundaries must preserve protocol invariants

---

## 8. Testing Implications

- Reducer tests assert lifecycle transitions explicitly
- Tests encode protocol narratives (startup, failure, recovery)
- Illegal transitions must be unrepresentable or rejected

---

## 9. Closing Note

This state model exists to ensure that GENISYS protocol behavior is:

- Deterministic
- Testable
- Auditable
- Resistant to accidental complexity

Any change to lifecycle semantics should update this document.
