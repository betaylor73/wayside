# GENISYS Layering and Controller Responsibilities

## Purpose

This architectural note explicitly documents the **layering model** of the WaysideController project, with particular focus on the relationship between:

- `com.questrail.wayside.api.WaysideController`
- `com.questrail.wayside.protocol.genisys.GenisysWaysideController`
- Phase 4 runtime and transport integration classes (e.g. `GenisysUdpRuntime`)

The goal is to prevent boundary erosion as transport, observability, and system-facing features are added in later phases.

---

## 1. High-Level Architectural Stack

At a conceptual level, the system is intentionally layered as follows:

```
┌──────────────────────────────────────────┐
│            System / Application          │
│                                          │
│   UI, Supervisory Systems, Diagnostics   │
│                                          │
└──────────────────────▲───────────────────┘
                       │
                       │ uses
                       │
┌──────────────────────┴───────────────────┐
│        WaysideController (API)            │
│  com.questrail.wayside.api                │
│                                          │
│  • protocol-agnostic façade               │
│  • exposes indications                    │
│  • accepts control intents                │
│  • reports system status                  │
└──────────────────────▲───────────────────┘
                       │
                       │ delegates to
                       │
┌──────────────────────┴───────────────────┐
│     GenisysWaysideController              │
│  com.questrail.wayside.protocol.genisys   │
│                                          │
│  • GENISYS protocol semantics             │
│  • reducer + executor                     │
│  • protocol state machine                 │
└──────────────────────▲───────────────────┘
                       │
                       │ driven by
                       │
┌──────────────────────┴───────────────────┐
│        Phase 4 Runtime Layer              │
│        (e.g. GenisysUdpRuntime)           │
│                                          │
│  • transport lifecycle                    │
│  • decode-before-event enforcement        │
│  • event submission                       │
└──────────────────────▲───────────────────┘
                       │
                       │ uses
                       │
┌──────────────────────┴───────────────────┐
│        Transport / Codec Layer            │
│                                          │
│  • UDP / Netty / test adapters            │
│  • bytes ↔ frames                         │
└──────────────────────────────────────────┘
```

---

## 2. `WaysideController`: System-Facing Façade

`WaysideController` is the **top-level abstraction** representing the *wayside system as a whole*.

### Responsibilities

- Provide a stable, protocol-independent API to:
  - user interfaces
  - supervisory / CTC systems
  - diagnostics and observability tooling
- Express:
  - indications (signal aspects, occupancy, alarms)
  - control intents (requested signal changes, commands)
  - high-level system health

### Non-Responsibilities

`WaysideController` does **not**:

- know which protocol is in use (GENISYS, ATCS, future protocols)
- manage transport or timing
- perform protocol-level retries or sequencing

This abstraction exists so that **protocol engines can be swapped without rewriting system-facing code**.

---

## 3. `GenisysWaysideController`: Protocol Semantic Engine

`GenisysWaysideController` is a **protocol-specific semantic engine**, not a system façade.

### Responsibilities

- Own all GENISYS protocol state
- Host:
  - the GENISYS reducer
  - the GENISYS intent executor
- Enforce:
  - protocol law (`genisys.md`)
  - documented implementation policy
- Consume only **semantic protocol events**

### Non-Responsibilities

`GenisysWaysideController` does **not**:

- expose a user-facing API
- interact with UI or external systems
- handle bytes, frames, sockets, or timers

It is intentionally blind to transport and system context.

---

## 4. Phase 4 Runtime Layer: Composition and Ownership

Classes such as `GenisysUdpRuntime` live in a **runtime/composition layer**.

### Responsibilities

- Own and wire together:
  - a concrete transport adapter
  - codec components
  - the `GenisysWaysideController`
- Enforce the **decode-before-event** invariant
- Translate transport lifecycle into semantic transport events

### Explicit Non-Responsibilities

The runtime layer must not:

- interpret protocol meaning
- modify reducer or executor behavior
- invent retries, polling, or timing behavior

It is strictly a *bridge*, not a decision-maker.

---

## 5. Transport and Codec Layer

The lowest layers of the system are responsible for **mechanics only**.

### Transport

- UDP, Netty, or other I/O frameworks
- Deliver and send raw datagrams
- Report transport up/down transitions

### Codec

- Byte-level framing and deframing
- No protocol semantics
- No event emission

These layers must remain replaceable without affecting higher layers.

---

## 6. Why This Separation Matters

This layering ensures that:

- Protocol correctness can be proven without real I/O
- Transport changes do not force protocol rewrites
- System-facing APIs remain stable over time
- Each phase of the roadmap closes a distinct class of risk

Most importantly:

> **Protocol meaning is never allowed to drift downward into transport, nor upward into the system façade.**

---

## 7. Authorities: External Actors Interfacing with a WaysideController

### Definition

An **Authority** is any external actor that is permitted to:

- submit **control intents** to a `WaysideController`, and/or
- consume **indications, state, and status** exposed by a `WaysideController`.

Authorities are **outside** the wayside system proper. They do not participate in protocol semantics, transport mechanics, or reducer logic. Instead, they represent sources of intent and sinks of information.

### Examples of Authorities

Authorities may be human-operated, machine-operated, or hybrid. Typical examples include:

- Dispatcher workstations or control offices
- Local control panels at or near the wayside
- Supervisory / CTC systems
- Protocol adapters (e.g., an ATCS server acting on behalf of one or more ATCS clients)
- Simulation harnesses or test drivers

### Terminology Rationale

The term **Authority** is intentionally chosen because it:

- is protocol-agnostic
- applies equally to humans and machines
- reflects standing and permission rather than implementation detail
- avoids transport-centric terms such as *client* or *endpoint*
- avoids human-only terms such as *operator* or *dispatcher*

More specific terms (e.g., *Dispatcher*, *Local Panel*, *ATCS Server*) may be used as roles or concrete implementations, but architecturally they are all Authorities.

---

## 8. One-Sentence Summary

> `** defines what the wayside *****is*****; **`\*\* defines how the GENISYS protocol ***behaves***; the runtime layer defines how that behavior is ***hosted*** in the real world.\*\*

This separation is intentional, binding, and foundational to the project.

