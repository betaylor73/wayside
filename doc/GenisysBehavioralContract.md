# GENISYS Behavioral Contract

> **Status:** Draft (architectural baseline)
>
> **Purpose:** This document defines the *behavioral expectations* for a GENISYS
> master implementation, grounded in the official GENISYS specifications and
> validated against long‑running production behavior.
>
> This is an *implementer’s contract*, not merely a restatement of the wire format.

---

## 1. Scope and non‑goals

### 1.1 In scope

This document defines:

* Wire framing, escaping, and CRC presence rules
* Valid GENISYS message types and response expectations
* Master startup, polling, retry, and failure behavior
* Control delivery behavior (default and optional checkback/execute mode)
* Indication delivery semantics (incremental updates)
* Semantics of the `$E0` configuration/status byte

### 1.2 Explicit non‑goals

This document does **not** define:

* The semantic meaning of individual control or indication bits
* Any causal relationship between controls and indications
* Supervisory logic, alarming, or UI behavior
* Transport implementation details (serial driver specifics, buffering, etc.)

Those concerns are intentionally layered above or below the GENISYS protocol engine.

---

## 2. Wire format fundamentals

### 2.1 Framing

GENISYS is a byte‑oriented serial protocol. Each message consists of:

* A header/control byte (`$FA`–`$FF`)
* A station address byte (`$01`–`$FF`)
* Zero or more data bytes
* Optional CRC‑16 (two bytes, LSB first)
* A terminator byte `$F6`

### 2.2 Escaping

To preserve uniqueness of framing and control bytes:

* Any data byte in the range `$F0`–`$FF` is escaped
* Escaping is performed by transmitting:

```
$F0, (byte − $F0)
```

* On receive, whenever `$F0` is encountered, `$F0` is added to the following byte
  to reconstruct the original data value

Escaping applies **only to data bytes**, not to header/control characters.

### 2.3 CRC‑16 rules

* Polynomial: `X^16 + X^15 + X^2 + 1`
* CRC is computed over the *unescaped* message content
* CRC includes all bytes except the terminator `$F6`

CRC is **present in all messages except**:

* Slave → master acknowledge (`$F1`)
* Master → slave *non‑secure poll* (`$FB` without CRC)

---

## 3. Addressing and data model

### 3.1 Station addressing

* Valid station addresses: `$01`–`$FF`
* Each GENISYS slave responds only when addressed explicitly

### 3.2 Byte‑addressed data model

All control and indication data is conveyed as repeated 2‑byte pairs:

```
[ byteAddress, byteValue ]
```

GENISYS 2000 typically accepts byte addresses `$00`–`$1F` (0–31).

Bit numbering rules:

* Bits are packed sequentially across bytes
* Bits 1–8 are in byte 0, bits 9–16 in byte 1, etc.
* The lowest‑numbered bit in a byte is the LSB

This naturally maps to dense bit‑set representations.

---

## 4. Message taxonomy

### 4.1 Master → Slave messages

| Header | Meaning                                   |
| ------ | ----------------------------------------- |
| `$FA`  | Acknowledge and Poll                      |
| `$FB`  | Poll (secure or non‑secure)               |
| `$FC`  | Control Data                              |
| `$FD`  | Recall (indication recall / startup sync) |
| `$FE`  | Execute Controls                          |

### 4.2 Slave → Master messages

| Header | Meaning                |
| ------ | ---------------------- |
| `$F1`  | Acknowledge (no CRC)   |
| `$F2`  | Indication Data        |
| `$F3`  | Control Data Checkback |

---

## 5. Master operational behavior

### 5.1 Startup and recovery (recall‑until‑active)

On startup or recovery, the master:

1. Sends `$FD` Recall to each configured slave
2. Repeats recall until a valid response is received
3. Marks the slave *active* upon successful indication reception
4. Sends the current control image to the slave
5. Transitions the slave into the normal polling cycle

This establishes an initial synchronized indication database.

### 5.2 Polling cycle and acknowledgments

A fundamental GENISYS rule:

> **All indication data received from a slave must be acknowledged.**

Operationally:

* If the previous exchange produced indication data (`$F2`), the next poll to
  that slave must use `$FA` (acknowledge + poll)
* Otherwise, `$FB` (poll) may be used

If indication data is not acknowledged, the slave will repeat it when next addressed.

### 5.3 Timeouts, retries, and failed slaves

* If a slave fails to respond or responds with invalid data, it is retried
* After three consecutive failures, the slave is marked *failed*

Failed‑slave behavior:

* Failed slaves are addressed with recall messages
* New control changes are sent to failed slaves **once** (not retried)
* Failed slaves resume normal operation upon successful response

---

## 6. Control delivery behavior

### 6.1 Default operating mode (baseline)

Unless configured otherwise:

* **Non‑secure poll** is used (`$FB` without CRC)
* **Checkback/execute is disabled**

Controls are delivered using a single `$FC` Control Data message.

### 6.2 Control Data (`$FC`)

Purpose:

* Deliver new or changed control byte values to the slave

Valid slave responses:

* `$F1` Acknowledge
* `$F2` Indication Data

### 6.3 Optional checkback / execute mode

If checkback is enabled:

1. Master sends `$FC` Control Data
2. Slave responds with `$F3` Checkback (echo of received data)
3. Master sends `$FE` Execute
4. Slave applies the controls

If the sequence fails, the control data is discarded and must be resent.

---

## 7. Indication delivery semantics

### 7.1 Indication Data (`$F2`)

* `$F2` contains *new or changed* indication data
* It may be returned in response to:

  * Polls
  * Recall
  * Control messages
  * Execute messages

### 7.2 Semantic interpretation

Because indication data is incremental:

* The master must maintain a *cumulative last‑known indication database*
* Each `$F2` message updates only the indicated bytes
* Unspecified bits retain their prior values

This behavior maps directly to cumulative merge semantics.

---

## 8. `$E0` configuration and status byte

### 8.1 Reserved address range

* Byte addresses `$E0`–`$FF` are reserved for configuration
* `$E0` is currently defined

### 8.2 `$E0` bit definitions

| Bit | Meaning                          |
| --- | -------------------------------- |
| 0   | Database complete                |
| 1   | Use checkback control delivery   |
| 2   | Respond to secure poll only      |
| 3   | Enable common control processing |
| 4–7 | Reserved                         |

### 8.3 Contractual usage

* Masters may configure slaves by sending `$E0` in `$FC` messages
* Slaves report status via `$E0` in `$F2` indication data

These bits influence protocol behavior but do not change the semantic control/indication model.

---

## 9. Default profile for this implementation

Unless explicitly configured otherwise:

* Non‑secure poll is used
* Checkback/execute is disabled
* Recall‑until‑active is enforced on startup and recovery
* Indications are treated as incremental updates

---

## 10. Architectural mapping (summary)

* `WaysideController.setControls()` expresses *intent*; GENISYS determines how
  that intent is serialized on the wire
* Indication data is merged cumulatively into last‑known state
* Controller health (`CONNECTED`, `DEGRADED`, `DISCONNECTED`) is derived from
  polling and retry behavior

This contract deliberately aligns with an actor‑style, message‑driven implementation
model and preserves a clean semantic boundary above the protocol layer.

---

**End of document**

