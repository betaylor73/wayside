# GENISYS Package Architecture

## 1. Purpose and Scope

This document codifies the architectural intent behind the package structure of the
**GENISYS protocol implementation** within the Wayside Controller system.

Its primary goals are:

- To clearly define **package-level responsibilities and boundaries**
- To prevent **accidental architectural drift**
- To provide a **fast re-synchronization point** if design context is lost
- To serve as a durable reference for future development, refactoring, or onboarding

This document is **normative** for the GENISYS implementation.
If code structure and this document diverge, the divergence should be treated as a
design issue and resolved deliberately.

### Out of Scope

This document intentionally does **not** cover:

- The GENISYS wire specification itself (see GENISYS.pdf)
- Netty, Spring Boot, or other framework integration details
- XML or other configuration file formats
- Logging framework specifics (SLF4J, Logback, etc.)

Those concerns are layered **below or above** the protocol implementation and are
deliberately decoupled.

---

## 2. High-Level Architectural Principles

The GENISYS implementation follows these core principles:

1. **Semantic meaning is separated from protocol mechanics**
   - “What a message means” is distinct from “how it is encoded or transmitted.”

2. **Reducers are pure and deterministic**
   - Protocol state transitions are driven solely by explicit inputs (events).
   - Reducers do not perform I/O, logging, timing, or mutation.

3. **Illegal protocol states should be unrepresentable**
   - Where practical, the type system is used to prevent invalid flows.

4. **Transport, frameworks, and I/O are strictly below the protocol layer**
   - Netty, serial drivers, Spring Boot, etc. must not leak upward.

5. **Observability and configuration are treated as data**
   - They are not side effects embedded in protocol logic.

6. **Testability is a first-class concern**
   - Protocol behavior must be testable without transport, threads, or clocks.

---

## 3. Package Overview

The GENISYS implementation lives under:

```
com.questrail.wayside.protocol.genisys
```

The package structure is intentionally layered:

```
com.questrail.wayside.protocol.genisys
├── model
│
├── internal
|   ├── frame        // Framed, CRC-validated units (wire-adjacent)
|   ├── decode       // Frame → semantic message
|   ├── encode       // Semantic message / intent → frame
|   ├── state        // Reducers and protocol state
|   ├── events       // Inputs to reducers
|   ├── intents      // Outputs from reducers
|   ├── exec         // Side effects (I/O, logging, transport)
```

Each package has a **specific responsibility** and **explicit dependency rules**.

---

## 4. The Semantic Model (`.model`)

```
com.questrail.wayside.protocol.genisys.model
```

### Purpose

This package defines the **semantic meaning** of the GENISYS protocol.

It answers questions such as:

- What kinds of messages exist in GENISYS?
- Who sent the message (master or slave)?
- What obligations or expectations does this message create?

### Contents

Typical classes include:

- `GenisysMessage`
- `GenisysMasterRequest`
- `GenisysSlaveResponse`
- Concrete message types (Poll, Recall, IndicationData, etc.)
- `GenisysStationAddress`

### Key Characteristics

- **No wire-level concerns**
  - No header bytes
  - No CRC handling
  - No escaping logic
- **No transport knowledge**
- **No framework dependencies**
- **Heavily documented**
- **Stable over time**

This package represents the **contract** between decoding, state machines, and tests.

### Dependency Rules

- `.model` must not depend on `.internal`
- Reducers and tests depend on `.model`
- Decoders translate *into* `.model`

---

## 5. The Internal Implementation (`.internal`)

```
com.questrail.wayside.protocol.genisys.internal
```

The `internal` package contains **protocol-specific implementation details**.
It is intentionally *not* a stable public API.

Code in this package is free to evolve as long as it preserves the semantic contract.

---

### 5.1 Frame Layer (`.internal.frame`)

```
.internal.frame
```

**Responsibility:**
- Represent decoded GENISYS frames at a byte-oriented level
- Carry header values, raw payloads, and CRC presence flags

**Notes:**
- Frames are *not* semantic messages
- Reducers must never reason directly about frames

---

### 5.2 Decode Layer (`.internal.decode`)

```
.internal.decode
```

**Responsibility:**
- Translate `GenisysFrame` instances into semantic `GenisysMessage` instances
- Enforce GENISYS message taxonomy and directionality
- Reject or flag invalid frame combinations

This package is the **critical boundary** between:
- Wire representation
- Protocol semantics

- Frames are decoded before event creation 
- Reducers never receive frames
- Decode failures are handled at ingress

### Decode Boundary Placement

GENISYS frames are decoded into semantic messages *before* protocol events
are created.

As a result:

- Reducers and state machines operate exclusively on `GenisysMessage`
- Wire-level artifacts (`GenisysFrame`, headers, CRC presence) never enter reducers
- Decode failures are handled at the ingress boundary, not inside protocol logic

This design ensures that protocol state transitions are driven by semantic facts
rather than wire representations, improving correctness, testability, and
observability.

---

### 5.3 Event Layer (`.internal.events`)

```
.internal.events
```

**Responsibility:**
- Represent discrete inputs into the protocol state machine
- Examples:
  - Semantic message received
  - Timeout occurred
  - Configuration update
  - Transport-level diagnostics (non-semantic)

Events are **data**, not behavior.

---

### 5.4 State / Reducer Layer (`.internal.state`)

```
.internal.state
```

**Responsibility:**
- Maintain protocol state
- Implement pure reducer logic
- Emit intents and observations

Key characteristics:
- Deterministic
- Side-effect free
- Testable in isolation

---

### 5.5 Intent Layer (`.internal.intents`)

```
.internal.intents
```

**Responsibility:**
- Describe *what should happen next*
- Examples:
  - Send poll
  - Send acknowledge-and-poll
  - Schedule timeout

Intents are **descriptive**, not imperative.

---

### 5.6 Execution Layer (`.internal.exec`)

```
.internal.exec
```

**Responsibility:**
- Perform side effects described by intents
- Message encoding
- Interaction with transport
- Logging and observability sinks

This is where impurity is allowed.

---

## 6. Dependency Rules (Normative)

The following dependency rules are **authoritative**:

- `.model`
  - Depends on nothing in `.internal`
- `.internal.decode`
  - Depends on `.frame` and `.model`
- `.internal.state`
  - Depends on `.model` and `.events`
- `.internal.intents`
  - Depends on `.model`
- `.internal.exec`
  - May depend on transport, logging, configuration
- Transport / framework code
  - Must not depend on `.internal.state`

Violations of these rules are architectural defects.

---

## 7. Testing Implications

- Reducer tests operate exclusively on **semantic messages**
- Tests must not fabricate raw header bytes to simulate behavior
- Unit tests are treated as **executable protocol documentation**

Tests should read as **GENISYS narratives**, not transport simulations.

---

## 8. Evolution and Extension Guidance

- New protocols (e.g., ATCS) follow the same pattern:
  ```
  com.questrail.wayside.protocol.<protocol>
  ```
- Netty, Spring Boot, and configuration adapters live **outside** the protocol packages
- Dynamic reconfiguration is modeled as events and state transitions
- Any change that alters package responsibilities should update this document

---

## 9. Closing Note

This package structure exists to preserve **clarity, correctness, and longevity**.

GENISYS is a legacy, safety-relevant protocol. The architecture must therefore
prioritize:

- Explicit intent
- Clear boundaries
- Testable behavior
- Resistance to accidental complexity

This document exists to help ensure those goals remain intact over time.
