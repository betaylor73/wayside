# GENISYS Non-Functional Concerns
*(Normative Architecture Document)*

## 1. Purpose and Scope

This document captures **non-functional architectural concerns** for the GENISYS protocol implementation. These concerns are **first-class design drivers** and must be considered on equal footing with protocol correctness and state-machine behavior.

This document is **normative**:
If implementation choices conflict with the principles and decisions recorded here, the implementation is presumed incorrect unless this document is explicitly revised.

At present, this document captures the following non-functional concerns:

1. Observability
2. Configurability

Additional concerns (e.g., resilience, safety, timing determinism) may be added in future revisions.

---

## 2. Architectural Principles (Global)

The following principles apply to all non-functional concerns captured herein:

- Non-functional concerns **must not leak into protocol semantics**
- Reducers and state machines **must remain pure and deterministic**
- Implementation mechanisms (logging frameworks, file formats, libraries) are **replaceable**
- Architectural intent must be **documented independently of code**

---

## 3. Observability

### 3.1 Core Principle (Normative)

> **Observability is not logging.  
> Logging is one sink for observability.**

This distinction is foundational and intentional.

Observability refers to the system’s ability to **explain its behavior**, both during normal operation and failure scenarios. Logging is merely one possible output mechanism for that information.

### 3.2 What Observability Means in GENISYS

In the GENISYS architecture, observability means the ability to answer questions such as:

- What semantic message was received?
- What protocol state transition occurred as a result?
- Why did a slave enter FAILED state?
- Why was a recall initiated?
- Why were controls (re)sent?
- Why did a timeout or retry occur?

These questions must be answerable **without inspecting raw frames or byte streams**.

### 3.3 Observability Boundaries

Observability is applied at **semantic and architectural boundaries**, not at arbitrary implementation points.

#### Observability is expected at:

- **Semantic message receipt**
  - Message type
  - Station address
- **Protocol state transitions**
  - Old state → new state
  - Triggering event
- **Failure and recovery events**
  - Timeout escalation
  - FAILED → RECALL transitions
- **Configuration changes**
  - What changed
  - When it changed
  - Which protocol components were affected

#### Observability is *not* expected at:

- Byte-level framing or escaping
- CRC calculation mechanics
- Transport buffer management
- Reducer internal decision trees beyond state transitions

### 3.4 Reducers and Observability

Reducers **must not perform logging**.

However, reducers **may emit observability signals** in one of the following forms:

- Structured events
- State-transition metadata
- Intent annotations

This preserves reducer purity while enabling observability at higher layers.

### 3.5 Observability Sinks (Non-Normative)

The following are examples of observability sinks:

- SLF4J / Logback logging
- Metrics collection
- Tracing systems
- Diagnostic event streams
- Test harnesses

These are **implementation choices**, not architectural commitments.

---

## 4. Configurability

### 4.1 Core Principle (Normative)

Configuration in GENISYS is **semantic**, not mechanical.

The protocol and state machine do not depend on:
- File formats
- Configuration libraries
- Parsing mechanisms
- Storage locations

They depend only on **configuration values expressed as domain-specific objects**.

### 4.2 Configuration as Data, Not I/O

Configuration must be represented internally as:

- Immutable value objects
- Explicit inputs to protocol components
- Clearly versioned and traceable

File-based configuration (e.g., XML) is an **external concern** and must not leak into protocol logic.

### 4.3 Dynamic Reconfiguration (Hard Requirement)

GENISYS **must support dynamic reconfiguration**.

This implies:

- Configuration changes can occur at runtime
- Configuration changes are modeled as **events**
- State transitions caused by configuration changes are explicit
- Safety and protocol invariants are preserved

Configuration changes must **not** require:
- Restarting the controller
- Resetting protocol state unnecessarily
- Reinitializing transports unless explicitly required

### 4.4 Reducers and Configuration

Reducers:

- May consume configuration values
- May react to configuration change events
- Must not know *how* configuration was sourced or parsed

Configuration changes must be observable in the same sense as protocol events.

---

## 5. Relationship to Other Architecture Documents

This document complements and constrains the following:

- **GenisysPackageArchitecture.md**
- **GenisysStateModel.md**
- **GenisysMessageModel.md**
- **MasterStateMachine.md**

Where conflicts arise, **this document governs non-functional behavior**.

---

## 6. Future Non-Functional Concerns (Reserved)

The following concerns are expected to be captured here in future revisions:

- Fault tolerance and resilience
- Safety invariants
- Timing determinism
- Resource usage constraints
- Security and trust boundaries

---

## 7. Summary (Normative)

- Observability is a **design property**, not a logging strategy
- Configuration is **semantic input**, not file parsing
- Reducers remain pure and deterministic
- Non-functional concerns are **explicitly documented**
- Implementation choices remain flexible without eroding intent
