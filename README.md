# Wayside Controls & Indications Framework

## One-line description

A protocol-agnostic framework for managing railway wayside controls and indications with dense internal representation, partial-update semantics, and clean separation between semantics and transport.

---

## Overview

This repository provides a **foundational architecture for railway wayside communication systems**. It defines a clean semantic model for **controls** (intent sent to the field) and **indications** (state reported from the field), while deliberately isolating protocol, transport, and encoding concerns.

The design is informed by real-world rail signaling systems and is intended to support long-lived, safety-critical deployments using protocols such as **GENISYS**, **ATCS**, and future rail communications standards.

At its core, the framework focuses on *meaning*, not messages.

---

## Key Design Principles

* **Semantic first**: Controls and indications are modeled as intent and observation, not packets or bits.
* **Partial updates are first-class**: Incremental and masked updates are explicitly supported.
* **Dense internal representation**: Boolean signals are stored efficiently (e.g., BitSet / bitvec) without leaking bit-level detail.
* **Protocol isolation**: All protocol-specific layout and encoding lives behind explicit mapping layers.
* **No assumed control–indication correspondence**: Any causal relationship between controls and indications is the responsibility of the remote wayside logic, not this framework.
* **Long-lived by design**: The architecture favors clarity, invariants, and extensibility over short-term convenience.

---

## Architectural Layers

```
Application / Supervision
        ↓
WaysideController (semantic API)
        ↓
SignalMapping (protocol layout)
        ↓
WaysideLink (transport)
```

* **WaysideController** manages desired controls, reported indications, and link-level health.
* **SignalMapping** translates between semantic identifiers and protocol-specific wire representations.
* **WaysideLink** handles byte transport (serial, UDP, HDLC, etc.) without semantic knowledge.

---

## Controls and Indications

* Each control or indication is identified by a **logical number** and an optional **human-readable label**.
* Signals support three explicit states:

  * `TRUE`
  * `FALSE`
  * `DONT_CARE` (not specified; must not cause change)
* Controls and indications are treated as **logically independent sets**.

---

## Language Support

The architecture is intentionally language-agnostic and is designed to support:

* **Java** implementations (encapsulation and clarity)
* **Rust** implementations (typestate and compile-time guarantees)

Both implementations express the same semantics and can evolve together.

---

## Intended Audience

This project is aimed at engineers working on:

* Railway signaling and wayside systems
* Safety-critical or long-lived control systems
* Protocol modernization or abstraction layers
* Simulation, test harnesses, and offline analysis tools

---

## Status

This repository focuses on **core architectural primitives**. Concrete protocol implementations (e.g., GENISYS, ATCS) are expected to live alongside the core framework and evolve independently.


