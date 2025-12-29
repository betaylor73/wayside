# GENISYS Master State Machine (Conceptual)

> **Purpose:** This document defines the *conceptual* state machine for a GENISYS
> master implementation. It is intentionally protocol-centric but implementation
> agnostic, and is designed to map cleanly onto an actor-style controller.
>
> This document is normative for the GENISYS master implementation and is intended to be directly reflected in the 
> state reducer.
> 
> This document precedes any concrete wire-level or transport implementation.

---

## 1. Design principles

The GENISYS master state machine is designed around the following principles:

1. **Single-threaded, event-driven semantics**

   * All state transitions occur in response to discrete events
   * No blocking waits; time is modeled via timers

2. **Explicit temporal reasoning**

   * Polling, retries, recalls, and control delivery are time-ordered behaviors
   * Control flow is not stack-based, but causal

3. **Semantic separation**

   * This state machine governs *protocol behavior*
   * It does not interpret signal meaning
   * It does not infer control↔indication causality

4. **Deterministic behavior under failure**

   * All error cases lead to explicit retry, recall, or failure states

---

## 2. Key concepts

### 2.1 Slave context

Each configured slave is modeled independently with its own context:

* Station address
* Active / failed flag
* Consecutive failure count
* Whether an acknowledgment is pending
* Whether a control update is pending
* Last-known indication database (merged incrementally)

The master cycles through slaves sequentially.

### 2.2 Global vs per-slave state

* **Global state** governs whether the master is initializing or running
* **Per-slave state** governs how a specific slave is polled, retried, or recalled

---

## 3. Global master states

### 3.1 `INITIALIZING`

**Entry condition:**

* Master startup
* Global communication recovery

**Behavior:**

* Iterate through all configured slaves
* For each slave, enter the per-slave `RECALL` state

**Exit condition:**

* All slaves have been successfully recalled at least once

---

### 3.2 `RUNNING`

**Entry condition:**

* All slaves marked active

**Behavior:**

* Cycle continuously through slaves
* For each slave, execute the per-slave polling state machine

**Exit condition:**

* Global transport failure
* Explicit shutdown

---

## 4. Per-slave states

Each slave proceeds through the following states during master operation.

---

### 4.1 `RECALL`

**Purpose:**

* Synchronize the master with the slave’s full indication database

**Entry actions:**

* Send `$FD` Recall to the slave
* Start response timer

**On valid response:**

* Merge indication data (if any)
* Mark slave active
* Reset failure count
* Transition to `SEND_CONTROLS`

**On timeout:**

* **Do not increment failure count** (the slave is already considered failed)
* Remain in `RECALL`
* Retry recall indefinitely

**Notes:**

* RECALL represents an explicit recovery loop
* Timeouts during RECALL do not escalate failure state
* Exit from RECALL is driven *only* by receipt of a valid semantic response

---

### 4.2 `SEND_CONTROLS`

**Purpose:**

* Deliver current control image to the slave

**Entry actions:**

* If no control changes are pending, transition immediately to `POLL`
* Otherwise:

    * Send `$FC` Control Data (and `$FE` Execute if checkback is enabled)
    * Start response timer

**On valid response:**

* Clear pending control flag
* Reset failure count
* Transition to `POLL`

**On timeout:**

* Increment consecutive failure count
* If failures < threshold:
    * Remain in `SEND_CONTROLS`
    * Retry control delivery
* If failures == threshold:
    * Transition to `FAILED`
    * Preserve pending control state
    * Initiate recovery via `RECALL`

---

### 4.3 `POLL`

**Purpose:**

* Solicit new or changed indication data

**Entry actions:**

* If acknowledgment is pending, send `$FA` Acknowledge+Poll
* Otherwise, send `$FB` Poll
* Start response timer

**On valid `$F2` Indication Data:**

* Merge indication update
* Mark acknowledgment pending
* Reset failure count
* Transition to next slave

**On valid `$F1` Acknowledge:**

* Clear acknowledgment pending
* Reset failure count
* Transition to next slave

**On timeout:**

* Increment consecutive failure count
* If failures < threshold:
    * Remain in `POLL`
    * Retry polling
* If failures == threshold:
    * Transition to `FAILED`
    * Clear acknowledgment pending
    * Initiate recovery via `RECALL`

---

### 4.4 `FAILED`

**Purpose:**

* Represent a slave with repeated communication failures

**Entry condition:**

* Failure count reaches threshold during `POLL` or `SEND_CONTROLS`

**Behavior:**

* Slave is considered out of protocol sync
* No normal polling or control delivery occurs
* Recovery is attempted via transition to `RECALL`

**Exit condition:**

* Receipt of any valid semantic slave response

**On exit:**

* Reset failure count
* Transition to `RECALL`

---

## 5. Events

The state machine reacts to the following events:

* `MessageReceived(message)`
* `ResponseTimeout`
* `ControlIntentChanged`
* `TransportUp`
* `TransportDown`

Events are serialized and processed one at a time.

Frame reception and decoding occur below the state machine and do not directly drive state transitions.

---

## 6. Timers

Timers are modeled as scheduled events:

* Response timeout per message
* Optional inter-poll delay

Timers do not block execution and do not introduce concurrency.

---

## 7. Mapping to `AbstractWaysideController`

* `ControlIntentChanged` originates from `setControls()`
* Indication merges occur via `applyIndicationUpdate()`
* Controller health is reflected via `setStatus()`

The GENISYS state machine determines *when* and *how* these semantic hooks are invoked.

---

## 8. Notes on extensibility

* Secure poll and checkback modes introduce additional transitions but do not
  fundamentally alter the structure
* Additional protocol variants can reuse the same master architecture with
  different state transitions

---

**End of document**

